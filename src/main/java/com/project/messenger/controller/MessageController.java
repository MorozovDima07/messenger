package com.project.messenger.controller;

import com.project.messenger.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    public String sendMessage(@PathVariable("id") Long id, @RequestParam("content") String content, Authentication auth) {
        messageService.sendMessage(id, content, userService.findByEmail(auth.getName()).getId());
        return "redirect:/direct?id=" + id;
    }

    @PostMapping("/chats/{id}/file")
    public String uploadFile(@PathVariable("id") Long id, @RequestParam("file") MultipartFile file, Authentication auth) {
        messageService.sendFile(id, file, userService.findByEmail(auth.getName()).getId());
        return "redirect:/direct?id=" + id;
    }

    @GetMapping("/chats/{id}/files")
    public String getChatFiles(@PathVariable("id") Long id, Model model) {
        model.addAttribute("files", fileService.getChatFiles(id));
        return "chat-files";
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id) {
        com.project.messenger.model.File file = fileService.getFile(id);
        Path path = Paths.get(file.getFilePath());
        Resource resource;
        try {
            resource = new UrlResource(path.toUri());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Ошибка при загрузке файла", e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .body(resource);
    }
}