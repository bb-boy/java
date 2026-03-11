package com.example.kafka.repository;

import com.example.kafka.entity.EventOperationDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOperationDetailRepository extends JpaRepository<EventOperationDetailEntity, Long> {
}
