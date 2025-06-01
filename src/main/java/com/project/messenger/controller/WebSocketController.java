package com.project.messenger.controller;

import com.project.messenger.model.dto.WebSocketMessageDTO;
import com.project.messenger.service.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.context.event.EventListener;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class WebSocketController {
    private final MessageService messageService;
    private final WebSocketService sessionService;

    public WebSocketController(MessageService messageService, WebSocketService sessionService) {
        this.messageService = messageService;
        this.sessionService = sessionService;
    }

    @GetMapping("/online-users")
    public List<Map<String, Object>> getOnlineUsers() {
        return sessionService.getOnlineUsers().stream()
                .map(email -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("email", email);
                    userMap.put("online", true);
                    return userMap;
                })
                .collect(Collectors.toList());
    }

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null) {
            sessionService.handleConnect(principal.getName());
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        String email = event.getUser() != null ? event.getUser().getName() : null;
        if (email != null) {
            sessionService.handleDisconnect(email);
        }
    }

    @MessageMapping("/chat/{chatId}/join")
    public void joinChat(@DestinationVariable("chatId") Long chatId, Principal principal) {
        sessionService.joinChat(principal.getName(), chatId);
    }

    @MessageMapping("/chat/{chatId}/leave")
    public void leaveChat(@DestinationVariable("chatId") Long chatId, Principal principal) {
        sessionService.leaveChat(principal.getName(), chatId);
    }

    @MessageMapping("/chat/{chatId}")
    public void sendMessage(@DestinationVariable("chatId") Long chatId,
                            WebSocketMessageDTO messageDTO,
                            Principal principal) {
        messageService.sendMessage(chatId, messageDTO, principal.getName());
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(Principal principal) {
        sessionService.updateHeartbeat(principal.getName());
    }
}