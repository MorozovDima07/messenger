package com.project.messenger.repository;

import com.project.messenger.model.Message;
import com.project.messenger.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m LEFT JOIN FETCH m.readBy WHERE m.chat.id = :chatId")
    List<Message> findByChatId(@Param("chatId") Long chatId);
    List<Message> findByChatIdAndIsReadFalse(Long chatId);
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND :user NOT MEMBER OF m.readBy")
    List<Message> findUnreadByChatIdAndUser(@Param("chatId") Long chatId, @Param("user") User user);
}