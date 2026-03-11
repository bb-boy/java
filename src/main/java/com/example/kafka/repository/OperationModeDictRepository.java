package com.example.kafka.repository;

import com.example.kafka.entity.OperationModeDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationModeDictRepository extends JpaRepository<OperationModeDictEntity, String> {
}
