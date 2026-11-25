package com.example.bulksmsmockgateway.repo;

import com.example.bulksmsmockgateway.entities.TransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<TransferEntity, Long> {
}
