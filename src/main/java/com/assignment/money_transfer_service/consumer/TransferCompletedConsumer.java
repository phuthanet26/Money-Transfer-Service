package com.assignment.money_transfer_service.consumer;

import com.assignment.money_transfer_service.service.RedisLockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransferCompletedConsumer {

    private final ObjectMapper objectMapper;
    private final RedisLockService redisLockService;

    @JmsListener(destination = "TRANSFER.COMPLETED", containerFactory = "jmsListenerContainerFactory")
    public void handleTransferCompleted(Message message) {
        try {
            if (message instanceof TextMessage textMessage) {
                String payload = textMessage.getText();
                JsonNode jsonNode = objectMapper.readTree(payload);
                
                String eventId = jsonNode.get("eventId").asText();
                
                String dedupeKey = "processed:events";
                boolean alreadyProcessed = redisLockService.isEventProcessed(dedupeKey, eventId);
                
                if (alreadyProcessed) {
                    log.info("Event already processed, skipping: {}", eventId);
                    return;
                }
                
                log.info("Processing TransferCompleted event: {}", eventId);
                log.info("Transfer ID: {}", jsonNode.get("transferId").asText());
                log.info("From Account: {}", jsonNode.get("fromAccountId").asText());
                log.info("To Account: {}", jsonNode.get("toAccountId").asText());
                log.info("Amount: {} {}", jsonNode.get("amount").asText(), jsonNode.get("currency").asText());
                
                redisLockService.markEventProcessed(dedupeKey, eventId);
                
                log.info("Successfully processed TransferCompleted event: {}", eventId);
            }
        } catch (Exception e) {
            log.error("Failed to process TransferCompleted event", e);
            throw new RuntimeException("Failed to process event", e);
        }
    }
}
