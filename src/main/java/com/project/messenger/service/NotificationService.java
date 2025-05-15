package com.project.messenger.service;

import com.project.messenger.model.*;
import com.project.messenger.repository.ChatMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    public void toggleNotifications(Long chatId, Long userId, boolean enabled) {
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));
        member.setNotifications(enabled ? NotificationLevel.ALL : NotificationLevel.NONE);
        chatMemberRepository.save(member);
    }
}