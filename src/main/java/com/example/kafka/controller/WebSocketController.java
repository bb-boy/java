package com.example.kafka.controller;

import com.example.kafka.model.*;
import com.example.kafka.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * WebSocket控制器 - 提供实时数据推送
 */
@Controller
public class WebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private DataService dataService;
    
    /**
     * 客户端请求订阅指定炮号的实时数据
     * 发送到 /app/subscribe/{shotNo}
     * 响应到 /topic/shot/{shotNo}
     */
    @MessageMapping("/subscribe/{shotNo}")
    @SendTo("/topic/shot/{shotNo}")
    public DataService.ShotCompleteData subscribeShot(Integer shotNo) {
        logger.info("客户端订阅炮号: {}", shotNo);
        return dataService.getCompleteData(shotNo);
    }
    
    /**
     * 定时推送最新炮号列表 (每5秒)
     */
    @Scheduled(fixedRate = 5000)
    public void pushShotList() {
        List<Integer> shots = dataService.getAllShotNumbers();
        messagingTemplate.convertAndSend("/topic/shots", shots);
    }
    
    /**
     * 推送新的波形数据
     */
    public void pushWaveData(Integer shotNo, WaveData waveData) {
        messagingTemplate.convertAndSend(
            "/topic/shot/" + shotNo + "/wave", 
            waveData
        );
    }
    
    /**
     * 推送新的操作日志
     */
    public void pushOperationLog(Integer shotNo, OperationLog log) {
        messagingTemplate.convertAndSend(
            "/topic/shot/" + shotNo + "/operation", 
            log
        );
    }
    
    /**
     * 推送PLC互锁数据
     */
    public void pushPlcInterlock(Integer shotNo, PlcInterlock interlock) {
        messagingTemplate.convertAndSend(
            "/topic/shot/" + shotNo + "/plc", 
            interlock
        );
    }
    
    /**
     * 推送数据源状态变化
     */
    public void pushDataSourceStatus() {
        messagingTemplate.convertAndSend(
            "/topic/status", 
            dataService.getDataSourceStatus()
        );
    }
}
