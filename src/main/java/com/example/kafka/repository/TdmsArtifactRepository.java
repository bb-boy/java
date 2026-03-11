package com.example.kafka.repository;

import com.example.kafka.entity.TdmsArtifactEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TdmsArtifactRepository extends JpaRepository<TdmsArtifactEntity, String> {
    Optional<TdmsArtifactEntity> findBySha256Hex(String sha256Hex);
}
