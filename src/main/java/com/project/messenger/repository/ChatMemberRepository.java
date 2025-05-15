package com.project.messenger.repository;

import com.project.messenger.model.ChatMember;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
    Page<ChatMember> findByChatId(Long chatId, Pageable pageable);
    Optional<ChatMember> findByChatIdAndUserId(Long chatId, Long userId);
    boolean existsByChatIdAndUserId(Long chatId, Long userId);
    Optional<ChatMember> findByChatIdAndUserEmail(Long chatId, String email);
    boolean existsByChatIdAndUserEmail(Long chatId, String email);
}