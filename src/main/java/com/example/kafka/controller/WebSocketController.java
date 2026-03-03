package com.example.kafka.controller;

import com.example.kafka.model.OperationLog;
import com.example.kafka.model.PlcInterlock;
import com.example.kafka.model.WaveData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * WebSocket控制器 - 提供实时数据推送
 */
@Controller
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    private static final Map<String, String> DIRECT_READ_REMOVED = Map.of(
        "status", "error",
        "message", "直读模式已移除，请通过 /api/kafka/sync/* 同步后使用 /api/hybrid/* 查询。"
    );

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 客户端请求订阅指定炮号的实时数据
     * 发送到 /app/subscribe/{shotNo}
     * 响应到 /topic/shot/{shotNo}
     */
    @MessageMapping("/subscribe/{shotNo}")
    @SendTo("/topic/shot/{shotNo}")
    public Map<String, String> subscribeShot(Integer shotNo) {
        logger.warn("WebSocket订阅请求已禁用直读模式: shotNo={}", shotNo);
        return DIRECT_READ_REMOVED;
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
        messagingTemplate.convertAndSend("/topic/status", DIRECT_READ_REMOVED);
    }
}
