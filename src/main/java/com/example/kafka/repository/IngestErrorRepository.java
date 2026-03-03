package com.example.kafka.repository;

import com.example.kafka.entity.IngestErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IngestErrorRepository extends JpaRepository<IngestErrorEntity, Long> {
    List<IngestErrorEntity> findByShotNoOrderByEventTimeDesc(Integer shotNo);
    List<IngestErrorEntity> findByTopicOrderByEventTimeDesc(String topic);
    List<IngestErrorEntity> findByEventTimeAfterOrderByEventTimeDesc(LocalDateTime since);
}
