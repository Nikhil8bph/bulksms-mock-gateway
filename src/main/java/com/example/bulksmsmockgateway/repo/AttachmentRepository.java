package com.example.bulksmsmockgateway.repo;

import com.example.bulksmsmockgateway.entities.AttachmentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, String> {
    @Modifying
    @Query("delete from AttachmentEntity a where a.expires <= :now")
    void deleteExpired(Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AttachmentEntity a where a.id = :id")
    Optional<AttachmentEntity> lockById(String id);
}
