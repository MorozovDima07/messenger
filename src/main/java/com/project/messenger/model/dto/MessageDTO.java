package com.project.messenger.model.dto;

import com.project.messenger.model.File;
import lombok.Data;

import java.util.List;

@Data
public class MessageDTO {
    private Long id;
    private String content;
    private String senderUsername;
    private String date;
    private boolean read;
    private boolean userSend;
    private String userAvatar;
    private List<File> files;

    public MessageDTO(){}

    public MessageDTO(Long id, String content, String senderUsername, String date, boolean read, boolean userSend, List<File> files, String userAvatar) {
        this.id = id;
        this.content = content;
        this.senderUsername = senderUsername;
        this.date = date;
        this.read = read;
        this.userSend = userSend;
        this.files = files;
        this.userAvatar = userAvatar;
    }
}