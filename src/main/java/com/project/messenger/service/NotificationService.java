package com.project.messenger.service;

import com.project.messenger.model.*;
import com.project.messenger.repository.ChatMemberRepository;
import com.project.messenger.repository.ChatRepository;
import com.project.messenger.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Transactional
    public void toggleNotifications(Long chatId, Long userId, boolean enabled) {
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден в чате"));
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Настройки пользователя не найдены"));
        NotificationLevel level = enabled ?
                (member.getChat().getType() == ChatType.PERSONAL ? settings.getPersonalChatNotifications() : settings.getGroupChatNotifications())
                : NotificationLevel.NONE;
        member.setNotifications(level);
        chatMemberRepository.save(member);
    }

    public void notifyNewMessage(Long chatId, Message message) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
        for (ChatMember member : members) {
            if (!member.getUser().getId().equals(message.getSender().getId())) {
                switch (member.getNotifications()) {
                    case ALL:
                        System.out.println("Уведомление для " + member.getUser().getUsername() + ": " + message.getContent());
                        break;
                    case MENTIONS:
                        if (message.getContent().contains("@" + member.getUser().getUsername())) {
                            System.out.println("Уведомление (упоминание) для " + member.getUser().getUsername() + ": " + message.getContent());
                        }
                        break;
                    case NONE:
                        break;
                }
            }
        }
    }
}