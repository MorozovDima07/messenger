package com.project.messenger.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Table(name = "chat_members", indexes = {
        @Index(name = "chat_members_chat_id_idx", columnList = "chat_id"),
        @Index(name = "idx_user_id", columnList = "user_id")
})
@Data
public class ChatMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Чат обязателен")
    @ManyToOne
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @NotNull(message = "Пользователь обязателен")
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Уровень уведомлений обязателен")
    @Enumerated(EnumType.STRING)
    private NotificationLevel notifications;

    @Column(name = "is_admin")
    private boolean isAdmin;
}