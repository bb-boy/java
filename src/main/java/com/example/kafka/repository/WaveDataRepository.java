package com.example.kafka.repository;

import com.example.kafka.entity.WaveDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 波形数据Repository
 */
@Repository
public interface WaveDataRepository extends JpaRepository<WaveDataEntity, Long> {
    
    /**
     * 根据炮号和通道查询
     */
    Optional<WaveDataEntity> findByShotNoAndChannelNameAndDataType(
        Integer shotNo, String channelName, String dataType);
    
    /**
     * 获取指定炮号的所有通道名称
     */
    @Query("SELECT DISTINCT w.channelName FROM WaveDataEntity w WHERE w.shotNo = :shotNo AND w.dataType = :dataType")
    List<String> findChannelNamesByShotNoAndDataType(
        @Param("shotNo") Integer shotNo, 
        @Param("dataType") String dataType);
    
    /**
     * 获取指定炮号的所有波形数据
     */
    List<WaveDataEntity> findByShotNoAndDataType(Integer shotNo, String dataType);
    
    /**
     * 获取指定炮号的所有波形数据(所有类型)
     */
    List<WaveDataEntity> findByShotNo(Integer shotNo);
    
    /**
     * 获取指定数据类型的所有波形数据
     */
    List<WaveDataEntity> findByDataType(String dataType);
    
    /**
     * 删除指定炮号的所有波形数据
     */
    void deleteByShotNo(Integer shotNo);
    
    /**
     * 检查是否存在指定炮号的波形数据
     */
    boolean existsByShotNo(Integer shotNo);
}
