package com.example.kafka.service;

import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.entity.WaveformMetadataEntity;
import com.example.kafka.model.SyntheticGenerationRequest;
import com.example.kafka.model.SyntheticGenerationResult;
import com.example.kafka.producer.DataProducer;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ProtectionEventRepository;
import com.example.kafka.repository.UserRepository;
import com.example.kafka.repository.WaveDataRepository;
import com.example.kafka.repository.WaveformMetadataRepository;
import com.example.kafka.util.WaveformCompression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class SyntheticDataGeneratorServiceTest {

    @Autowired
    private SyntheticDataGeneratorService generatorService;

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

    @MockBean
    private DataProducer dataProducer;

    @BeforeEach
    void setUp() {
        protectionEventRepository.deleteAll();
        operationLogRepository.deleteAll();
        waveformMetadataRepository.deleteAll();
        waveDataRepository.deleteAll();
        userRepository.deleteAll();
        when(dataProducer.sendOperationLog(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void generateCreatesLogsAndEvents() {
        int shotNo = 1001;
        String channelName = "InPower";
        String dataType = "Tube";
        LocalDateTime startTime = LocalDateTime.of(2026, 3, 2, 10, 0, 0);
        double sampleRate = 1000.0d;
        List<Double> values = buildValues(200);

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
            3,
            2,
            0.8d,
            true
        );

        SyntheticGenerationResult result = generatorService.generateProtectionOperation(request);

        assertThat(result.operationCount()).isEqualTo(3);
        assertThat(result.protectionCount()).isEqualTo(2);
        verify(dataProducer, times(result.operationCount())).sendOperationLog(any());
        assertThat(protectionEventRepository.findByShotNoOrderByTriggerTimeAsc(shotNo)).hasSize(2);
    }

    private List<Double> buildValues(int count) {
        return IntStream.range(0, count)
            .mapToDouble(i -> Math.sin(i / 10.0d) * 10.0d)
            .boxed()
            .toList();
    }
}
