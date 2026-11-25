package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.entities.WebhookDeliveryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDeliveryEntity, Long> {
    @Modifying
    @Query("""
            update WebhookDeliveryEntity d set d.due = null, d.lease = null
            where d.webhookId = :webhookId and d.due is not null
            """)
    void cancelPending(long webhookId);

    Optional<WebhookDeliveryEntity> findFirstByDueLessThanEqualOrderByDueAscIdAsc(Instant now);
    List<WebhookDeliveryEntity> findByWebhookIdAndAttemptAndDueLessThanEqualOrderByDueAscIdAsc(
            long webhookId, int attempt, Instant now, Pageable page);
}
