package com.example.kafka.repository;

import com.example.kafka.entity.SignalSourceMapEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalSourceMapRepository extends JpaRepository<SignalSourceMapEntity, Long> {
    List<SignalSourceMapEntity> findByDataTypeOrderBySourceNameAsc(String dataType);

    Optional<SignalSourceMapEntity> findBySourceSystemAndSourceTypeAndSourceNameAndDataType(
        String sourceSystem,
        String sourceType,
        String sourceName,
        String dataType
    );
}
