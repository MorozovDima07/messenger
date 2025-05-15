package com.project.messenger.model.dto;

import com.project.messenger.model.ChatType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDTO {
    private String type;
    private Long chatId;
    private String chatName;
    private ChatType chatType;
    private String message;
    private String senderEmail;
    private String senderUsername;
    private LocalDateTime timestamp;
}