package com.project.messenger.service;

import com.project.messenger.model.*;
import com.project.messenger.model.dto.ChatDTO;
import com.project.messenger.model.dto.MessageDTO;
import com.project.messenger.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ChatService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private ChatMemberRepository chatMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserServiceInterface userService;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired private MessageService messageService;

    @Transactional(readOnly = true)
    public List<ChatDTO> getUserChats(String email) {
        User user = userService.findByEmail(email);
        List<Chat> chats = chatRepository.findByMembersUserId(user.getId());
        return mapToChatDTOs(chats, user.getId());
    }

    @Transactional(readOnly = true)
    public List<ChatDTO> getDirectChats(String email) {
        User user = userService.findByEmail(email);
        List<Chat> chats = chatRepository.findByMembersUserId(user.getId())
                .stream()
                .filter(chat -> chat.getType() == ChatType.PERSONAL)
                .collect(Collectors.toList());
        return mapToChatDTOs(chats, user.getId());
    }

    @Transactional(readOnly = true)
    public List<ChatDTO> getGroupChats(String email) {
        User user = userService.findByEmail(email);
        List<Chat> chats = chatRepository.findByMembersUserId(user.getId())
                .stream()
                .filter(chat -> chat.getType() == ChatType.GROUP)
                .collect(Collectors.toList());
        return mapToChatDTOs(chats, user.getId());
    }

    @Transactional
    public void updateChatName(Long chatId, String newName) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        chat.setName(newName);
        chatRepository.save(chat);
    }

    @Transactional(readOnly = true)
    public Chat getChat(Long chatId, String email) {
        User user = userService.findByEmail(email);
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, user.getId())) {
            throw new SecurityException("У вас нет доступа к этому чату");
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public Chat getChatWithMembers(Long chatId, String email) {
        User user = userService.findByEmail(email);
        Chat chat = chatRepository.findChatWithMembersById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, user.getId())) {
            throw new SecurityException("У вас нет доступа к этому чату");
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public Chat getChatWithMembersUsersData(Long chatId, String email) {
        User user = userService.findByEmail(email);
        Chat chat = chatRepository.findChatWithMembersWithUsersById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, user.getId())) {
            throw new SecurityException("У вас нет доступа к этому чату");
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public User getChatContact(Long chatId, String currentUserEmail) {
        Chat chat = getChat(chatId, currentUserEmail);
        return chat.getMembers().stream()
                .map(ChatMember::getUser)
                .filter(user -> !user.getEmail().equals(currentUserEmail))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Собеседник не найден в чате"));
    }

    @Transactional
    public Chat createDirectChat(Long userId1, Long userId2) {
        User user1 = userRepository.findById(userId1)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        User user2 = userRepository.findById(userId2)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Optional<Chat> existingChat = chatRepository.findPersonalChatBetweenUsers(userId1, userId2);
        if (existingChat.isPresent()) {
            return existingChat.get();
        }

        Chat chat = new Chat();
        chat.setType(ChatType.PERSONAL);
        chat.setCreatedBy(user1);
        chat = chatRepository.save(chat);

        addMember(chat, user1, false, NotificationLevel.ALL);
        addMember(chat, user2, false, NotificationLevel.ALL);
        return chat;
    }

    @Transactional
    public Chat createGroupChat(String name, Long creatorId, List<Long> memberIds) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Название чата не может быть пустым для группового чата");
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Создатель не найден"));

        Chat chat = new Chat();
        chat.setName(name);
        chat.setType(ChatType.GROUP);
        chat.setCreatedBy(creator);
        chat.setInviteLink(UUID.randomUUID().toString());
        chat = chatRepository.save(chat);

        addMember(chat, creator, true, NotificationLevel.ALL);
        for (Long memberId : memberIds) {
            User member = userRepository.findById(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));
            addMember(chat, member, false, NotificationLevel.ALL);
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public ChatMember getChatMember(Long chatId, String email) {
        return chatMemberRepository.findByChatIdAndUserEmail(chatId, email)
                .orElseThrow(() -> new IllegalStateException("Текущий пользователь не является участником чата"));
    }

    @Transactional
    public void addMemberToGroup(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        addMember(chat, user, false, NotificationLevel.ALL);
    }

    @Transactional
    public void removeMemberFromGroup(Long chatId, Long userId) {
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден в чате"));

        if (member.isAdmin()) {
            throw new IllegalStateException("Нельзя удалить администратора чата");
        }

        chatMemberRepository.delete(member);
    }

    @Transactional
    public void leaveGroup(Long chatId, Long userId) {
        removeMemberFromGroup(chatId, userId);
    }

    @Transactional
    public void deleteChat(Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        chatMemberRepository.deleteAllByChatId(chatId);
        chatRepository.delete(chat);
    }

    public List<MessageDTO> getMessages(Long chatId, Long currentUserId) {
        List<Message> messages = messageRepository.findByChatId(chatId);
        return messages.stream()
                .map(message -> mapToMessageDTO(message, currentUserId))
                .collect(Collectors.toList());
    }

    public List<MessageDTO> getMessagesAndIsCurrUserSent(Long chatId, String email) {
        User currentUser = userService.findByEmail(email);
        List<Message> messages = messageRepository.findByChatId(chatId);
        return messages.stream()
                .map(message -> new MessageDTO(
                        message.getId(),
                        message.getContent(),
                        message.getSender().getUsername(),
                        message.getTimestamp().toString(),
                        message.isRead(),
                        message.getSender().getId().equals(currentUser.getId()),
                        message.getFiles(),
                        message.getSender().getAvatarPath()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public Message sendMessage(Long chatId, String content, String senderEmail) {
        User sender = userService.findByEmail(senderEmail);
        return messageService.sendMessage(chatId, content, sender.getId());
    }

    public String getInviteLink(Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalStateException("Пригласительные ссылки доступны только для групповых чатов");
        }
        return "http://messenger.com/join?link=" + chat.getInviteLink();
    }

    public void joinGroupByLink(String link, Long userId) {
        Chat chat = chatRepository.findByInviteLink(link)
                .orElseThrow(() -> new IllegalArgumentException("Недействительная ссылка"));
        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalStateException("Ссылка недействительна");
        }
        addMemberToGroup(chat.getId(), userId);
    }

//    @Transactional(readOnly = true)
//    public List<ChatDTO> searchPersonalChatByName(String email, String query) {
//        String normalizedQuery = query.trim().toLowerCase();
//        Long userId = userService.getUserIdByEmail(email);
//        List<Chat> chats = chatRepository.findGroupChatsByName(normalizedQuery, userId);
//        return mapToChatDTOs(chats, userId);
//    }
//
//    @Transactional(readOnly = true)
//    public List<ChatDTO> searchGroupChatByName(String email, String query) {
//        String normalizedQuery = query.trim().toLowerCase();
//        Long userId = userService.getUserIdByEmail(email);
//        List<Chat> chats = chatRepository.findPersonalChatsByOtherUsername(normalizedQuery, userId);
//        return mapToChatDTOs(chats, userId);
//    }

    @Transactional(readOnly = true)
    public List<ChatDTO> searchChatsByType(String email, String query, @Nullable ChatType type) {
        String normalizedQuery = query.trim().toLowerCase();
        Long userId = userService.getUserIdByEmail(email);

        List<Chat> chats;

        if (type == null) {
            chats = Stream.concat(
                chatRepository.findPersonalChatsByOtherUsername(userId, normalizedQuery).stream(),
                chatRepository.findGroupChatsByName(userId, normalizedQuery).stream()
            ).toList();
        } else {
            chats = switch (type) {
                case PERSONAL -> chatRepository.findPersonalChatsByOtherUsername(userId, normalizedQuery);
                case GROUP -> chatRepository.findGroupChatsByName(userId, normalizedQuery);
            };
        }

        return mapToChatDTOs(chats, userId);
    }


    private void addMember(Chat chat, User user, boolean isAdmin, NotificationLevel notifications) {
        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
        NotificationLevel defaultLevel = (chat.getType() == ChatType.PERSONAL && settings != null)
                ? settings.getPersonalChatNotifications()
                : (settings != null ? settings.getGroupChatNotifications() : NotificationLevel.ALL);
        if (chatMemberRepository.existsByChatIdAndUserId(chat.getId(), user.getId())) {
            return;
        }
        ChatMember member = new ChatMember();
        member.setChat(chat);
        member.setUser(user);
        member.setAdmin(isAdmin);
        member.setNotifications(notifications != null ? notifications : defaultLevel);
        chatMemberRepository.save(member);
    }

    private List<ChatDTO> mapToChatDTOs(List<Chat> chats, Long userId) {
        return chats.stream().map(chat -> {
            ChatDTO dto = new ChatDTO();
            dto.setId(chat.getId());
            dto.setName(chat.getType() == ChatType.PERSONAL ? getPersonalChatName(chat, userId) : chat.getName());
            dto.setType(chat.getType());
            List<Message> messages = messageRepository.findByChatId(chat.getId());
            if (!messages.isEmpty()) {
                Message last = messages.get(messages.size() - 1);
                dto.setLastMessage(last.getContent());
                dto.setLastMessageDate(last.getTimestamp().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")));
                dto.setUnreadCount(messageRepository.findUnreadByChatIdAndUser(chat.getId(), userRepository.findById(userId).get()).size());
            }
            if (chat.getType() == ChatType.PERSONAL) {
                ChatMember companion = chat.getMembers().stream()
                        .filter(member -> !member.getUser().getId().equals(userId))
                        .findFirst()
                        .orElse(null);
                dto.setAvatar(companion != null ? companion.getUser().getAvatarPath() : null);
            } else {
                dto.setAvatar(null);
            }  //доделать для группового чата
            return dto;
        }).collect(Collectors.toList());
    }

    private String getPersonalChatName(Chat chat, Long userId) {
        return chat.getMembers().stream()
                .filter(m -> !m.getUser().getId().equals(userId))
                .findFirst()
                .map(m -> m.getUser().getUsername())
                .orElse("Вы");
    }

    private MessageDTO mapToMessageDTO(Message message, Long currentUserId) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setSenderUsername(message.getSender().getUsername());
        dto.setContent(message.getContent());
        dto.setDate(message.getTimestamp().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")));
        dto.setUserSend(message.getSender().getId().equals(currentUserId));
        dto.setRead(message.isRead());
        dto.setFiles(message.getFiles());
        dto.setUserAvatar(message.getSender().getAvatarPath());
        return dto;
    }

    public void saveChatMember(ChatMember chatMember) {
        chatMemberRepository.save(chatMember);
    }
}