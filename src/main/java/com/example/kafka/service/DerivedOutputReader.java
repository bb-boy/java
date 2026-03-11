package com.example.kafka.service;

import com.example.kafka.model.DerivedPayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DerivedOutputReader {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String ARTIFACT_FILE = "artifact.json";
    private static final String SHOT_META_FILE = "shot_meta.json";
    private static final String SIGNAL_CATALOG_FILE = "signal_catalog.jsonl";
    private static final String OPERATION_EVENTS_FILE = "operation_events.jsonl";
    private static final String PROTECTION_EVENTS_FILE = "protection_events.jsonl";
    private static final String WAVEFORM_REQUEST_FILE = "waveform_ingest_request.json";
    private static final String WAVEFORM_BATCH_FILE = "waveform_channel_batch.jsonl";

    private final ObjectMapper objectMapper;
    private final Path outputRoot;

    public DerivedOutputReader(ObjectMapper objectMapper, @Value("${app.derivation.output-root}") String outputRoot) {
        this.objectMapper = objectMapper;
        this.outputRoot = Path.of(outputRoot);
    }

    public DerivedPayload read(int shotNo) {
        Path outputDir = outputRoot.resolve(String.valueOf(shotNo));
        if (!Files.isDirectory(outputDir)) {
            throw new IllegalStateException("Derived output not found: " + outputDir);
        }
        DerivedPayload payload = new DerivedPayload();
        payload.setArtifacts(readArtifacts(outputDir.resolve(ARTIFACT_FILE)));
        payload.setShotMeta(readJson(outputDir.resolve(SHOT_META_FILE)));
        payload.setSignalCatalog(readJsonLines(outputDir.resolve(SIGNAL_CATALOG_FILE)));
        payload.setOperationEvents(readJsonLines(outputDir.resolve(OPERATION_EVENTS_FILE)));
        payload.setProtectionEvents(readJsonLines(outputDir.resolve(PROTECTION_EVENTS_FILE)));
        payload.setWaveformRequest(readJson(outputDir.resolve(WAVEFORM_REQUEST_FILE)));
        payload.setWaveformBatches(readJsonLines(outputDir.resolve(WAVEFORM_BATCH_FILE)));
        return payload;
    }

    private List<Map<String, Object>> readArtifacts(Path path) {
        Map<String, Object> root = readJson(path);
        Object artifacts = root.get("artifacts");
        if (artifacts instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) artifacts;
            return list;
        }
        return List.of();
    }

    private Map<String, Object> readJson(Path path) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read derived JSON: " + path, ex);
        }
    }

    private List<Map<String, Object>> readJsonLines(Path path) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                if (line.isBlank()) {
                    continue;
                }
                rows.add(objectMapper.readValue(line, MAP_TYPE));
            }
            return rows;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read derived JSONL: " + path, ex);
        }
    }
}
