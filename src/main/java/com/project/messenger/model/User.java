package com.project.messenger.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email(message = "Email должен быть корректным")
    @NotBlank(message = "Email не может быть пустым")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Пароль не может быть пустым")
    @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
    @Column(nullable = false)
    private String password;

    @NotBlank(message = "Имя пользователя не может быть пустым")
    @Size(min = 2, max = 50, message = "Имя пользователя должно быть от 2 до 50 символов")
    @Column(nullable = false)
    private String username;

    @Column
    private String avatarPath;

    private LocalDateTime lastActive;

    private boolean emailVisible;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BlockedUser> blockedUsers;

    @OneToMany(mappedBy = "createdBy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chat> createdChats;

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Message> sentMessages;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMember> chatMemberships;
}