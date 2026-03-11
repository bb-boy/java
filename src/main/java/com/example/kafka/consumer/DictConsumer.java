package com.example.kafka.consumer;

import com.example.kafka.config.TopicNames;
import com.example.kafka.service.DictProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class DictConsumer {
    private final KafkaRecordProcessor processor;
    private final DictProjectionService projectionService;

    public DictConsumer(
        KafkaRecordProcessor processor,
        DictProjectionService projectionService
    ) {
        this.processor = processor;
        this.projectionService = projectionService;
    }

    @KafkaListener(topics = TopicNames.DICT_PROTECTION_TYPE, groupId = "${app.kafka.group-id}")
    public void onProtectionType(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleProtectionType);
    }

    @KafkaListener(topics = TopicNames.DICT_OPERATION_MODE, groupId = "${app.kafka.group-id}")
    public void onOperationMode(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleOperationMode);
    }

    @KafkaListener(topics = TopicNames.DICT_OPERATION_TASK, groupId = "${app.kafka.group-id}")
    public void onOperationTask(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleOperationTask);
    }

    @KafkaListener(topics = TopicNames.DICT_OPERATION_TYPE, groupId = "${app.kafka.group-id}")
    public void onOperationType(ConsumerRecord<String, String> record, Acknowledgment ack) {
        processor.processRecord(record, ack, projectionService::handleOperationType);
    }
}
