package com.example.kafka.ingest;

import com.example.kafka.model.*;
import com.example.kafka.producer.DataProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网络数据接收器 - 通过TCP/UDP接收外部数据并发送到Kafka
 */
@Service
public class NetworkDataReceiver {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkDataReceiver.class);
    
    @Autowired
    private DataProducer dataProducer;
    
    @Value("${app.network.enabled:false}")
    private boolean networkEnabled;
    
    @Value("${app.network.port:9999}")
    private int port;
    
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean running = false;
    private final ObjectMapper objectMapper;
    
    public NetworkDataReceiver() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @PostConstruct
    public void init() {
        if (networkEnabled) {
            startServer();
        }
    }
    
    /**
     * 启动TCP服务器
     */
    public void startServer() {
        if (running) {
            logger.warn("网络接收器已在运行");
            return;
        }
        
        executorService = Executors.newCachedThreadPool();
        
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                running = true;
                logger.info("网络数据接收器启动,监听端口: {}", port);
                
                while (running) {
                    Socket clientSocket = serverSocket.accept();
                    executorService.submit(() -> handleClient(clientSocket));
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("服务器异常", e);
                }
            }
        });
    }
    
    /**
     * 停止服务器
     */
    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            logger.info("网络数据接收器已停止");
        } catch (IOException e) {
            logger.error("停止服务器失败", e);
        }
    }
    
    /**
     * 处理客户端连接
     */
    private void handleClient(Socket socket) {
        String clientAddress = socket.getInetAddress().getHostAddress();
        logger.info("客户端连接: {}", clientAddress);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                processMessage(line, writer);
            }
            
        } catch (IOException e) {
            logger.error("处理客户端消息失败: {}", clientAddress, e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
            logger.info("客户端断开: {}", clientAddress);
        }
    }
    
    /**
     * 处理接收到的消息
     * 消息格式: TYPE|JSON_DATA
     * TYPE: METADATA, WAVE, OPERATION, PLC
     */
    private void processMessage(String message, PrintWriter writer) {
        try {
            String[] parts = message.split("\\|", 2);
            if (parts.length != 2) {
                writer.println("ERROR|Invalid message format");
                return;
            }
            
            String type = parts[0].toUpperCase();
            String jsonData = parts[1];
            
            switch (type) {
                case "METADATA":
                    ShotMetadata metadata = objectMapper.readValue(jsonData, ShotMetadata.class);
                    dataProducer.sendMetadata(metadata);
                    writer.println("OK|Metadata sent");
                    logger.info("接收到元数据: ShotNo={}", metadata.getShotNo());
                    break;
                    
                case "WAVE":
                    WaveData waveData = objectMapper.readValue(jsonData, WaveData.class);
                    waveData.setSourceType(WaveData.DataSourceType.NETWORK);
                    dataProducer.sendWaveData(waveData);
                    writer.println("OK|WaveData sent");
                    logger.info("接收到波形数据: ShotNo={}, Channel={}", 
                               waveData.getShotNo(), waveData.getChannelName());
                    break;
                    
                case "OPERATION":
                    OperationLog opLog = objectMapper.readValue(jsonData, OperationLog.class);
                    opLog.setSourceType(WaveData.DataSourceType.NETWORK);
                    dataProducer.sendOperationLog(opLog);
                    writer.println("OK|OperationLog sent");
                    logger.info("接收到操作日志: ShotNo={}", opLog.getShotNo());
                    break;
                    
                case "PLC":
                    PlcInterlock plc = objectMapper.readValue(jsonData, PlcInterlock.class);
                    plc.setSourceType(WaveData.DataSourceType.NETWORK);
                    dataProducer.sendPlcInterlock(plc);
                    writer.println("OK|PlcInterlock sent");
                    logger.info("接收到PLC互锁: ShotNo={}", plc.getShotNo());
                    break;
                    
                default:
                    writer.println("ERROR|Unknown message type: " + type);
            }
            
        } catch (Exception e) {
            logger.error("处理消息失败: {}", message, e);
            writer.println("ERROR|" + e.getMessage());
        }
    }
    
    /**
     * 手动接收并发送数据 (用于API调用)
     */
    public void receiveMetadata(ShotMetadata metadata) {
        dataProducer.sendMetadata(metadata);
    }
    
    public void receiveWaveData(WaveData waveData) {
        waveData.setSourceType(WaveData.DataSourceType.NETWORK);
        dataProducer.sendWaveData(waveData);
    }
    
    public void receiveOperationLog(OperationLog log) {
        log.setSourceType(WaveData.DataSourceType.NETWORK);
        dataProducer.sendOperationLog(log);
    }
    
    public void receivePlcInterlock(PlcInterlock interlock) {
        interlock.setSourceType(WaveData.DataSourceType.NETWORK);
        dataProducer.sendPlcInterlock(interlock);
    }
    
    public boolean isRunning() {
        return running;
    }
}
