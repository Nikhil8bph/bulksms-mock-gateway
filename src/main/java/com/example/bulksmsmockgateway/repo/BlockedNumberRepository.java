package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.entities.BlockedNumberEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BlockedNumberRepository extends JpaRepository<BlockedNumberEntity, Long> {
    boolean existsByPhoneNumber(String phoneNumber);
    List<BlockedNumberEntity> findByPhoneNumberIn(Collection<String> phoneNumbers);
    List<BlockedNumberEntity> findByIdGreaterThanEqualOrderById(long minId, Pageable page);
}
