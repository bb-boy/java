package com.example.kafka.service;

import com.example.kafka.consumer.DataConsumer;
import com.example.kafka.entity.OperationLogEntity;
import com.example.kafka.entity.ProtectionEventEntity;
import com.example.kafka.entity.ShotMetadataEntity;
import com.example.kafka.entity.UserEntity;
import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.entity.WaveformMetadataEntity;
import com.example.kafka.model.SyntheticGenerationRequest;
import com.example.kafka.model.SyntheticGenerationResult;
import com.example.kafka.model.OperationLog;
import com.example.kafka.model.WaveData;
import com.example.kafka.producer.DataProducer;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ProtectionEventRepository;
import com.example.kafka.repository.UserRepository;
import com.example.kafka.repository.WaveDataRepository;
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
public class SyntheticDataGeneratorService {

    private static final int DEFAULT_OPERATION_COUNT = 5;
    private static final int DEFAULT_PROTECTION_LIMIT = 3;
    private static final int MIN_COUNT = 1;
    private static final double DEFAULT_PROTECTION_RATIO = 0.9d;
    private static final double MIN_RATIO = 0.1d;
    private static final double MAX_RATIO = 0.999d;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private static final String DEFAULT_USER = "generator";
    private static final String DEFAULT_PASSWORD = "generated";
    private static final String DEFAULT_ROLE = "system";

    private static final String SOURCE_GENERATOR = "GENERATOR";
    private static final String OPERATION_TYPE = "COMMAND";
    private static final String STEP_TYPE = "AUTO";
    private static final String RESULT_OK = "OK";

    private static final String INTERLOCK_NAME = "AMPLITUDE_HIGH";
    private static final String THRESHOLD_OP = ">=";
    private static final String ACTION_SHUTDOWN = "HVPS_SHUTDOWN";
    private static final String ACTION_INHIBIT = "INHIBIT_RF";

    private static final String[] COMMANDS = {
        "ARM",
        "SET_POWER",
        "SET_DURATION",
        "START_PULSE",
        "STOP_PULSE"
    };

    private final WaveDataRepository waveDataRepository;
    private final OperationLogRepository operationLogRepository;
    private final ProtectionEventRepository protectionEventRepository;
    private final UserRepository userRepository;
    private final WaveformMetadataRepository waveformMetadataRepository;
    private final DataProducer dataProducer;
    private final ObjectMapper objectMapper;

    @Value("${app.protection.window.before-ms:100}")
    private long windowBeforeMs;

    @Value("${app.protection.window.after-ms:200}")
    private long windowAfterMs;

    @Autowired
    public SyntheticDataGeneratorService(
        WaveDataRepository waveDataRepository,
        OperationLogRepository operationLogRepository,
        ProtectionEventRepository protectionEventRepository,
        UserRepository userRepository,
        WaveformMetadataRepository waveformMetadataRepository,
        DataProducer dataProducer,
        ObjectMapper objectMapper
    ) {
        this.waveDataRepository = waveDataRepository;
        this.operationLogRepository = operationLogRepository;
        this.protectionEventRepository = protectionEventRepository;
        this.userRepository = userRepository;
        this.waveformMetadataRepository = waveformMetadataRepository;
        this.dataProducer = dataProducer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SyntheticGenerationResult generateProtectionOperation(SyntheticGenerationRequest request) {
        GenerationContext context = prepareContext(request);
        if (context.replaceExisting) {
            operationLogRepository.deleteByShotNo(context.shotNo);
            protectionEventRepository.deleteByShotNo(context.shotNo);
        }
        List<OperationLogEntity> logs = buildOperationLogs(context);
        if (!logs.isEmpty()) {
            for (OperationLogEntity log : logs) {
                dataProducer.sendOperationLog(toOperationLog(log)).join();
            }
        }
        List<ProtectionEventEntity> events = buildProtectionEvents(context);
        if (!events.isEmpty()) {
            protectionEventRepository.saveAll(events);
        }
        return new SyntheticGenerationResult(
            context.shotNo,
            context.channelName,
            context.dataType,
            logs.size(),
            events.size(),
            context.protectionRatio,
            context.threshold,
            context.startTime,
            context.endTime
        );
    }

    private GenerationContext prepareContext(SyntheticGenerationRequest request) {
        if (request == null || request.shotNo() == null) {
            throw new IllegalArgumentException("shotNo required");
        }
        WaveDataEntity entity = resolveWaveData(request);
        List<Double> values = resolveValues(entity);
        Double sampleRate = resolveSampleRate(entity);
        LocalDateTime startTime = resolveStartTime(entity);
        LocalDateTime endTime = resolveEndTime(entity, startTime, sampleRate, values.size());
        int operationCount = resolveOperationCount(request.operationCount(), values.size());
        int protectionLimit = resolveProtectionLimit(request.protectionLimit(), values.size());
        double protectionRatio = resolveProtectionRatio(request.protectionRatio());
        double peakAbs = computePeakAbs(values);
        double threshold = peakAbs * protectionRatio;
        Long userId = resolveUserId();
        String deviceId = resolveDeviceId(entity);
        Long waveformId = resolveWaveformId(request.shotNo(), deviceId, entity.getChannelName());
        boolean replaceExisting = Boolean.TRUE.equals(request.replaceExisting());
        return new GenerationContext(
            request.shotNo(),
            entity.getChannelName(),
            entity.getDataType(),
            entity.getFileSource(),
            entity.getSourceType(),
            values,
            sampleRate,
            startTime,
            endTime,
            deviceId,
            userId,
            operationCount,
            protectionLimit,
            protectionRatio,
            threshold,
            peakAbs,
            waveformId,
            replaceExisting
        );
    }

    private WaveDataEntity resolveWaveData(SyntheticGenerationRequest request) {
        if (request.channelName() != null && request.dataType() != null) {
            return waveDataRepository
                .findByShotNoAndChannelNameAndDataType(
                    request.shotNo(),
                    request.channelName(),
                    request.dataType()
                )
                .orElseThrow(() -> new IllegalStateException("waveform not found"));
        }
        List<WaveDataEntity> candidates = loadWaveDataCandidates(request);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("waveform not found");
        }
        candidates.sort(Comparator
            .comparing(WaveDataEntity::getDataType, Comparator.nullsLast(String::compareTo))
            .thenComparing(WaveDataEntity::getChannelName, Comparator.nullsLast(String::compareTo))
        );
        return candidates.get(0);
    }

    private List<WaveDataEntity> loadWaveDataCandidates(SyntheticGenerationRequest request) {
        if (request.channelName() != null) {
            List<WaveDataEntity> byShot = waveDataRepository.findByShotNo(request.shotNo());
            return filterByChannel(byShot, request.channelName());
        }
        if (request.dataType() != null) {
            return waveDataRepository.findByShotNoAndDataType(request.shotNo(), request.dataType());
        }
        return waveDataRepository.findByShotNo(request.shotNo());
    }

    private List<WaveDataEntity> filterByChannel(List<WaveDataEntity> data, String channelName) {
        List<WaveDataEntity> result = new ArrayList<>();
        for (WaveDataEntity entity : data) {
            if (channelName.equals(entity.getChannelName())) {
                result.add(entity);
            }
        }
        return result;
    }

    private List<Double> resolveValues(WaveDataEntity entity) {
        List<Double> values = DataConsumer.decompressWaveData(entity.getData());
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException("waveform data empty");
        }
        return values;
    }

    private Double resolveSampleRate(WaveDataEntity entity) {
        if (entity.getSampleRate() == null || entity.getSampleRate() <= 0) {
            throw new IllegalStateException("sampleRate missing");
        }
        return entity.getSampleRate();
    }

    private LocalDateTime resolveStartTime(WaveDataEntity entity) {
        if (entity.getStartTime() == null) {
            throw new IllegalStateException("startTime missing");
        }
        return entity.getStartTime();
    }

    private LocalDateTime resolveEndTime(
        WaveDataEntity entity,
        LocalDateTime startTime,
        double sampleRate,
        int size
    ) {
        if (entity.getEndTime() != null) {
            return entity.getEndTime();
        }
        long nanos = Math.round(((size - 1) / sampleRate) * NANOS_PER_SECOND);
        return startTime.plusNanos(nanos);
    }

    private int resolveOperationCount(Integer requested, int max) {
        int count = requested != null ? requested : DEFAULT_OPERATION_COUNT;
        if (count < MIN_COUNT) {
            count = DEFAULT_OPERATION_COUNT;
        }
        return Math.min(count, Math.max(max, MIN_COUNT));
    }

    private int resolveProtectionLimit(Integer requested, int max) {
        int count = requested != null ? requested : DEFAULT_PROTECTION_LIMIT;
        if (count < MIN_COUNT) {
            count = DEFAULT_PROTECTION_LIMIT;
        }
        return Math.min(count, Math.max(max, MIN_COUNT));
    }

    private double resolveProtectionRatio(Double requested) {
        double ratio = requested != null ? requested : DEFAULT_PROTECTION_RATIO;
        if (ratio < MIN_RATIO || ratio >= 1.0d) {
            ratio = DEFAULT_PROTECTION_RATIO;
        }
        return Math.min(Math.max(ratio, MIN_RATIO), MAX_RATIO);
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

    private Long resolveUserId() {
        return userRepository.findByUsername(DEFAULT_USER)
            .orElseGet(this::createDefaultUser)
            .getUserId();
    }

    private UserEntity createDefaultUser() {
        UserEntity user = new UserEntity();
        user.setUsername(DEFAULT_USER);
        user.setDisplayName("Generator");
        user.setPasswordHash(DEFAULT_PASSWORD);
        user.setRole(DEFAULT_ROLE);
        user.setEnabled(Boolean.TRUE);
        return userRepository.save(user);
    }

    private String resolveDeviceId(WaveDataEntity entity) {
        if (entity.getDataType() != null && !entity.getDataType().isBlank()) {
            return entity.getDataType();
        }
        if (entity.getChannelName() != null && !entity.getChannelName().isBlank()) {
            return entity.getChannelName();
        }
        return "UNKNOWN";
    }

    private Long resolveWaveformId(Integer shotNo, String deviceId, String channelName) {
        if (shotNo == null || deviceId == null || channelName == null) {
            return null;
        }
        return waveformMetadataRepository
            .findByShotNoAndDeviceIdAndChannelName(shotNo, deviceId, channelName)
            .map(WaveformMetadataEntity::getWaveformId)
            .orElse(null);
    }

    private List<OperationLogEntity> buildOperationLogs(GenerationContext context) {
        List<OperationLogEntity> logs = new ArrayList<>();
        for (int i = 0; i < context.operationCount; i++) {
            int index = resolveOperationIndex(i, context.operationCount, context.values.size());
            double value = context.values.get(index);
            OperationLogEntity log = new OperationLogEntity();
            log.setShotNo(context.shotNo);
            log.setTimestamp(timeAtIndex(context.startTime, context.sampleRate, index));
            log.setOperationType(OPERATION_TYPE);
            log.setChannelName(context.channelName);
            log.setUserId(context.userId);
            log.setDeviceId(context.deviceId);
            log.setCommand(COMMANDS[i % COMMANDS.length]);
            log.setParameters(buildParameters(context, log.getCommand(), value, index));
            log.setResultCode(RESULT_OK);
            log.setResultMessage("generated");
            log.setSource(SOURCE_GENERATOR);
            log.setCorrelationId("shot-" + context.shotNo + "-op-" + (i + 1));
            log.setStepType(STEP_TYPE);
            log.setOldValue(value * 0.95d);
            log.setNewValue(value);
            log.setDelta(value - log.getOldValue());
            log.setConfidence(resolveConfidence(value, context.peakAbs));
            log.setFileSource(context.fileSource);
            log.setSourceType(context.sourceType);
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

    private int resolveOperationIndex(int step, int total, int size) {
        if (size <= 1) {
            return 0;
        }
        double ratio = (step + 1) / (double) (total + 1);
        int index = (int) Math.round(ratio * (size - 1));
        return Math.max(0, Math.min(index, size - 1));
    }

    private double resolveConfidence(double value, double peakAbs) {
        if (peakAbs <= 0.0d) {
            return 0.0d;
        }
        return Math.min(1.0d, Math.abs(value) / peakAbs);
    }

    private String buildParameters(
        GenerationContext context,
        String command,
        double value,
        int index
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", command);
        payload.put("channel", context.channelName);
        payload.put("index", index);
        payload.put("value", value);
        payload.put("shotNo", context.shotNo);
        return writeJson(payload);
    }

    private List<ProtectionEventEntity> buildProtectionEvents(GenerationContext context) {
        List<Integer> indices = selectProtectionIndices(context.values, context.protectionLimit);
        List<ProtectionEventEntity> events = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i);
            double value = context.values.get(index);
            LocalDateTime triggerTime = timeAtIndex(context.startTime, context.sampleRate, index);
            ProtectionEventEntity event = new ProtectionEventEntity();
            event.setShotNo(context.shotNo);
            event.setTriggerTime(triggerTime);
            event.setDeviceId(context.deviceId);
            event.setSeverity(resolveSeverity(i));
            event.setProtectionLevel(resolveProtectionLevel(i));
            event.setInterlockName(INTERLOCK_NAME);
            event.setTriggerCondition("abs(value) " + THRESHOLD_OP + " " + context.threshold);
            event.setMeasuredValue(value);
            event.setThresholdValue(context.threshold);
            event.setThresholdOp(THRESHOLD_OP);
            event.setActionTaken(resolveAction(i));
            event.setActionLatencyUs(resolveLatencyUs(i));
            event.setRelatedWaveformId(context.waveformId);
            event.setWindowStart(triggerTime.minusNanos(windowBeforeMs * NANOS_PER_MILLI));
            event.setWindowEnd(triggerTime.plusNanos(windowAfterMs * NANOS_PER_MILLI));
            event.setRelatedChannels(buildRelatedChannels(context.channelName));
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
        List<Integer> indices = buildIndexList(size);
        indices.sort((a, b) -> compareByAbs(values, a, b));
        int minSpacing = resolveMinSpacing(size, limit);
        List<Integer> selected = new ArrayList<>();
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

    private List<Integer> buildIndexList(int size) {
        List<Integer> indices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            indices.add(i);
        }
        return indices;
    }

    private int compareByAbs(List<Double> values, int left, int right) {
        double leftAbs = Math.abs(values.get(left));
        double rightAbs = Math.abs(values.get(right));
        int cmp = Double.compare(rightAbs, leftAbs);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(left, right);
    }

    private int resolveMinSpacing(int size, int limit) {
        int denom = Math.max(1, limit * 4);
        return Math.max(1, size / denom);
    }

    private boolean isSpaced(int index, List<Integer> selected, int minSpacing) {
        for (Integer existing : selected) {
            if (Math.abs(existing - index) < minSpacing) {
                return false;
            }
        }
        return true;
    }

    private String resolveSeverity(int order) {
        return order == 0 ? "CRITICAL" : "TRIP";
    }

    private String resolveProtectionLevel(int order) {
        return order == 0 ? "FAST" : "LOW";
    }

    private String resolveAction(int order) {
        return order == 0 ? ACTION_SHUTDOWN : ACTION_INHIBIT;
    }

    private long resolveLatencyUs(int order) {
        return order == 0 ? 10L : 50L;
    }

    private String buildRelatedChannels(String channelName) {
        return writeJson(List.of(channelName));
    }

    private String buildRawPayload(GenerationContext context, double value, int index) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedBy", "SyntheticDataGeneratorService");
        payload.put("shotNo", context.shotNo);
        payload.put("channel", context.channelName);
        payload.put("index", index);
        payload.put("value", value);
        payload.put("threshold", context.threshold);
        payload.put("ratio", context.protectionRatio);
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

    private record GenerationContext(
        Integer shotNo,
        String channelName,
        String dataType,
        String fileSource,
        ShotMetadataEntity.DataSourceType sourceType,
        List<Double> values,
        double sampleRate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String deviceId,
        Long userId,
        int operationCount,
        int protectionLimit,
        double protectionRatio,
        double threshold,
        double peakAbs,
        Long waveformId,
        boolean replaceExisting
    ) {}
}
