package com.example.kafka.controller;

import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.entity.WaveformMetadataEntity;
import com.example.kafka.model.SyntheticGenerationRequest;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ProtectionEventRepository;
import com.example.kafka.repository.UserRepository;
import com.example.kafka.repository.WaveDataRepository;
import com.example.kafka.repository.WaveformMetadataRepository;
import com.example.kafka.util.WaveformCompression;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EcrhControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WaveDataRepository waveDataRepository;

    @Autowired
    private WaveformMetadataRepository waveformMetadataRepository;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private ProtectionEventRepository protectionEventRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        protectionEventRepository.deleteAll();
        operationLogRepository.deleteAll();
        waveformMetadataRepository.deleteAll();
        waveDataRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void generateEndpointCreatesEventsAndFilters() throws Exception {
        int shotNo = 1002;
        String channelName = "InPower";
        String dataType = "Tube";
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 2, 11, 0, 0);
        double sampleRate = 500.0d;
        List<Double> values = buildValues(120);

        WaveDataEntity waveData = new WaveDataEntity();
        waveData.setShotNo(shotNo);
        waveData.setChannelName(channelName);
        waveData.setDataType(dataType);
        waveData.setStartTime(startTime);
        waveData.setSampleRate(sampleRate);
        waveData.setSamples(values.size());
        waveData.setData(WaveformCompression.compress(values));
        waveDataRepository.save(waveData);

        WaveformMetadataEntity metadata = new WaveformMetadataEntity();
        metadata.setShotNo(shotNo);
        metadata.setSystemName("ECRH");
        metadata.setDeviceId(dataType);
        metadata.setChannelName(channelName);
        metadata.setSampleRateHz(sampleRate);
        metadata.setStartTime(startTime);
        metadata.setEndTime(startTime.plusNanos((long) ((values.size() - 1) / sampleRate * 1_000_000_000L)));
        metadata.setTotalSamples((long) values.size());
        waveformMetadataRepository.save(metadata);

        SyntheticGenerationRequest request = new SyntheticGenerationRequest(
            shotNo,
            channelName,
            dataType,
            4,
            2,
            0.85d,
            true
        );

        mockMvc.perform(post("/api/ecrh/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operationCount").value(4))
            .andExpect(jsonPath("$.protectionCount").value(2));

        mockMvc.perform(get("/api/ecrh/protection-events")
                .param("shotNo", String.valueOf(shotNo))
                .param("severity", "CRITICAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/ecrh/operation-logs")
                .param("shotNo", String.valueOf(shotNo))
                .param("command", "SET_POWER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    private List<Double> buildValues(int count) {
        return IntStream.range(0, count)
            .mapToDouble(i -> Math.cos(i / 8.0d) * 5.0d)
            .boxed()
            .toList();
    }
}
