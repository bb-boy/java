package com.example.kafka.repository;

import com.example.kafka.entity.SignalCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalCatalogRepository extends JpaRepository<SignalCatalogEntity, String> {
}
