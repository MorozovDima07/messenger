package com.project.messenger.model.dto;

import lombok.Data;

@Data
public class MessageDTO {
    private Long id;
    private Long senderId;
    private String senderUsername;
    private String content;
    private String date;
    private boolean userSend;
    private boolean read;
}