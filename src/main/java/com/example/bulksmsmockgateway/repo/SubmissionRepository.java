package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.entities.SubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {
    Optional<SubmissionEntity> findFirstByDeduplicationIdAndSubmittedAtGreaterThanEqualOrderByIdDesc(
            int deduplicationId, Instant cutoff);
}
