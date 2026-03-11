package com.example.kafka.model;

import java.time.LocalDateTime;

public record WaveformPoint(LocalDateTime time, Double value) {
}
