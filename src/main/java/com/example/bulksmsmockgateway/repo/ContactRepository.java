package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.entities.ContactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContactRepository extends JpaRepository<ContactEntity, Long> {
    Optional<ContactEntity> findByPhoneNumber(String phoneNumber);
}
