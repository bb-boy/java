package com.example.kafka.repository;

import com.example.kafka.entity.ProtectionTypeDictEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtectionTypeDictRepository extends JpaRepository<ProtectionTypeDictEntity, String> {
}
