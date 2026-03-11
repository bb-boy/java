package com.example.kafka.repository;

import com.example.kafka.entity.OperationTaskDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationTaskDictRepository extends JpaRepository<OperationTaskDictEntity, String> {
}
