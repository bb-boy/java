package com.example.kafka.model;

public record WaveformStats(
    double min,
    double max,
    double rms,
    double peakToPeak
) {}
