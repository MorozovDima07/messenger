package com.project.messenger.model.dto;

import com.project.messenger.model.ChatType;
import lombok.Data;

@Data
public class ChatDTO {
    private Long id;
    private String name;
    private ChatType type;
    private String lastMessage;
    private String lastMessageDate;
    private String memberCount;
    private String avatar;
    private int unreadCount;
    private boolean active;
}