package com.project.messenger.controller;

import com.project.messenger.model.*;
import com.project.messenger.model.dto.WebSocketMessageDTO;
import com.project.messenger.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class MessageController {

    @Autowired
    private MessageService messageService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserServiceInterface userService;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;


    @MessageMapping("/chat/{chatId}/read")
    public void markMessagesAsRead(@DestinationVariable("chatId") Long chatId, Principal principal) {
        String email = principal.getName();
        Chat chat = chatService.getChatWithMembers(chatId, email);
        User currentUser = chat.getMembers().stream()
                .map(ChatMember::getUser)
                .filter(user -> user.getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден в чате: " + email));

        List<Message> updatedMessages = messageService.markMessagesAsRead(chatId, email);

        updatedMessages.forEach(message -> {
            WebSocketMessageDTO response = createWebSocketMessageDTO(message, chatId);
            if (chat.getType() == ChatType.PERSONAL) {
                chat.getMembers().forEach(member -> {
                    String recipientEmail = member.getUser().getEmail();
                    simpMessagingTemplate.convertAndSendToUser(recipientEmail, "/queue/private", response);
                });
            } else {
                simpMessagingTemplate.convertAndSend("/topic/chat/" + chatId, response);
            }
        });
    }

    private WebSocketMessageDTO createWebSocketMessageDTO(Message message, Long chatId) {
        WebSocketMessageDTO response = new WebSocketMessageDTO();
        response.setMessageId(message.getId());
        response.setContent(message.getContent());
        response.setChatId(chatId);
        response.setSenderEmail(message.getSender().getEmail());
        response.setSenderUsername(message.getSender().getUsername());
        response.setSenderAvatarPath(message.getSender().getAvatarPath());
        response.setRead(message.isRead());
        response.setSentAt(message.getTimestamp());
        response.setFiles(message.getFiles() != null && !message.getFiles().isEmpty()
                ? message.getFiles().stream().map(file -> {
            WebSocketMessageDTO.FileAttachment attachment = new WebSocketMessageDTO.FileAttachment();
            attachment.setId(file.getId());
            attachment.setFileName(file.getFileName());
            attachment.setContentType(file.getContentType());
            return attachment;
        }).collect(Collectors.toList())
                : Collections.emptyList());
        return response;
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        File file = fileService.getFile(id);
        Message message = file.getMessage();
        if (!chatService.isUserInChat(message.getChat().getId(), currentUser.getId())) {
            throw new SecurityException("Нет доступа к файлу");
        }

        Path filePath = Paths.get(file.getFilePath());
        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Файл не найден или недоступен");
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Ошибка при загрузке файла", e);
        }

        String contentType;
        try {
            contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
        } catch (IOException e) {
            contentType = "application/octet-stream";
        }

        HttpHeaders headers = new HttpHeaders();
        if (contentType.startsWith("image/") || contentType.startsWith("video/")) {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFileName() + "\"");
        } else {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .headers(headers)
                .body(resource);
    }

    @PostMapping("/chats/{chatId}/upload")
    public ResponseEntity<List<Long>> uploadFiles(@PathVariable("chatId") Long chatId,
                                                  @RequestParam("files") MultipartFile[] files,
                                                  Principal principal) {
        try {
            List<Long> fileIds = Arrays.stream(files)
                    .filter(file -> !file.isEmpty())
                    .map(file -> {
                        try {
                            File savedFile = fileService.uploadFile(file, null);
                            return savedFile.getId();
                        } catch (Exception e) {
                            System.err.println("Ошибка при загрузке файла " + file.getOriginalFilename() + ": " + e.getMessage());
                            e.printStackTrace();
                            throw new RuntimeException("Ошибка загрузки файла: " + file.getOriginalFilename(), e);
                        }
                    })
                    .collect(Collectors.toList());

            System.out.println("Загружены файлы с ID: " + fileIds);
            return ResponseEntity.ok(fileIds);
        } catch (Exception e) {
            System.err.println("Общая ошибка загрузки файлов: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(new ArrayList<>());
        }
    }
}