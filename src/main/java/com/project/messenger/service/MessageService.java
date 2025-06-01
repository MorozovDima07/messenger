package com.project.messenger.service;

import com.project.messenger.exception.MessageNotFoundException;
import com.project.messenger.model.*;
import com.project.messenger.model.dto.NotificationDTO;
import com.project.messenger.model.dto.WebSocketMessageDTO;
import com.project.messenger.repository.MessageRepository;
import com.project.messenger.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private WebSocketService webSocketService;

    @Transactional
    public void sendMessage(Long chatId, WebSocketMessageDTO messageDTO, String userEmail) {
        boolean hasContent = messageDTO.getContent() != null && !messageDTO.getContent().trim().isEmpty();
        boolean hasFiles = messageDTO.getFiles() != null && !messageDTO.getFiles().isEmpty();
        if (!hasContent && !hasFiles) {
            return;
        }

        User sender = userService.findByEmail(userEmail);
        Chat chat = chatService.getChatWithMembers(chatId, userEmail);

        Message message;
        if (messageDTO.getMessageId() != null) {
            message = findById(messageDTO.getMessageId())
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

        if (chat.getType() == ChatType.GROUP) {
            message.getReadBy().add(sender);
        }

        message = messageRepository.save(message);

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
            message = messageRepository.save(message);
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
        response.setFiles(
                message.getFiles() != null && !message.getFiles().isEmpty()
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

        notificationService.sendNewMessageNotification(chat, message, userEmail, hasFiles);

        chat.setLastMessageTimestamp(message.getTimestamp());
        chatService.saveChat(chat);
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
    public void updateReadMessageStatus(Long chatId, String email, List<Message> updatedMessages){
        Chat chat = chatService.getChatWithMembers(chatId, email);
        updatedMessages.forEach(message -> {
            WebSocketMessageDTO response = createWebSocketMessageDTO(message, chatId);
            if (chat.getType() == ChatType.PERSONAL) {
                chat.getMembers().forEach(member -> {
                    String recipientEmail = member.getUser().getEmail();
                    simpMessagingTemplate.convertAndSendToUser(recipientEmail, "/queue/private", response);
                });
            } else {
                simpMessagingTemplate.convertAndSend("/topic/chat/" + chatId, response);
            }
        });
    }

    private WebSocketMessageDTO createWebSocketMessageDTO(Message message, Long chatId) {
        WebSocketMessageDTO response = new WebSocketMessageDTO();
        response.setMessageId(message.getId());
        response.setContent(message.getContent());
        response.setChatId(chatId);
        response.setSenderEmail(message.getSender().getEmail());
        response.setSenderUsername(message.getSender().getUsername());
        response.setSenderAvatarPath(message.getSender().getAvatarPath());
        response.setRead(message.isRead());
        response.setSentAt(message.getTimestamp());
        response.setFiles(message.getFiles() != null && !message.getFiles().isEmpty()
                ? message.getFiles().stream().map(file -> {
            WebSocketMessageDTO.FileAttachment attachment = new WebSocketMessageDTO.FileAttachment();
            attachment.setId(file.getId());
            attachment.setFileName(file.getFileName());
            attachment.setContentType(file.getContentType());
            return attachment;
        }).collect(Collectors.toList())
                : Collections.emptyList());
        return response;
    }

    @Transactional
    public Optional<Message> findById(Long id) {
        return messageRepository.findById(id);
    }
}