package com.example.kafka.consumer;

import com.example.kafka.repository.ProtectionEventRepository;
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
class ProtectionEventConsumerTest {

    @Autowired
    private DataConsumer dataConsumer;

    @Autowired
    private ProtectionEventRepository protectionEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        protectionEventRepository.deleteAll();
    }

    @Test
    void consumeProtectionEventPersistsToDatabase() throws Exception {
        int shotNo = 4001;
        Map<String, Object> payload = Map.of(
            "shotNo", shotNo,
            "triggerTime", "2026-03-02 10:00:00.000",
            "interlockName", "AMPLITUDE_HIGH",
            "severity", "CRITICAL",
            "protectionLevel", "FAST",
            "deviceId", "TDMS"
        );

        String json = objectMapper.writeValueAsString(payload);
        dataConsumer.consumeProtectionEvent(json);

        assertThat(protectionEventRepository.findByShotNoOrderByTriggerTimeAsc(shotNo))
            .hasSize(1);
    }
}
