package com.example.kafka.controller;

import com.example.kafka.service.TdmsIngestService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ingest")
public class IngestController {
    private final TdmsIngestService ingestService;

    public IngestController(TdmsIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/shot")
    public ResponseEntity<Map<String, String>> ingestShot(@RequestParam Integer shotNo) {
        ingestService.ingestShot(shotNo);
        return ResponseEntity.ok(Map.of("status", "accepted", "shotNo", shotNo.toString()));
    }
}
