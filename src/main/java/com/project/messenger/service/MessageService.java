package com.project.messenger.service;

import com.project.messenger.model.*;
import com.project.messenger.repository.MessageRepository;
import com.project.messenger.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MessageService {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileService fileService;

    @Autowired
    private UserServiceInterface userService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public Message sendMessage(Long chatId, String content, Long senderId) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }
        Chat chat = chatService.getChat(chatId, userRepository.findById(senderId).get().getEmail());
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Отправитель не найден"));

        Message message = new Message();
        message.setChat(chat);
        message.setSender(sender);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);
        message = messageRepository.save(message);
        message.getReadBy().add(sender);
        return message;
    }

    @Transactional
    public List<Message> markMessagesAsRead(Long chatId, String userEmail) {
        Chat chat = chatService.getChatWithMembers(chatId, userEmail);
        User currentUser = userService.findByEmail(userEmail);
        List<Message> updatedMessages = new ArrayList<>();

        List<Message> unreadMessages = messageRepository.findByChatIdAndIsReadFalse(chatId)
                .stream()
                .filter(message -> !message.getSender().getEmail().equals(userEmail))
                .collect(Collectors.toList());

        if (chat.getType() == ChatType.PERSONAL) {
            for (Message message : unreadMessages) {
                System.out.println("Прочитанность сообщений для личных чатов");
                message.setRead(true);
                messageRepository.save(message);
                updatedMessages.add(message);
            }
        } else if (chat.getType() == ChatType.GROUP) {
                    for (Message message : unreadMessages) {
                        if (message.getReadBy() == null) {
                            message.setReadBy(new HashSet<>());
                        }
                        System.out.println("Прочитанность сообщений для групповых чатов");
                        message.getReadBy().add(currentUser);
                        message.setRead(true);
                        messageRepository.save(message);
                        updatedMessages.add(message);
                    }
        }

        return updatedMessages;
    }

    @Transactional
    public Message save(Message message) {
        return messageRepository.save(message);
    }

    @Transactional
    public Optional<Message> findById(Long id) {
        return messageRepository.findById(id);
    }
}