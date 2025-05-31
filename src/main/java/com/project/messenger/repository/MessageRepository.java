package com.project.messenger.repository;

import com.project.messenger.model.Message;
import com.project.messenger.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT DISTINCT m FROM Message m LEFT JOIN FETCH m.readBy LEFT JOIN FETCH m.files WHERE m.chat.id = :chatId")
    List<Message> findByChatId(@Param("chatId") Long chatId);
    @Query("SELECT DISTINCT m FROM Message m LEFT JOIN FETCH m.readBy LEFT JOIN FETCH m.files WHERE m.chat.id = :chatId AND m.isRead = false")
    List<Message> findByChatIdAndIsReadFalse(@Param("chatId") Long chatId);
    @Query("SELECT DISTINCT m FROM Message m LEFT JOIN FETCH m.files WHERE m.chat.id = :chatId AND m.isRead = false")
    List<Message> findUnreadByPersonalChatIdAndUser(@Param("chatId") Long chatId);
    @Query("SELECT DISTINCT m FROM Message m LEFT JOIN FETCH m.readBy LEFT JOIN FETCH m.files WHERE m.chat.id = :chatId AND :user NOT MEMBER OF m.readBy")
    List<Message> findUnreadByGroupChatIdAndUser(@Param("chatId") Long chatId, @Param("user") User user);
    @Query("SELECT DISTINCT m FROM Message m LEFT JOIN FETCH m.files WHERE m.chat.id = :chatId")
    Page<Message> findByChatIdOrderByTimestampDesc(@Param("chatId") Long chatId, Pageable pageable);
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO message_read (message_id, user_id) " +
            "SELECT m.id, :userId FROM messages m WHERE m.chat_id = :chatId " +
            "ON CONFLICT DO NOTHING", nativeQuery = true)
    void markMessagesAsReadByUser(@Param("chatId") Long chatId, @Param("userId") Long userId);
}