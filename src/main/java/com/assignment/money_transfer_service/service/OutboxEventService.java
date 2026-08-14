package com.assignment.money_transfer_service.service;

import com.assignment.money_transfer_service.domain.OutboxEventEntity;
import com.assignment.money_transfer_service.domain.OutboxStatus;
import com.assignment.money_transfer_service.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventService {

    private static final String TRANSFER_COMPLETED_QUEUE = "TRANSFER.COMPLETED";
    private static final long PUBLISH_DELAY_MS = 5000;

    private final OutboxEventRepository outboxEventRepository;
    private final JmsTemplate jmsTemplate;

    public OutboxEventEntity createOutboxEvent(String aggregateType, String aggregateId,
                                                String eventType, String payload) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setStatus(OutboxStatus.PENDING);
        event.setCreatedAt(LocalDateTime.now());
        
        return outboxEventRepository.save(event);
    }

    @Scheduled(fixedDelay = PUBLISH_DELAY_MS)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEventEntity> pendingEvents = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        
        pendingEvents.forEach(this::publishEvent);
    }

    private void publishEvent(OutboxEventEntity event) {
        try {
            jmsTemplate.convertAndSend(TRANSFER_COMPLETED_QUEUE, event.getPayload());
            markAsPublished(event);
            log.info("Published outbox event: {}", event.getId());
        } catch (Exception e) {
            markAsFailed(event);
            log.error("Failed to publish outbox event: {}", event.getId(), e);
        }
    }

    private void markAsPublished(OutboxEventEntity event) {
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(LocalDateTime.now());
        outboxEventRepository.save(event);
    }

    private void markAsFailed(OutboxEventEntity event) {
        event.setStatus(OutboxStatus.FAILED);
        outboxEventRepository.save(event);
    }
}