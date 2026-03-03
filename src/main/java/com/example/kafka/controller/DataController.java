package com.example.kafka.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据查询控制器（直读模式已移除）
 */
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class DataController {

    private static final Map<String, String> DIRECT_READ_REMOVED = Map.of(
        "status", "error",
        "message", "直读模式已移除，请先通过 /api/kafka/sync/* 同步，再使用 /api/hybrid/* 查询。"
    );

    @RequestMapping({"", "/", "/**"})
    public ResponseEntity<Map<String, String>> directReadRemoved() {
        return ResponseEntity.status(HttpStatus.GONE).body(DIRECT_READ_REMOVED);
    }
}
