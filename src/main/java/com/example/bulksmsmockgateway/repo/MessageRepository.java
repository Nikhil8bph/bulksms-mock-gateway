package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.entities.MessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<MessageEntity, Long>, JpaSpecificationExecutor<MessageEntity> {
    List<MessageEntity> findBySubmissionIdOrderById(long submissionId);
    List<MessageEntity> findByRelatedSentMessageIdAndDirectionOrderById(long id, String direction);
    List<MessageEntity> findByMailDueLessThanEqualOrderByMailDueAscIdAsc(Instant cutoff, Pageable page);
    Optional<MessageEntity> findFirstByDestinationAndOriginAndDirectionAndRepliableTrueAndStatusTypeAndAvailableAtLessThanEqualOrderByIdDesc(
            String destination, String origin, String direction, String statusType, Instant now);
    long countByDirectionAndSubmittedAtGreaterThanEqual(String direction, Instant since);
}
