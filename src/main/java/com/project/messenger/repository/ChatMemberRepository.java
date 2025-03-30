package com.project.messenger.repository;

import com.project.messenger.model.Chat;
import com.project.messenger.model.ChatMember;
import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMemberRepository extends JpaRepository<ChatMember, Long> {
    List<ChatMember> findByChatId(Long chatId);
    Optional<ChatMember> findByChatIdAndUserId(Long chatId, Long userId);
    boolean existsByChatIdAndUserId(Long chatId, Long userId);
    Optional<ChatMember> findByChatIdAndUserEmail(Long chatId, String email);
}