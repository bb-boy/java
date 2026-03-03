package com.example.kafka.service;

import com.example.kafka.model.ShotMetadata;
import com.example.kafka.model.WaveData;
import com.example.kafka.producer.DataProducer;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ProtectionEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class TdmsEventGeneratorServiceTest {

    @Autowired
    private TdmsEventGeneratorService tdmsEventGeneratorService;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private ProtectionEventRepository protectionEventRepository;

    @MockBean
    private DataProducer dataProducer;

    @BeforeEach
    void setUp() {
        protectionEventRepository.deleteAll();
        operationLogRepository.deleteAll();
        when(dataProducer.sendOperationLog(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(dataProducer.sendProtectionEvent(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void generateFromWaveDataCreatesLogsAndEvents() {
        int shotNo = 2001;
        LocalDateTime start = LocalDateTime.of(2026, 3, 2, 9, 0, 0);
        ShotMetadata metadata = new ShotMetadata(shotNo);
        metadata.setStartTime(start);
        metadata.setSampleRate(1000.0d);

        WaveData waveData = new WaveData(shotNo, "TestChannel");
        waveData.setFileSource("data/TUBE/2001/2001_Tube.tdms");
        waveData.setStartTime(start);
        waveData.setSampleRate(1000.0d);
        waveData.setData(buildStepValues());

        TdmsEventGeneratorService.GenerationResult result =
            tdmsEventGeneratorService.generateFromWaveData(metadata, waveData, true);

        assertThat(result.operationCount()).isGreaterThan(0);
        assertThat(result.protectionCount()).isGreaterThan(0);
        verify(dataProducer, times(result.operationCount())).sendOperationLog(any());
        verify(dataProducer, times(result.protectionCount())).sendProtectionEvent(any());
    }

    private List<Double> buildStepValues() {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            values.add(0.0d);
        }
        for (int i = 0; i < 50; i++) {
            values.add(10.0d);
        }
        return values;
    }
}
