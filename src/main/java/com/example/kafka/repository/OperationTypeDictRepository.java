package com.example.kafka.repository;

import com.example.kafka.entity.OperationTypeDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationTypeDictRepository extends JpaRepository<OperationTypeDictEntity, String> {
}
