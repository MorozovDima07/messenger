package com.project.messenger.controller;

import com.project.messenger.model.*;
import com.project.messenger.model.dto.FileDownloadDTO;
import com.project.messenger.model.dto.MessageDTO;
import com.project.messenger.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

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

    @GetMapping("/chat/{chatId}/messages/load")
    @ResponseBody
    public Page<MessageDTO> loadMoreMessages(
            @PathVariable("chatId") Long chatId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        return chatService.getMessages(chatId, currentUser.getId(), page, size);
    }

    @MessageMapping("/chat/{chatId}/read")
    public void markMessagesAsRead(@DestinationVariable("chatId") Long chatId, Principal principal) {
        String email = principal.getName();
        List<Message> updatedMessages = messageService.markMessagesAsRead(chatId, email);
        messageService.updateReadMessageStatus(chatId, email, updatedMessages);
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id, Authentication auth) {
        FileDownloadDTO dto = fileService.downloadFile(id, auth.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(dto.getContentType()))
                .headers(dto.getHeaders())
                .body(dto.getResource());
    }

    @PostMapping("/chats/{chatId}/upload")
    public ResponseEntity<List<Long>> uploadFiles(@PathVariable("chatId") Long chatId,
                                                  @RequestParam("files") MultipartFile[] files,
                                                  Principal principal) {
        return ResponseEntity.ok(fileService.uploadFiles(files));
    }
}