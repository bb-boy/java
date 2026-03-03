package com.example.kafka.repository;

import com.example.kafka.entity.WaveformMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WaveformMetadataRepository extends JpaRepository<WaveformMetadataEntity, Long> {
    Optional<WaveformMetadataEntity> findByShotNoAndDeviceIdAndChannelName(
        Integer shotNo,
        String deviceId,
        String channelName
    );
}
