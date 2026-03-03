package com.example.kafka.repository;

import com.example.kafka.entity.ProtectionEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProtectionEventRepository extends JpaRepository<ProtectionEventEntity, Long> {
    List<ProtectionEventEntity> findByShotNoAndTriggerTimeBetweenOrderByTriggerTimeAsc(
        Integer shotNo,
        LocalDateTime start,
        LocalDateTime end
    );

    List<ProtectionEventEntity> findByShotNoOrderByTriggerTimeAsc(Integer shotNo);

    List<ProtectionEventEntity> findByShotNoAndSeverityInOrderByTriggerTimeAsc(
        Integer shotNo,
        List<String> severity
    );

    void deleteByShotNo(Integer shotNo);
}
