package com.example.kafka.repository;

import com.example.kafka.entity.WaveformSampleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface WaveformSampleRepository extends JpaRepository<WaveformSampleEntity, Long> {
    List<WaveformSampleEntity> findByWaveformIdAndSegmentStartTimeBetweenOrderBySegmentStartTimeAsc(
        Long waveformId,
        LocalDateTime start,
        LocalDateTime end
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from WaveformSampleEntity s where s.waveformId = :waveformId")
    int deleteByWaveformId(@Param("waveformId") Long waveformId);
}
