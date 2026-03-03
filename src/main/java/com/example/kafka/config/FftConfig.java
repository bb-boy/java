package com.example.kafka.config;

import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FftConfig {

    @Bean
    public FastFourierTransformer fastFourierTransformer() {
        return new FastFourierTransformer(DftNormalization.STANDARD);
    }
}
