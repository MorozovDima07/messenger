package com.project.messenger.model.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class WebSocketMessageDTO {
    private Long messageId;
    private String content;
    private Long chatId;
    private String senderEmail;
    private String senderUsername;
    private String senderAvatarPath;
    private boolean isRead;
    private LocalDateTime sentAt;
    private List<FileAttachment> files;
    private String tempId;

    @Data
    public static class FileAttachment {
        private Long id;
        private String fileName;
        private String contentType;
        public boolean isImage() {
            return contentType != null && contentType.startsWith("image/");
        }
        public boolean isVideo() {
            return contentType != null && contentType.startsWith("video/");
        }
    }
}