package com.example.kafka.service;

import com.example.kafka.datasource.FileDataSource;
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
class DataPipelineServiceTdmsGenerationTest {

    @Autowired
    private DataPipelineService dataPipelineService;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private ProtectionEventRepository protectionEventRepository;

    @MockBean
    private FileDataSource fileDataSource;

    @MockBean
    private DataProducer dataProducer;

    @BeforeEach
    void setUp() {
        protectionEventRepository.deleteAll();
        operationLogRepository.deleteAll();
    }

    @Test
    void syncShotGeneratesTdmsLogsAndEvents() {
        int shotNo = 3001;
        LocalDateTime start = LocalDateTime.of(2026, 3, 2, 9, 0, 0);

        ShotMetadata metadata = new ShotMetadata(shotNo);
        metadata.setSampleRate(1000.0d);
        metadata.setStartTime(start);
        metadata.setEndTime(start.plusSeconds(1));

        WaveData waveData = new WaveData(shotNo, "CH1");
        waveData.setFileSource("data/TUBE/3001/3001_Tube.tdms");
        waveData.setStartTime(start);
        waveData.setSampleRate(1000.0d);
        waveData.setData(buildStepValues());

        when(fileDataSource.getShotMetadata(shotNo)).thenReturn(metadata);
        when(fileDataSource.getChannelNames(shotNo, "Tube")).thenReturn(List.of("CH1"));
        when(fileDataSource.getChannelNames(shotNo, "Water")).thenReturn(List.of());
        when(fileDataSource.getWaveData(shotNo, "CH1", "Tube")).thenReturn(waveData);
        when(fileDataSource.getPlcInterlocks(shotNo)).thenReturn(List.of());
        when(dataProducer.sendMetadata(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(dataProducer.sendWaveData(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(dataProducer.sendOperationLog(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(dataProducer.sendProtectionEvent(any())).thenReturn(CompletableFuture.completedFuture(null));

        DataPipelineService.SyncResult result = dataPipelineService.syncShotToKafka(shotNo);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOperationLogCount()).isGreaterThan(0);
        assertThat(result.getProtectionEventCount()).isGreaterThan(0);
        verify(dataProducer, times(result.getOperationLogCount())).sendOperationLog(any());
        verify(dataProducer, times(result.getProtectionEventCount())).sendProtectionEvent(any());
    }

    private List<Double> buildStepValues() {
        List<Double> values = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            values.add(0.0d);
        }
        for (int i = 0; i < 40; i++) {
            values.add(12.0d);
        }
        return values;
    }
}
