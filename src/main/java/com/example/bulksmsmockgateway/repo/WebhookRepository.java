package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.entities.WebhookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookRepository extends JpaRepository<WebhookEntity, Long> {
    Optional<WebhookEntity> findByName(String name);
    List<WebhookEntity> findByActiveTrueAndTriggerScope(String triggerScope);
}
