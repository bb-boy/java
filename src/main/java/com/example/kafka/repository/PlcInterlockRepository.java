package com.example.kafka.repository;

import com.example.kafka.entity.PlcInterlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PLC互锁日志Repository
 */
@Repository
public interface PlcInterlockRepository extends JpaRepository<PlcInterlockEntity, Long> {
    
    /**
     * 根据炮号查询,按时间排序
     */
    List<PlcInterlockEntity> findByShotNoOrderByTimestampAsc(Integer shotNo);
    
    /**
     * 根据炮号和互锁名称查询
     */
    List<PlcInterlockEntity> findByShotNoAndInterlockName(Integer shotNo, String interlockName);
    
    /**
     * 根据状态查询(查找触发的互锁)
     */
    List<PlcInterlockEntity> findByShotNoAndStatus(Integer shotNo, Boolean status);
    
    /**
     * 获取指定炮号的所有互锁名称
     */
    @Query("SELECT DISTINCT p.interlockName FROM PlcInterlockEntity p WHERE p.shotNo = :shotNo")
    List<String> findInterlockNamesByShotNo(@Param("shotNo") Integer shotNo);
    
    /**
     * 时间范围查询
     */
    @Query("SELECT p FROM PlcInterlockEntity p WHERE p.shotNo = :shotNo " +
           "AND p.timestamp BETWEEN :startTime AND :endTime ORDER BY p.timestamp")
    List<PlcInterlockEntity> findByShotNoAndTimeRange(
        @Param("shotNo") Integer shotNo,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计触发次数
     */
    long countByShotNoAndStatus(Integer shotNo, Boolean status);
    
    /**
     * 删除指定炮号的PLC日志
     */
    void deleteByShotNo(Integer shotNo);
}
