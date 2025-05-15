package com.project.messenger.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatMemberDTO {
    private Long userId;
    private String username;
    private String email;
    private boolean isAdmin;
    private LocalDateTime lastActive;
    private boolean isOnline;
    private String avatarPath;
}