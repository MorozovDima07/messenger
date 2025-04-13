package com.project.messenger.controller;

import com.project.messenger.model.*;
import com.project.messenger.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;

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

    @PostMapping("/chats/{id}/send")
    public String sendMessage(
            @PathVariable("id") Long id,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());

        boolean hasContent = content != null && !content.trim().isEmpty();
        boolean hasFiles = files != null && files.length > 0 && Arrays.stream(files).anyMatch(file -> !file.isEmpty());
        if (!hasContent && !hasFiles) {
            return "redirect:/chats/" + id;
        }

        Message message = new Message();
        message.setChat(chat);
        message.setSender(currentUser);
        message.setContent(hasContent ? content.trim() : null);
        message.setTimestamp(LocalDateTime.now());
        messageService.save(message);

        if (hasFiles) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    fileService.uploadFile(file, message);
                }
            }
            messageService.save(message);
        }

        if (chat.getType() == ChatType.PERSONAL) {
            return "redirect:/direct?id=" + id;
        } else {
            return "redirect:/group?id=" + id;
        }
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id) {
        File file = fileService.getFile(id);
        Path filePath = Paths.get(file.getFilePath());
        Resource resource;
        try {
            resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("Файл не найден или недоступен");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Ошибка при загрузке файла", e);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }
}