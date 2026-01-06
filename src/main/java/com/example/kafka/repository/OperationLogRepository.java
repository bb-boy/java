package com.example.kafka.repository;

import com.example.kafka.entity.OperationLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志Repository
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {
    
    /**
     * 根据炮号查询,按时间排序
     */
    List<OperationLogEntity> findByShotNoOrderByTimestampAsc(Integer shotNo);
    
    /**
     * 根据炮号和通道名查询
     */
    List<OperationLogEntity> findByShotNoAndChannelNameOrderByTimestampAsc(
        Integer shotNo, String channelName);
    
    /**
     * 根据炮号和操作类型查询
     */
    List<OperationLogEntity> findByShotNoAndOperationType(Integer shotNo, String operationType);
    
    /**
     * 时间范围查询
     */
    @Query("SELECT o FROM OperationLogEntity o WHERE o.shotNo = :shotNo " +
           "AND o.timestamp BETWEEN :startTime AND :endTime ORDER BY o.timestamp")
    List<OperationLogEntity> findByShotNoAndTimeRange(
        @Param("shotNo") Integer shotNo,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计某炮号的操作次数
     */
    long countByShotNo(Integer shotNo);
    
    /**
     * 删除指定炮号的操作日志
     */
    void deleteByShotNo(Integer shotNo);
}
