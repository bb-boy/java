package com.example.kafka.service;

import com.example.kafka.entity.OperationLogEntity;
import com.example.kafka.entity.ProtectionEventEntity;
import com.example.kafka.entity.ShotMetadataEntity;
import com.example.kafka.model.OperationLog;
import com.example.kafka.model.ShotMetadata;
import com.example.kafka.model.WaveData;
import com.example.kafka.producer.DataProducer;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ProtectionEventRepository;
import com.example.kafka.repository.WaveformMetadataRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TdmsEventGeneratorService {

    private static final String SOURCE_TDMS = "TDMS_DERIVED";
    private static final String OPERATION_TYPE = "TDMS_STEP";
    private static final String STEP_TYPE = "AUTO";
    private static final String RESULT_OK = "OK";
    private static final String COMMAND_SET = "SET_VALUE";
    private static final String INTERLOCK_NAME = "TDMS_AMPLITUDE_HIGH";
    private static final String THRESHOLD_OP = ">=";
    private static final String ACTION_SHUTDOWN = "HVPS_SHUTDOWN";
    private static final String ACTION_INHIBIT = "INHIBIT_RF";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final int DEFAULT_OPERATION_FALLBACK = 5;
    private static final int DEFAULT_PROTECTION_LIMIT = 2;
    private static final double DEFAULT_PROTECTION_RATIO = 0.9d;
    private static final double DEFAULT_SIGMA = 6.0d;
    private static final int DEFAULT_MERGE_GAP = 5;
    private static final double DEFAULT_MIN_DELTA = 0.0d;

    private final OperationLogRepository operationLogRepository;
    private final ProtectionEventRepository protectionEventRepository;
    private final WaveformMetadataRepository waveformMetadataRepository;
    private final DataProducer dataProducer;
    private final ObjectMapper objectMapper;

    @Value("${app.tdms.generator.operation.max-count:" + DEFAULT_OPERATION_FALLBACK + "}")
    private int maxOperationCount;

    @Value("${app.tdms.generator.operation.sigma:" + DEFAULT_SIGMA + "}")
    private double sigmaFactor;

    @Value("${app.tdms.generator.operation.merge-gap:" + DEFAULT_MERGE_GAP + "}")
    private int mergeGap;

    @Value("${app.tdms.generator.operation.min-delta-abs:" + DEFAULT_MIN_DELTA + "}")
    private double minDeltaAbs;

    @Value("${app.tdms.generator.protection.limit:" + DEFAULT_PROTECTION_LIMIT + "}")
    private int protectionLimit;

    @Value("${app.tdms.generator.protection.ratio:" + DEFAULT_PROTECTION_RATIO + "}")
    private double protectionRatio;

    @Value("${app.protection.window.before-ms:100}")
    private long windowBeforeMs;

    @Value("${app.protection.window.after-ms:200}")
    private long windowAfterMs;

    @Autowired
    public TdmsEventGeneratorService(
        OperationLogRepository operationLogRepository,
        ProtectionEventRepository protectionEventRepository,
        WaveformMetadataRepository waveformMetadataRepository,
        DataProducer dataProducer,
        ObjectMapper objectMapper
    ) {
        this.operationLogRepository = operationLogRepository;
        this.protectionEventRepository = protectionEventRepository;
        this.waveformMetadataRepository = waveformMetadataRepository;
        this.dataProducer = dataProducer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GenerationResult generateFromWaveData(
        ShotMetadata metadata,
        WaveData waveData,
        boolean replaceExisting
    ) {
        if (waveData == null || waveData.getData() == null || waveData.getData().isEmpty()) {
            return new GenerationResult(0, 0);
        }
        Integer shotNo = waveData.getShotNo();
        if (shotNo == null) {
            throw new IllegalArgumentException("shotNo required");
        }
        if (replaceExisting) {
            operationLogRepository.deleteByShotNo(shotNo);
            protectionEventRepository.deleteByShotNo(shotNo);
        }

        GenerationContext context = buildContext(metadata, waveData);
        List<OperationLogEntity> logs = buildOperationLogs(context);
        if (!logs.isEmpty()) {
            for (OperationLogEntity log : logs) {
                dataProducer.sendOperationLog(toOperationLog(log)).join();
            }
        }
        List<ProtectionEventEntity> events = buildProtectionEvents(context);
        if (!events.isEmpty()) {
            for (ProtectionEventEntity event : events) {
                dataProducer.sendProtectionEvent(event).join();
            }
        }
        return new GenerationResult(logs.size(), events.size());
    }

    private GenerationContext buildContext(ShotMetadata metadata, WaveData waveData) {
        List<Double> values = waveData.getData();
        double sampleRate = resolveSampleRate(metadata, waveData);
        LocalDateTime startTime = resolveStartTime(metadata, waveData);
        LocalDateTime endTime = resolveEndTime(startTime, sampleRate, values.size());
        String dataType = waveData.getFileSource() != null && waveData.getFileSource().contains("Water")
            ? "Water"
            : (waveData.getFileSource() != null && waveData.getFileSource().contains("Tube") ? "Tube" : null);
        String deviceId = dataType != null ? dataType : "TDMS";
        Long waveformId = waveformMetadataRepository
            .findByShotNoAndDeviceIdAndChannelName(waveData.getShotNo(), deviceId, waveData.getChannelName())
            .map(m -> m.getWaveformId())
            .orElse(null);
        double peakAbs = computePeakAbs(values);
        double threshold = peakAbs * protectionRatio;
        return new GenerationContext(
            waveData.getShotNo(),
            waveData.getChannelName(),
            waveData.getFileSource(),
            dataType,
            values,
            sampleRate,
            startTime,
            endTime,
            deviceId,
            waveformId,
            peakAbs,
            threshold
        );
    }

    private double resolveSampleRate(ShotMetadata metadata, WaveData waveData) {
        if (waveData.getSampleRate() != null && waveData.getSampleRate() > 0) {
            return waveData.getSampleRate();
        }
        if (metadata != null && metadata.getSampleRate() != null && metadata.getSampleRate() > 0) {
            return metadata.getSampleRate();
        }
        return 1000.0d;
    }

    private LocalDateTime resolveStartTime(ShotMetadata metadata, WaveData waveData) {
        if (waveData.getStartTime() != null) {
            return waveData.getStartTime();
        }
        if (metadata != null && metadata.getStartTime() != null) {
            return metadata.getStartTime();
        }
        return LocalDateTime.now();
    }

    private LocalDateTime resolveEndTime(LocalDateTime startTime, double sampleRate, int totalSamples) {
        if (totalSamples <= 0) {
            return startTime;
        }
        long nanos = Math.round(((double) (totalSamples - 1) / sampleRate) * NANOS_PER_SECOND);
        return startTime.plusNanos(nanos);
    }

    private List<OperationLogEntity> buildOperationLogs(GenerationContext context) {
        List<Integer> indices = detectStepIndices(context.values);
        if (indices.isEmpty()) {
            indices = fallbackIndices(context.values.size(), maxOperationCount);
        }
        if (indices.size() > maxOperationCount) {
            indices = indices.subList(0, maxOperationCount);
        }
        List<OperationLogEntity> logs = new ArrayList<>();
        int count = 0;
        for (Integer index : indices) {
            if (index == null || index < 0 || index >= context.values.size()) {
                continue;
            }
            double newValue = context.values.get(index);
            double oldValue = context.values.get(Math.max(0, index - 1));
            OperationLogEntity log = new OperationLogEntity();
            log.setShotNo(context.shotNo);
            log.setTimestamp(timeAtIndex(context.startTime, context.sampleRate, index));
            log.setOperationType(OPERATION_TYPE);
            log.setChannelName(context.channelName);
            log.setCommand(COMMAND_SET);
            log.setParameters(buildParameters(context, newValue, index));
            log.setResultCode(RESULT_OK);
            log.setResultMessage("derived");
            log.setSource(SOURCE_TDMS);
            log.setCorrelationId("shot-" + context.shotNo + "-tdms-op-" + (++count));
            log.setStepType(STEP_TYPE);
            log.setOldValue(oldValue);
            log.setNewValue(newValue);
            log.setDelta(newValue - oldValue);
            log.setConfidence(resolveConfidence(newValue - oldValue, context.peakAbs));
            log.setDeviceId(context.deviceId);
            log.setFileSource(context.fileSource);
            log.setSourceType(ShotMetadataEntity.DataSourceType.FILE);
            logs.add(log);
        }
        return logs;
    }

    private OperationLog toOperationLog(OperationLogEntity entity) {
        OperationLog log = new OperationLog();
        log.setShotNo(entity.getShotNo());
        log.setTimestamp(entity.getTimestamp());
        log.setOperationType(entity.getOperationType());
        log.setChannelName(entity.getChannelName());
        log.setUserId(entity.getUserId());
        log.setDeviceId(entity.getDeviceId());
        log.setCommand(entity.getCommand());
        log.setParameters(entity.getParameters());
        log.setResultCode(entity.getResultCode());
        log.setResultMessage(entity.getResultMessage());
        log.setSource(entity.getSource());
        log.setCorrelationId(entity.getCorrelationId());
        log.setStepType(entity.getStepType());
        log.setOldValue(entity.getOldValue());
        log.setNewValue(entity.getNewValue());
        log.setDelta(entity.getDelta());
        log.setConfidence(entity.getConfidence());
        log.setFileSource(entity.getFileSource());
        if (entity.getSourceType() != null) {
            log.setSourceType(WaveData.DataSourceType.valueOf(entity.getSourceType().name()));
        }
        return log;
    }

    private List<Integer> detectStepIndices(List<Double> values) {
        if (values.size() < 2) {
            return List.of();
        }
        List<Double> deltas = new ArrayList<>(values.size() - 1);
        for (int i = 1; i < values.size(); i++) {
            deltas.add(Math.abs(values.get(i) - values.get(i - 1)));
        }
        double median = median(deltas);
        double mad = medianAbsoluteDeviation(deltas, median);
        double threshold = Math.max(minDeltaAbs, median + sigmaFactor * mad);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < deltas.size(); i++) {
            if (deltas.get(i) >= threshold) {
                indices.add(i + 1);
            }
        }
        return mergeIndices(indices, mergeGap);
    }

    private List<Integer> mergeIndices(List<Integer> indices, int gap) {
        if (indices.isEmpty()) {
            return List.of();
        }
        List<Integer> sorted = new ArrayList<>(indices);
        sorted.sort(Comparator.naturalOrder());
        List<Integer> merged = new ArrayList<>();
        int start = sorted.get(0);
        int prev = start;
        for (int i = 1; i < sorted.size(); i++) {
            int current = sorted.get(i);
            if (current - prev <= gap) {
                prev = current;
                continue;
            }
            merged.add((start + prev) / 2);
            start = current;
            prev = current;
        }
        merged.add((start + prev) / 2);
        return merged;
    }

    private List<Integer> fallbackIndices(int size, int maxCount) {
        if (size <= 0 || maxCount <= 0) {
            return List.of();
        }
        int count = Math.min(maxCount, size);
        List<Integer> indices = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            int index = (int) Math.round((i / (double) (count + 1)) * (size - 1));
            indices.add(Math.max(0, Math.min(index, size - 1)));
        }
        return indices;
    }

    private double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        List<Double> copy = new ArrayList<>(values);
        copy.sort(Comparator.naturalOrder());
        int mid = copy.size() / 2;
        if (copy.size() % 2 == 1) {
            return copy.get(mid);
        }
        return (copy.get(mid - 1) + copy.get(mid)) / 2.0d;
    }

    private double medianAbsoluteDeviation(List<Double> values, double median) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        List<Double> deviations = new ArrayList<>(values.size());
        for (Double value : values) {
            deviations.add(Math.abs(value - median));
        }
        double mad = median(deviations);
        return mad * 1.4826d;
    }

    private double resolveConfidence(double delta, double peakAbs) {
        if (peakAbs <= 0.0d) {
            return 0.0d;
        }
        return Math.min(1.0d, Math.abs(delta) / peakAbs);
    }

    private String buildParameters(GenerationContext context, double value, int index) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", context.channelName);
        payload.put("index", index);
        payload.put("value", value);
        payload.put("shotNo", context.shotNo);
        return writeJson(payload);
    }

    private List<ProtectionEventEntity> buildProtectionEvents(GenerationContext context) {
        List<Integer> indices = selectProtectionIndices(context.values, protectionLimit);
        List<ProtectionEventEntity> events = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i);
            double value = context.values.get(index);
            LocalDateTime triggerTime = timeAtIndex(context.startTime, context.sampleRate, index);
            ProtectionEventEntity event = new ProtectionEventEntity();
            event.setShotNo(context.shotNo);
            event.setTriggerTime(triggerTime);
            event.setDeviceId(context.deviceId);
            event.setSeverity(i == 0 ? "CRITICAL" : "TRIP");
            event.setProtectionLevel(i == 0 ? "FAST" : "LOW");
            event.setInterlockName(INTERLOCK_NAME);
            event.setTriggerCondition("abs(value) " + THRESHOLD_OP + " " + context.threshold);
            event.setMeasuredValue(value);
            event.setThresholdValue(context.threshold);
            event.setThresholdOp(THRESHOLD_OP);
            event.setActionTaken(i == 0 ? ACTION_SHUTDOWN : ACTION_INHIBIT);
            event.setActionLatencyUs(i == 0 ? 10L : 50L);
            event.setRelatedWaveformId(context.waveformId);
            event.setWindowStart(triggerTime.minusNanos(windowBeforeMs * NANOS_PER_MILLI));
            event.setWindowEnd(triggerTime.plusNanos(windowAfterMs * NANOS_PER_MILLI));
            event.setRelatedChannels(writeJson(List.of(context.channelName)));
            event.setRawPayload(buildRawPayload(context, value, index));
            events.add(event);
        }
        return events;
    }

    private List<Integer> selectProtectionIndices(List<Double> values, int limit) {
        int size = values.size();
        if (limit <= 0 || size == 0) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> Double.compare(Math.abs(values.get(b)), Math.abs(values.get(a))));
        List<Integer> selected = new ArrayList<>();
        int minSpacing = Math.max(1, size / Math.max(1, limit * 4));
        for (Integer index : indices) {
            if (isSpaced(index, selected, minSpacing)) {
                selected.add(index);
                if (selected.size() >= limit) {
                    break;
                }
            }
        }
        selected.sort(Integer::compareTo);
        return selected;
    }

    private boolean isSpaced(int index, List<Integer> selected, int minSpacing) {
        for (Integer existing : selected) {
            if (Math.abs(existing - index) < minSpacing) {
                return false;
            }
        }
        return true;
    }

    private String buildRawPayload(GenerationContext context, double value, int index) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedBy", "TdmsEventGeneratorService");
        payload.put("shotNo", context.shotNo);
        payload.put("channel", context.channelName);
        payload.put("index", index);
        payload.put("value", value);
        payload.put("threshold", context.threshold);
        payload.put("ratio", protectionRatio);
        return writeJson(payload);
    }

    private String writeJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return String.valueOf(payload);
        }
    }

    private LocalDateTime timeAtIndex(LocalDateTime startTime, double sampleRate, int index) {
        long nanos = Math.round((index / sampleRate) * NANOS_PER_SECOND);
        return startTime.plusNanos(nanos);
    }

    private double computePeakAbs(List<Double> values) {
        double peak = 0.0d;
        for (Double value : values) {
            if (value == null) {
                continue;
            }
            peak = Math.max(peak, Math.abs(value));
        }
        return peak;
    }

    public record GenerationResult(int operationCount, int protectionCount) {}

    private record GenerationContext(
        Integer shotNo,
        String channelName,
        String fileSource,
        String dataType,
        List<Double> values,
        double sampleRate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String deviceId,
        Long waveformId,
        double peakAbs,
        double threshold
    ) {}
}
