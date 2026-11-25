package com.example.bulksmsmockgateway.repo;


import com.example.bulksmsmockgateway.entities.GroupMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, Long> {
    List<GroupMemberEntity> findByGroupIdOrderById(String groupId);
    List<GroupMemberEntity> findByGroupNameOrderById(String groupName);
}
