package com.example.kafka.repository;

import com.example.kafka.entity.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, String> {
    List<SystemConfigEntity> findByScopeOrderByUpdatedAtDesc(String scope);
}
