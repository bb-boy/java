package com.example.kafka.repository;

import com.example.kafka.entity.ShotMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 炮号元数据Repository
 */
@Repository
public interface ShotMetadataRepository extends JpaRepository<ShotMetadataEntity, Integer> {
    
    /**
     * 获取所有炮号,按炮号排序
     */
    @Query("SELECT s.shotNo FROM ShotMetadataEntity s ORDER BY s.shotNo")
    List<Integer> findAllShotNumbers();
    
    /**
     * 根据状态查询
     */
    List<ShotMetadataEntity> findByStatus(String status);
    
    /**
     * 根据数据源类型查询
     */
    List<ShotMetadataEntity> findBySourceType(ShotMetadataEntity.DataSourceType sourceType);
    
    /**
     * 查询最近的N条记录
     */
    @Query("SELECT s FROM ShotMetadataEntity s ORDER BY s.createdAt DESC LIMIT ?1")
    List<ShotMetadataEntity> findRecentShots(int limit);
}
