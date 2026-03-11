package com.example.kafka.consumer;

import com.example.kafka.config.TopicNames;
import com.example.kafka.service.EventProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class EventConsumer {
    private final KafkaRecordProcessor processor;
    private final EventProjectionService projectionService;

    public EventConsumer(
        KafkaRecordProcessor processor,
        EventProjectionService projectionService
    ) {
        this.processor = processor;
        this.projectionService = projectionService;
    }

    @KafkaListener(topics = TopicNames.EVENT_OPERATION, groupId = "${app.kafka.group-id}")
    public void onOperationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleOperationEvent);
    }

    @KafkaListener(topics = TopicNames.EVENT_PROTECTION, groupId = "${app.kafka.group-id}")
    public void onProtectionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleProtectionEvent);
    }
}
