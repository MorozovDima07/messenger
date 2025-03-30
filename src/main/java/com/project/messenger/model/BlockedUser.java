package com.project.messenger.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Entity
@Table(name = "blocked_users")
@Data
public class BlockedUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Пользователь не может быть null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull(message = "Заблокированный пользователь не может быть null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;
}