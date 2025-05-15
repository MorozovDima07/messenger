package com.project.messenger.repository;

import com.project.messenger.model.BlockedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    Page<BlockedUser> findByUserId(Long userId, Pageable pageable);
    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);
    void deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);
}