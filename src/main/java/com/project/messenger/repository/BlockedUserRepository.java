package com.project.messenger.repository;

import com.project.messenger.model.BlockedUser;
import com.project.messenger.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlockedUserRepository extends JpaRepository<BlockedUser, Long> {
    List<BlockedUser> findByUserId(Long userId);
    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);
    void deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);
}