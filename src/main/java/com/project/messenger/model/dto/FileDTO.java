package com.project.messenger.model.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileDTO {
    private Long id;
    private String fileName;
    private String uploadedAt;
    private Long fileSize;
    private String senderUsername;
    private String contentType;
    public boolean isImage() {
        return contentType != null && contentType.startsWith("image/");
    }
    public boolean isVideo() {
        return contentType != null && contentType.startsWith("video/");
    }
}