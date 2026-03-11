package com.example.kafka.controller;

import com.example.kafka.entity.ShotEntity;
import com.example.kafka.repository.ShotRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shots")
public class ShotController {

    private final ShotRepository shotRepository;

    public ShotController(ShotRepository shotRepository) {
        this.shotRepository = shotRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listShots() {
        List<Map<String, Object>> result = shotRepository
            .findAll(Sort.by(Sort.Direction.DESC, "shotNo"))
            .stream()
            .map(this::toShotMap)
            .toList();
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> toShotMap(ShotEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shotNo", s.getShotNo());
        m.put("shotStartTime", s.getShotStartTime());
        m.put("shotEndTime", s.getShotEndTime());
        m.put("statusCode", s.getStatusCode());
        m.put("actualDuration", s.getActualDuration());
        return m;
    }

}
