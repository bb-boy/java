package com.example.kafka.repository;

import com.example.kafka.entity.SourceRecordEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRecordRepository extends JpaRepository<SourceRecordEntity, Long> {
    Optional<SourceRecordEntity> findByTopicNameAndPartitionIdAndOffsetValue(
        String topicName,
        Integer partitionId,
        Long offsetValue
    );
}
