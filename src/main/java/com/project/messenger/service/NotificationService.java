package com.project.messenger.service;

import com.project.messenger.model.*;
import com.project.messenger.model.dto.NotificationDTO;
import com.project.messenger.repository.ChatMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

@Service
public class NotificationService {

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private WebSocketService webSocketService;

    public Chat toggleNotificationsAndGetChat(Long chatId, String email, boolean enabled) {
        ChatMember member = chatMemberRepository.findByChatIdAndUserEmail(chatId, email)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));
        member.setNotifications(enabled ? NotificationLevel.ALL : NotificationLevel.NONE);
        chatMemberRepository.save(member);
        return member.getChat();
    }

    public void sendNewMessageNotification(Chat chat, Message message, String senderEmail, boolean hasFiles) {
        NotificationDTO notification = new NotificationDTO();
        notification.setType("NEW_MESSAGE");
        notification.setChatId(chat.getId());
        notification.setChatName(chat.getType() == ChatType.PERSONAL
                ? chat.getMembers().stream()
                .filter(m -> !m.getUser().getEmail().equals(senderEmail))
                .findFirst()
                .map(m -> m.getUser().getUsername())
                .orElse("Чат")
                : chat.getName());
        notification.setChatType(chat.getType());
        notification.setMessage(hasFiles
                ? message.getSender().getUsername() + " отправил файлы"
                : (message.getContent() != null ? message.getContent() : "Новое сообщение"));
        notification.setSenderEmail(message.getSender().getEmail());
        notification.setSenderUsername(message.getSender().getUsername());
        notification.setTimestamp(LocalDateTime.now());

        chat.getMembers().forEach(member -> {
            String recipientEmail = member.getUser().getEmail();
            Set<Long> activeChatIds = webSocketService.getActiveChats(recipientEmail);
            NotificationLevel notificationLevel = member.getNotifications();
            if (!recipientEmail.equals(senderEmail) &&
                    notificationLevel == NotificationLevel.ALL &&
                    (activeChatIds == null || !activeChatIds.contains(chat.getId()))) {
                simpMessagingTemplate.convertAndSendToUser(recipientEmail, "/queue/notifications", notification);
            }
        });
    }
}