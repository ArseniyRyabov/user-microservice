package com.github.arseniyryabov.user.kafka;

import com.github.arseniyryabov.user.event.UserCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class UserCreatedKafkaPublisher {

    private static final String TOPIC = "user-created";

    private final KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    public UserCreatedKafkaPublisher(KafkaTemplate<String, UserCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        log.info("Sending UserCreatedEvent to Kafka: eventId={}, userId={}",
                event.eventId(), event.userId());

        kafkaTemplate.send(TOPIC, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("UserCreatedEvent sent: eventId={}, offset={}",
                                event.eventId(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send UserCreatedEvent: eventId={}",
                                event.eventId(), ex);
                    }
                });
    }
}
