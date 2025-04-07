package com.project.messenger.service;

import com.project.messenger.model.Chat;
import com.project.messenger.model.ChatMember;
import com.project.messenger.model.Message;
import com.project.messenger.model.User;
import com.project.messenger.repository.MessageRepository;
import com.project.messenger.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

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
        notificationService.notifyNewMessage(chatId, message); // Нужна интеграция с уведомлениями
        return message;
    }

    @Transactional
    public Message sendFile(Long chatId, MultipartFile file, Long senderId) {
        Chat chat = chatService.getChat(chatId, userRepository.findById(senderId).get().getEmail());
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Отправитель не найден"));

        Message message = new Message();
        message.setChat(chat);
        message.setSender(sender);
        message.setContent("Файл загружен: " + file.getOriginalFilename());
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);
        message = messageRepository.save(message);

        fileService.uploadFile(file, message);
        notificationService.notifyNewMessage(chatId, message); // Нужна интеграция с уведомлениями
        return message;
    }

    @Transactional
    public void markMessageAsRead(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Сообщение не найдено"));
        message.setRead(true);
        messageRepository.save(message);
    }

    public List<Message> getUnreadMessages(Long chatId, Long userId) {
        return messageRepository.findByChatIdAndIsReadFalse(chatId);
    }

    @Transactional
    public void markMessagesAsRead(Long chatId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        List<Message> unreadMessages = messageRepository.findUnreadByChatIdAndUser(chatId, user);
        for (Message message : unreadMessages) {
            message.getReadBy().add(user);
        }
        messageRepository.saveAll(unreadMessages);
    }

    public void save(Message message) {
        messageRepository.save(message);
    }
}