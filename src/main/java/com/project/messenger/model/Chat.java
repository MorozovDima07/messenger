package com.project.messenger.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "chats", indexes = {
        @Index(name = "idx_invite_link", columnList = "invite_link", unique = true),
        @Index(name = "idx_created_by", columnList = "created_by")
})
@Data
public class Chat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Тип чата обязателен")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatType type;

    @Column(name = "invite_link")
    private String inviteLink;

    private String name;

    private String avatarPath;

    @NotNull(message = "Создатель чата обязателен")
    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMember> members;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> messages;

    @Column(name = "last_message_timestamp")
    private LocalDateTime lastMessageTimestamp;
}

