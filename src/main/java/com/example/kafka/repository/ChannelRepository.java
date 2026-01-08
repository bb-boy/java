package com.example.kafka.repository;

import com.example.kafka.entity.ChannelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<ChannelEntity, Long> {
    List<ChannelEntity> findByShotNoAndDataTypeOrderByChannelName(Integer shotNo, String dataType);
    Optional<ChannelEntity> findByShotNoAndChannelNameAndDataType(Integer shotNo, String channelName, String dataType);
}