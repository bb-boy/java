package com.example.kafka.repository;

import com.example.kafka.entity.EventEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventEntity, Long> {
    List<EventEntity> findByShotNoAndEventFamilyOrderByEventTimeAsc(
        Integer shotNo,
        String eventFamily
    );

    List<EventEntity> findByShotNoAndEventFamilyAndEventTimeBetweenOrderByEventTimeAsc(
        Integer shotNo,
        String eventFamily,
        LocalDateTime start,
        LocalDateTime end
    );

    Optional<EventEntity> findByDedupKey(String dedupKey);
}
