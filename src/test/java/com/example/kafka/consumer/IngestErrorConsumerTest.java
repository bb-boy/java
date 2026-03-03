package com.example.kafka.consumer;

import com.example.kafka.repository.IngestErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IngestErrorConsumerTest {

    @Autowired
    private DataConsumer dataConsumer;

    @Autowired
    private IngestErrorRepository ingestErrorRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ingestErrorRepository.deleteAll();
    }

    @Test
    void consumeIngestErrorPersistsToDatabase() throws Exception {
        int shotNo = 3002;
        Map<String, Object> payload = Map.of(
            "timestamp", "2026-03-02 10:00:00.000",
            "errorType", "PRODUCE_FAILED",
            "topic", "protection-event",
            "messageKey", "3002_2026-03-02T10:00:00",
            "shotNo", shotNo,
            "dataType", "保护事件",
            "errorMessage", "Failed to send",
            "payloadSize", 128,
            "payloadPreview", "{\"shotNo\":3002}"
        );

        String json = objectMapper.writeValueAsString(payload);
        dataConsumer.consumeIngestError(json);

        assertThat(ingestErrorRepository.findByShotNoOrderByEventTimeDesc(shotNo))
            .hasSize(1);
    }
}
