package com.project.messenger.service;

import com.project.messenger.model.*;
import com.project.messenger.model.dto.ChatDTO;
import com.project.messenger.model.dto.ChatMemberDTO;
import com.project.messenger.model.dto.MessageDTO;
import com.project.messenger.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private ChatRepository chatRepository;

    @Autowired
    private FileRepository fileRepository;

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

    @Autowired @Value("${app.base-url:}") private String baseUrl;

    @Transactional(readOnly = true)
    public Page<ChatDTO> getChatsForUser(String email, ChatType chatType, Pageable pageable) {
        User user = userService.findByEmail(email);
        Page<Chat> chats;
        if (chatType == null) {
            chats = chatRepository.findByMembersUserId(user.getId(), pageable);
        } else {
            chats = chatRepository.findByMembersUserIdAndType(user.getId(), chatType, pageable);
        }
        return chats.map(chat -> mapToChatDTO(chat, user.getId(), email));
    }

    @Transactional(readOnly = true)
    public Page<ChatDTO> getChatsPage(String email, ChatType chatType, Pageable pageable) {
        return getChatsForUser(email, chatType, pageable);
    }

    @Transactional(readOnly = true)
    public Chat findByInviteLink(String link) {
        return chatRepository.findByInviteLink(link)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
    }

    @Transactional
    public void updateChatAvatar(Long chatId, MultipartFile avatarFile) throws IOException {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));

        if (avatarFile != null && !avatarFile.isEmpty()) {
            String uploadDirGroup = "uploads/group-avatars/";
            java.io.File dir = new java.io.File(uploadDirGroup);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String contentType = avatarFile.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IOException("Файл должен быть изображением (JPEG, PNG)");
            }

            if (chat.getAvatarPath() != null) {
                java.io.File oldAvatar = new java.io.File(chat.getAvatarPath().substring(1)); // Убираем начальный слэш
                if (oldAvatar.exists()) {
                    oldAvatar.delete();
                }
            }

            String fileName = "group_" + chatId + "_" + UUID.randomUUID().toString() + "." + getFileExtension(avatarFile.getOriginalFilename());
            java.io.File dest = new java.io.File(dir.getAbsolutePath() + java.io.File.separator + fileName);

            BufferedImage originalImage = ImageIO.read(avatarFile.getInputStream());
            int targetSize = 400;
            BufferedImage resizedImage;

            if (originalImage.getWidth() < originalImage.getHeight()) {
                resizedImage = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_WIDTH, targetSize);
            } else {
                resizedImage = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_HEIGHT, targetSize);
            }

            ImageIO.write(resizedImage, getFileExtension(avatarFile.getOriginalFilename()), dest);

            chat.setAvatarPath("/" + uploadDirGroup + fileName);
            chatRepository.save(chat);
        }
    }

    @Transactional
    public void updateChatName(Long chatId, String name) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        chat.setName(name);
        chatRepository.save(chat);
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "jpg";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    @Transactional(readOnly = true)
    public Chat getChat(Long chatId, String email) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (!chatMemberRepository.existsByChatIdAndUserEmail(chatId, email)) {
            throw new SecurityException("У вас нет доступа к этому чату");
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public Chat getGroupChat(Long chatId, String email) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (!chatMemberRepository.existsByChatIdAndUserEmail(chatId, email)) {
            throw new IllegalArgumentException("Вы не участник чата");
        }
        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalArgumentException("Доступно только для групповых чатов");
        }
        return chat;
    }

    public Page<ChatMemberDTO> getChatMembers(Long chatId, String email, Pageable pageable) {
        return chatMemberRepository.findByChatId(chatId, pageable).map(member -> {
            ChatMemberDTO dto = new ChatMemberDTO();
            dto.setUserId(member.getUser().getId());
            dto.setUsername(member.getUser().getUsername());
            dto.setEmail(member.getUser().getEmail());
            dto.setAdmin(member.isAdmin());
            dto.setLastActive(member.getUser().getLastActive());
            dto.setOnline(isUserOnline(member.getUser().getLastActive()));
            dto.setAvatarPath(member.getUser().getAvatarPath());
            return dto;
        });
    }

    private boolean isUserOnline(LocalDateTime lastActive) {
        return lastActive != null && lastActive.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    @Transactional(readOnly = true)
    public ChatMember getChatMember(Long chatId, String email) {
        return chatMemberRepository.findByChatIdAndUserEmail(chatId, email)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден"));
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
        chat.setLastMessageTimestamp(LocalDateTime.now());
        chat = chatRepository.save(chat);

        addMember(chat, user1, false, NotificationLevel.ALL);
        addMember(chat, user2, false, NotificationLevel.ALL);
        return chat;
    }

    @Transactional
    public Chat createGroupChat(String name, Long creatorId, List<Long> memberIds, NotificationLevel notificationLevel) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Название чата не может быть пустым для группового чата");
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Создатель с ID " + creatorId + " не найден"));

        Set<Long> uniqueMemberIds = new HashSet<>(memberIds);

        Chat chat = new Chat();
        chat.setName(name);
        chat.setType(ChatType.GROUP);
        chat.setCreatedBy(creator);
        chat.setInviteLink(generateUniqueInviteLink());
        chat.setLastMessageTimestamp(LocalDateTime.now());
        chat = chatRepository.save(chat);

        List<ChatMember> members = new ArrayList<>();
        members.add(createChatMember(chat, creator, true, notificationLevel));

        for (Long memberId : uniqueMemberIds) {
            if (memberId.equals(creatorId)) {
                continue;
            }
            User member = userRepository.findById(memberId)
                    .orElseThrow(() ->
                        new IllegalArgumentException("Участник с ID " + memberId + " не найден")
                    );
            if (userService.isBlocked(creator.getId(), member.getId())) {
                throw new IllegalArgumentException("Пользователь с email " + member.getEmail() + " заблокирован");
            }
            members.add(createChatMember(chat, member, false, notificationLevel));
        }

        chatMemberRepository.saveAll(members);
        return chat;
    }

    private String generateUniqueInviteLink() {
        String inviteLink;
        do {
            inviteLink = UUID.randomUUID().toString();
        } while (chatRepository.existsByInviteLink(inviteLink));
        return inviteLink;
    }

    private ChatMember createChatMember(Chat chat, User user, boolean isAdmin, NotificationLevel notificationLevel) {
        if (chatMemberRepository.existsByChatIdAndUserId(chat.getId(), user.getId())) {
            return null;
        }

        UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
        NotificationLevel defaultLevel = (chat.getType() == ChatType.PERSONAL && settings != null)
                ? settings.getPersonalChatNotifications()
                : (settings != null ? settings.getGroupChatNotifications() : NotificationLevel.ALL);

        ChatMember member = new ChatMember();
        member.setChat(chat);
        member.setUser(user);
        member.setAdmin(isAdmin);
        member.setNotifications(notificationLevel != null ? notificationLevel : defaultLevel);
        return member;
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

    @Transactional
    public void addMembersToGroup(Long chatId, List<Long> userIds) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalArgumentException("Можно добавлять участников только в групповые чаты");
        }

        List<ChatMember> newMembers = new ArrayList<>();
        for (Long userId : userIds) {
            if (chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
                continue;
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь с ID " + userId + " не найден"));
            UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
            NotificationLevel defaultLevel = (settings != null) ? settings.getGroupChatNotifications() : NotificationLevel.ALL;

            ChatMember member = new ChatMember();
            member.setChat(chat);
            member.setUser(user);
            member.setNotifications(defaultLevel);
            member.setAdmin(false);
            newMembers.add(member);
        }

        if (!newMembers.isEmpty()) {
            chatMemberRepository.saveAll(newMembers);
        }

        List<Message> messages = messageRepository.findByChatId(chatId);
        for(ChatMember member : newMembers){
            for(Message message: messages){
                message.getReadBy().add(member.getUser());
            }
        }
    }

    @Transactional
    public void addMemberToGroup(Long chatId, Long userId) {
        addMembersToGroup(chatId, Collections.singletonList(userId));
    }

    @Transactional
    public void removeMemberFromGroup(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalArgumentException("Можно удалять участников только из групповых чатов");
        }
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Участник не найден в чате"));

        if (member.isAdmin()) {
            throw new IllegalStateException("Нельзя удалить администратора чата");
        }

        chatMemberRepository.delete(member);
    }

    @Transactional
    public void leaveGroup(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalArgumentException("Можно покидать только групповые чаты");
        }
        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Вы не являетесь участником чата"));

        if (member.isAdmin()) {
            throw new IllegalStateException("Администратор не может покинуть чат");
        }

        chatMemberRepository.delete(member);
    }

    @Transactional
    public void deleteChat(Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));
        List<File> files = fileRepository.findByMessageChatId(chatId);
        for (File file : files) {
            try {
                java.nio.file.Files.deleteIfExists(Paths.get(file.getFilePath()));
            } catch (IOException e) {
                System.out.println("Ошибка '" + e + "' при удалении файла " + file.getFilePath());
            }
        }
        chatRepository.delete(chat);
    }

    public Page<MessageDTO> getMessages(Long chatId, Long currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Message> messagePage = messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable);
        return messagePage.map(message -> mapToMessageDTO(message, currentUserId));
    }

    public String getInviteLink(Long chatId, String userEmail, HttpServletRequest request) {
        Chat chat = chatRepository.findChatWithMembersById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));

        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalStateException("Пригласительные ссылки доступны только для групповых чатов");
        }

        if (chat.getInviteLink() == null || chat.getInviteLink().isEmpty()) {
            chat.setInviteLink(UUID.randomUUID().toString());
            chatRepository.save(chat);
        }

        String effectiveBaseUrl = baseUrl.isEmpty()
                ? request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath()
                : baseUrl;
        return effectiveBaseUrl + "/group/join?link=" + chat.getInviteLink();
    }

    public void joinGroupByLink(String link, Long userId) {
        Chat chat = chatRepository.findByInviteLink(link)
                .orElseThrow(() -> new IllegalArgumentException("Недействительная ссылка"));

        if (chat.getType() != ChatType.GROUP) {
            throw new IllegalStateException("Ссылка недействительна");
        }

        boolean alreadyMember = chat.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId));
        if (alreadyMember) {
            throw new IllegalStateException("Вы уже состоите в этом чате");
        }

        addMemberToGroup(chat.getId(), userId);
    }

    public void resetInviteLink(Long chatId, String userEmail) {
        Chat chat = chatRepository.findChatWithMembersById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден"));

        boolean isAdmin = chat.getMembers().stream()
                .anyMatch(m -> m.getUser().getEmail().equals(userEmail) && m.isAdmin());
        if (!isAdmin) {
            throw new SecurityException("Только администратор может сбросить ссылку");
        }

        chat.setInviteLink(UUID.randomUUID().toString());
        chatRepository.save(chat);
    }

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
        List<Chat> chats = chatRepository.findByNameContainingAndUserId(normalizedQuery, userId);
        return mapToChatDTOs(chats, userId, email);
    }

    private List<ChatDTO> mapToChatDTOs(List<Chat> chats, Long userId, String email) {
        return chats.stream()
                .map(chat -> mapToChatDTO(chat, userId, email))
                .collect(Collectors.toList());
    }

    private ChatDTO mapToChatDTO(Chat chat, Long userId, String email) {
        ChatDTO dto = new ChatDTO();
        dto.setId(chat.getId());
        dto.setName(chat.getType() == ChatType.PERSONAL ? getPersonalChatName(chat, userId) : chat.getName());
        dto.setType(chat.getType());
        dto.setPersonal(chat.getType() == ChatType.PERSONAL);
        if (chat.getType() == ChatType.PERSONAL) {
            String recipientEmail = chat.getMembers().stream()
                    .filter(m -> !m.getUser().getEmail().equals(email))
                    .findFirst()
                    .map(m -> m.getUser().getEmail())
                    .orElse(null);
            dto.setRecipientEmail(recipientEmail);
        }
        List<Message> messages = messageRepository.findByChatId(chat.getId());
        if (!messages.isEmpty()) {
            Message last = messages.get(messages.size() - 1);
            if (last.getFiles() != null && !last.getFiles().isEmpty()) {
                dto.setLastMessage("отправил файлы");
            } else {
                dto.setLastMessage(last.getContent());
            }
            dto.setLastMessageDate(last.getTimestamp().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")));
            if(chat.getType() == ChatType.PERSONAL){
                dto.setUnreadCount(messageRepository.findUnreadByPersonalChatIdAndUser(chat.getId()).size());
            } else{
                dto.setUnreadCount(messageRepository.findUnreadByGroupChatIdAndUser(chat.getId(), userRepository.findById(userId).get()).size());
            }
        }
        if (chat.getType() == ChatType.PERSONAL) {
            ChatMember companion = chat.getMembers().stream()
                    .filter(member -> !member.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);
            dto.setAvatar(companion != null ? companion.getUser().getAvatarPath() : null);
        } else {
            dto.setAvatar(chat.getAvatarPath());
        }
        return dto;
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
        for (File file: message.getFiles()){
            System.out.println(message.getId() + " " + file.getId());
        }
        //пофиксить
        List<File> uniqueFiles = new ArrayList<>(new LinkedHashSet<>(message.getFiles()));
        dto.setFiles(uniqueFiles);

        for (File file : uniqueFiles) {
            System.out.println(message.getId() + " " + file.getId());
        }
        dto.setUserAvatar(message.getSender().getAvatarPath());
        return dto;
    }

    public void saveChat(Chat chat){
        chatRepository.save(chat);
    }

    public boolean isUserInChat(Long chatId, Long userId) {
        return chatMemberRepository.existsByChatIdAndUserId(chatId, userId);
    }

    public boolean existsByChatIdAndUserId(Long chatId, Long userId) {
        return chatMemberRepository.existsByChatIdAndUserId(chatId,userId);
    }

    @Transactional(readOnly = true)
    public Chat getChatWithMembers(Long chatId, String email) {
        Chat chat = chatRepository.findChatWithMembersById(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Чат не найден: " + chatId));
        boolean isMember = chat.getMembers().stream()
                .anyMatch(member -> member.getUser().getEmail().equals(email));
        if (!isMember) {
            throw new SecurityException("Пользователь " + email + " не является участником чата " + chatId);
        }
        return chat;
    }
}