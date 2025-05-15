package com.project.messenger.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "user_settings", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id", unique = true)
})
@Data
public class UserSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private NotificationLevel personalChatNotifications;

    @Enumerated(EnumType.STRING)
    private NotificationLevel groupChatNotifications;

    private String theme;
}