package com.example.kafka.repository;

import com.example.kafka.entity.ShotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShotRepository extends JpaRepository<ShotEntity, Integer> {
}
