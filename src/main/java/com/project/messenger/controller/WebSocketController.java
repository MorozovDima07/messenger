package com.project.messenger.controller;

import com.project.messenger.exception.MessageNotFoundException;
import com.project.messenger.model.*;
import com.project.messenger.model.dto.NotificationDTO;
import com.project.messenger.model.dto.OnlineStatusDTO;
import com.project.messenger.model.dto.WebSocketMessageDTO;
import com.project.messenger.service.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.context.event.EventListener;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class WebSocketController {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserServiceInterface userService;
    private final ChatService chatService;
    private final MessageService messageService;
    private final FileService fileService;
    private final ConcurrentHashMap<String, Set<Long>> activeChats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> activeUsers = new ConcurrentHashMap<>();
    private static final long INACTIVITY_THRESHOLD = 30 * 60 * 1000;
    public WebSocketController(SimpMessagingTemplate simpMessagingTemplate, UserServiceInterface userService,
                               ChatService chatService, MessageService messageService, FileService fileService) {
        this.simpMessagingTemplate = simpMessagingTemplate;
        this.userService = userService;
        this.chatService = chatService;
        this.messageService = messageService;
        this.fileService = fileService;
    }

    @GetMapping("/online-users")
    public List<Map<String, Object>> getOnlineUsers() {
        System.out.println("Запрос /api/online-users, activeUsers: " + activeUsers);
        return activeUsers.keySet().stream()
                .map(email -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("email", email);
                    userMap.put("online", true);
                    return userMap;
                })
                .collect(Collectors.toList());
    }

    private void broadcastOnlineStatus(String email, boolean isOnline) {
        OnlineStatusDTO statusDTO = new OnlineStatusDTO();
        statusDTO.setEmail(email);
        statusDTO.setOnline(isOnline);
        simpMessagingTemplate.convertAndSend("/topic/online-status", statusDTO);
    }

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null) {
            String email = principal.getName();
            activeUsers.put(email, System.currentTimeMillis());
            broadcastOnlineStatus(email, true);
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        String email = event.getUser() != null ? event.getUser().getName() : null;
        if (email != null) {
            activeUsers.remove(email);
            activeChats.remove(email);
            broadcastOnlineStatus(email, false);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupInactiveUsers() {
        long currentTime = System.currentTimeMillis();
        activeUsers.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > INACTIVITY_THRESHOLD) {
                String email = entry.getKey();
                activeChats.remove(email);
                broadcastOnlineStatus(email, false);
                return true;
            }
            return false;
        });
    }

    @MessageMapping("/chat/{chatId}/join")
    public void joinChat(@DestinationVariable("chatId") Long chatId, Principal principal) {
        String email = principal.getName();
        activeChats.computeIfAbsent(email, k -> new HashSet<>()).add(chatId);
        activeUsers.put(email, System.currentTimeMillis());
    }

    @MessageMapping("/chat/{chatId}/leave")
    public void leaveChat(@DestinationVariable("chatId") Long chatId, Principal principal) {
        String email = principal.getName();
        Set<Long> userChats = activeChats.get(email);
        if (userChats != null) {
            userChats.remove(chatId);
            if (userChats.isEmpty()) {
                activeChats.remove(email);
            }
        }
        activeUsers.put(email, System.currentTimeMillis());
    }

    @MessageMapping("/chat/{chatId}")
    @Transactional
    public void sendMessage(@DestinationVariable("chatId") Long chatId,
                            WebSocketMessageDTO messageDTO,
                            Principal principal) {
        String email = principal.getName();
        User sender = userService.findByEmail(email);
        Chat chat = chatService.getChatWithMembers(chatId, email);

        boolean hasContent = messageDTO.getContent() != null && !messageDTO.getContent().trim().isEmpty();
        boolean hasFiles = messageDTO.getFiles() != null && !messageDTO.getFiles().isEmpty();
        if (!hasContent && !hasFiles) {
            return;
        }

        Message message;
        if (messageDTO.getMessageId() != null) {
            message = messageService.findById(messageDTO.getMessageId())
                    .orElseThrow(() -> new MessageNotFoundException("Сообщение с ID " + messageDTO.getMessageId() + " не найдено"));
        } else {
            message = new Message();
            message.setChat(chat);
            message.setSender(sender);
            message.setTimestamp(LocalDateTime.now());
            message.setRead(false);
        }

        if (hasContent) {
            message.setContent(messageDTO.getContent().trim());
        }

        if (chat.getType() == ChatType.GROUP){
            message.getReadBy().add(sender);
        }

        message = messageService.save(message);

        if (hasFiles) {
            List<File> files = messageDTO.getFiles().stream()
                    .filter(fileAttachment -> fileAttachment.getId() != null)
                    .map(fileAttachment -> fileService.getFile(fileAttachment.getId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            for (File file : files) {
                if (file.getMessage() == null) {
                    file.setMessage(message);
                    fileService.save(file);
                }
            }

            message.setFiles(files);
            messageService.save(message);
        }

        WebSocketMessageDTO response = new WebSocketMessageDTO();
        response.setMessageId(message.getId());
        response.setContent(message.getContent());
        response.setChatId(chatId);
        response.setSenderEmail(sender.getEmail());
        response.setSenderUsername(sender.getUsername());
        response.setSenderAvatarPath(sender.getAvatarPath());
        response.setRead(message.isRead());
        response.setSentAt(message.getTimestamp());
        response.setTempId(messageDTO.getTempId());
        response.setFiles(message.getFiles() != null && !message.getFiles().isEmpty()
                ? message.getFiles().stream().map(file -> {
            WebSocketMessageDTO.FileAttachment attachment = new WebSocketMessageDTO.FileAttachment();
            attachment.setId(file.getId());
            attachment.setFileName(file.getFileName());
            attachment.setContentType(file.getContentType());
            return attachment;
        }).collect(Collectors.toList())
                : Collections.emptyList());

        if (chat.getType() == ChatType.PERSONAL) {
            chat.getMembers().forEach(member -> {
                String recipientEmail = member.getUser().getEmail();
                simpMessagingTemplate.convertAndSendToUser(
                        recipientEmail,
                        "/queue/private",
                        response
                );
            });
        } else {
            simpMessagingTemplate.convertAndSend("/topic/chat/" + chatId, response);
        }

        NotificationDTO notification = new NotificationDTO();
        notification.setType("NEW_MESSAGE");
        notification.setChatId(chatId);
        notification.setChatName(chat.getType() == ChatType.PERSONAL
                ? chat.getMembers().stream()
                .filter(m -> !m.getUser().getEmail().equals(email))
                .findFirst()
                .map(m -> m.getUser().getUsername())
                .orElse("Чат")
                : chat.getName());
        notification.setChatType(chat.getType());
        notification.setMessage(hasFiles
                ? sender.getUsername() + " отправил файлы"
                : (message.getContent() != null ? message.getContent() : "Новое сообщение"));
        notification.setSenderEmail(sender.getEmail());
        notification.setSenderUsername(sender.getUsername());
        notification.setTimestamp(LocalDateTime.now());

        chat.getMembers().forEach(member -> {
            String recipientEmail = member.getUser().getEmail();
            Set<Long> activeChatIds = activeChats.get(recipientEmail);
            NotificationLevel notificationLevel = member.getNotifications();
            if (!recipientEmail.equals(email) &&
                    notificationLevel == NotificationLevel.ALL &&
                    (activeChatIds == null || !activeChatIds.contains(chatId))) {
                simpMessagingTemplate.convertAndSendToUser(recipientEmail, "/queue/notifications", notification);
            } else {
                System.out.println("Уведомление не отправлено для " + recipientEmail + ": " +
                        (recipientEmail.equals(email) ? "это отправитель" :
                                (notificationLevel != NotificationLevel.ALL ? "уведомления отключены" :
                                        (activeChatIds != null && activeChatIds.contains(chatId) ? "пользователь в чате" : "другая причина"))));
            }
        });

        chat.setLastMessageTimestamp(message.getTimestamp());
        chatService.saveChat(chat);
    }

    @MessageMapping("/heartbeat")
    public void heartbeat(Principal principal) {
        String email = principal.getName();
        activeUsers.put(email, System.currentTimeMillis());
    }
}