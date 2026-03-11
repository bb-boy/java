package com.example.kafka.service;

import com.example.kafka.config.TopicNames;
import com.example.kafka.model.DerivedPayload;
import com.example.kafka.producer.KafkaMessagePublisher;
import com.example.kafka.util.EventConstants;
import com.example.kafka.util.KafkaPayloadParser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DerivedKafkaPublisher {
    private static final String SOURCE_ROLE_PRIMARY = "PRIMARY";
    private static final Set<String> PRIMARY_OPERATION_CHANNELS = Set.of(
        "NegVoltage",
        "PosVoltage",
        "FilaVoltage",
        "FilaCurrent",
        "InPower",
        "RefPower"
    );
    private static final Set<String> PRIMARY_PROTECTION_CHANNELS = Set.of(
        "NegVoltage",
        "NegCurrent",
        "PosVoltage",
        "PosCurrent",
        "InPower",
        "RefPower",
        "FilaCurrent",
        "TiPumpCurrent"
    );

    private final KafkaMessagePublisher publisher;

    public DerivedKafkaPublisher(KafkaMessagePublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(DerivedPayload payload) {
        List<Map<String, Object>> artifacts = payload.getArtifacts();
        publishArtifacts(artifacts);
        publishShotMeta(payload.getShotMeta(), resolvePrimaryArtifactId(artifacts));
        publishSignalCatalog(payload.getSignalCatalog());
        publishEvents(payload.getOperationEvents(), TopicNames.EVENT_OPERATION);
        publishEvents(payload.getProtectionEvents(), TopicNames.EVENT_PROTECTION);
        publishWaveformRequest(payload.getWaveformRequest());
        publishWaveformBatches(payload.getWaveformBatches());
    }

    private void publishArtifacts(List<Map<String, Object>> artifacts) {
        for (Map<String, Object> artifact : artifacts) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("artifact_id", artifact.get("artifact_id"));
            payload.put("shot_no", artifact.get("shot_no"));
            payload.put("data_type", artifact.get("data_type"));
            payload.put("file_path", artifact.get("file_path"));
            payload.put("file_name", artifact.get("file_name"));
            payload.put("file_size_bytes", artifact.get("file_size"));
            payload.put("file_mtime", artifact.get("file_mtime"));
            payload.put("sha256_hex", artifact.get("sha256_hex"));
            payload.put("artifact_status", artifact.get("artifact_status"));
            String key = KafkaPayloadParser.asString(payload, "sha256_hex");
            publisher.publish(TopicNames.TDMS_ARTIFACT, key, payload);
        }
    }

    private void publishShotMeta(Map<String, Object> meta, String artifactId) {
        if (meta.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("shot_no", meta.get("shot_no"));
        payload.put("shot_start_time", meta.get("shot_start_time"));
        payload.put("shot_end_time", meta.get("shot_end_time"));
        payload.put("expected_duration", meta.get("expected_duration_seconds"));
        payload.put("actual_duration", meta.get("actual_duration_seconds"));
        payload.put("status_code", meta.get("status_code"));
        payload.put("status_reason", meta.get("status_reason"));
        payload.put("artifact_id", artifactId);
        String key = KafkaPayloadParser.asString(payload, "shot_no");
        publisher.publish(TopicNames.SHOT_META, key, payload);
    }

    private void publishSignalCatalog(List<Map<String, Object>> signalCatalog) {
        for (Map<String, Object> entry : signalCatalog) {
            String channelName = KafkaPayloadParser.asString(entry, "channel_name");
            Map<String, Object> payload = new HashMap<>();
            payload.put("source_system", entry.getOrDefault("source_system", EventConstants.SOURCE_SYSTEM_TDMS));
            payload.put("source_type", entry.get("data_type"));
            payload.put("source_name", channelName);
            payload.put("data_type", entry.get("data_type"));
            payload.put("process_id", entry.get("process_id"));
            payload.put("display_name", channelName);
            payload.put("unit", entry.get("unit"));
            payload.put("value_type", entry.get("value_type"));
            payload.put("device_scope", entry.get("data_type"));
            payload.put("is_primary_waveform", isPrimaryWaveform(entry));
            payload.put("is_primary_operation_signal", isPrimaryOperation(channelName));
            payload.put("is_primary_protection_signal", isPrimaryProtection(channelName));
            String key = EventConstants.SOURCE_SYSTEM_TDMS + ":" + channelName;
            publisher.publish(TopicNames.SIGNAL_CATALOG, key, payload);
        }
    }

    private void publishEvents(List<Map<String, Object>> events, String topic) {
        for (Map<String, Object> event : events) {
            Map<String, Object> payload = new HashMap<>();
            String channelName = KafkaPayloadParser.asString(event, "channel_name");
            payload.put("source_system", event.get("source_system"));
            payload.put("authority_level", event.get("authority_level"));
            payload.put("correlation_key", event.get("dedup_key"));
            payload.put("source_entity_id", channelName);
            payload.put("shot_no", event.get("shot_no"));
            payload.put("artifact_id", event.get("artifact_id"));
            payload.put("event_family", event.get("event_family"));
            payload.put("event_code", event.get("event_code"));
            payload.put("event_name", buildEventName(event, channelName));
            payload.put("event_time", event.get("event_time"));
            payload.put("process_id", event.get("process_id"));
            payload.put("message_text", event.get("message_text"));
            payload.put("message_level", "INFO");
            payload.put("severity", event.get("severity"));
            payload.put("details", event.get("details"));
            payload.put("dedup_key", event.get("dedup_key"));
            publisher.publish(topic, String.valueOf(event.get("shot_no")), payload);
        }
    }

    private void publishWaveformRequest(Map<String, Object> request) {
        if (request.isEmpty()) {
            return;
        }
        String key = KafkaPayloadParser.asString(request, "sha256_hex");
        if (key == null) {
            throw new IllegalArgumentException("waveform request missing sha256_hex");
        }
        publisher.publish(TopicNames.WAVEFORM_INGEST_REQUEST, key, request);
    }

    private void publishWaveformBatches(List<Map<String, Object>> batches) {
        for (Map<String, Object> batch : batches) {
            String artifactId = KafkaPayloadParser.asString(batch, "artifact_id");
            String channelName = KafkaPayloadParser.asString(batch, "channel_name");
            if (artifactId == null || channelName == null) {
                throw new IllegalArgumentException("waveform batch missing artifact_id or channel_name");
            }
            String key = artifactId + ":" + channelName;
            publisher.publish(TopicNames.WAVEFORM_CHANNEL_BATCH, key, batch);
        }
    }

    private boolean isPrimaryWaveform(Map<String, Object> entry) {
        String sourceRole = KafkaPayloadParser.asString(entry, "source_role");
        return SOURCE_ROLE_PRIMARY.equalsIgnoreCase(sourceRole);
    }

    private boolean isPrimaryOperation(String channelName) {
        return channelName != null && PRIMARY_OPERATION_CHANNELS.contains(channelName);
    }

    private boolean isPrimaryProtection(String channelName) {
        return channelName != null && PRIMARY_PROTECTION_CHANNELS.contains(channelName);
    }

    private String resolvePrimaryArtifactId(List<Map<String, Object>> artifacts) {
        for (Map<String, Object> artifact : artifacts) {
            String sourceRole = KafkaPayloadParser.asString(artifact, "source_role");
            if (SOURCE_ROLE_PRIMARY.equalsIgnoreCase(sourceRole)) {
                return KafkaPayloadParser.asString(artifact, "artifact_id");
            }
        }
        return artifacts.isEmpty() ? null : KafkaPayloadParser.asString(artifacts.get(0), "artifact_id");
    }

    private String buildEventName(Map<String, Object> event, String channelName) {
        String eventCode = KafkaPayloadParser.asString(event, "event_code");
        if (channelName == null) {
            return eventCode;
        }
        return eventCode + ":" + channelName;
    }
}
