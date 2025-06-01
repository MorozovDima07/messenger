package com.project.messenger.service;

import com.project.messenger.exception.*;
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
    public Page<ChatDTO> getChatsPage(String email, ChatType chatType, String chatName, Pageable pageable) {
        if (chatName == null || chatName.isBlank()) {
            return getChatsForUser(email, chatType, pageable);
        }
        return getChatsByName(email, chatName, chatType, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ChatDTO> getChatsByName(String email, String query, ChatType chatType, Pageable pageable) {
        String normalizedQuery = query.trim().toLowerCase();
        Long userId = userService.findByEmail(email).getId();
        Page<Chat> chats;
        if (chatType == null) {
            chats = chatRepository.findChatsAmongAll(userId, normalizedQuery, pageable);
        } else {
            chats = switch (chatType) {
                case PERSONAL -> chatRepository.findPersonalChatsByOtherUsername(userId, normalizedQuery, pageable);
                case GROUP -> chatRepository.findGroupChatsByName(userId, normalizedQuery, pageable);
            };
        }
        return chats.map(chat -> mapToChatDTO(chat, userId, email));
    }

    @Transactional
    public void updateChatAvatar(Long chatId, MultipartFile avatarFile, String userEmail) throws IOException {
        Chat chat = getChat(chatId, userEmail);

        if (avatarFile == null || avatarFile.isEmpty()) {
            throw new FileUploadException("Файл аватара не предоставлен");
        }

        String contentType = avatarFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new FileUploadException("Файл должен быть изображением (JPEG, PNG)");
        }

        String uploadDirGroup = "uploads/group-avatars/";
        java.io.File dir = new java.io.File(uploadDirGroup);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new FileUploadException("Не удалось создать директорию для загрузки аватара");
        }

        if (chat.getAvatarPath() != null) {
            java.io.File oldAvatar = new java.io.File(chat.getAvatarPath().substring(1));
            if (oldAvatar.exists()) {
                oldAvatar.delete();
            }
        }

        String fileName = "group_" + chatId + "_" + UUID.randomUUID() + "." + getFileExtension(avatarFile.getOriginalFilename());
        java.io.File dest = new java.io.File(dir.getAbsolutePath() + java.io.File.separator + fileName);

        BufferedImage originalImage = ImageIO.read(avatarFile.getInputStream());
        if (originalImage == null) {
            throw new FileUploadException("Не удалось прочитать изображение");
        }
        int targetSize = 400;
        BufferedImage resizedImage = originalImage.getWidth() < originalImage.getHeight()
                ? Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_WIDTH, targetSize)
                : Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_HEIGHT, targetSize);

        if (!ImageIO.write(resizedImage, getFileExtension(avatarFile.getOriginalFilename()), dest)) {
            throw new FileUploadException("Не удалось сохранить изображение");
        }

        chat.setAvatarPath("/" + uploadDirGroup + fileName);
        chatRepository.save(chat);
    }

    @Transactional
    public void updateChatName(Long chatId, String name, String userEmail) {
        Chat chat = getChat(chatId, userEmail);
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
                .orElseThrow(() -> new ChatNotFoundException(chatId));
        if (!chatMemberRepository.existsByChatIdAndUserEmail(chatId, email)) {
            throw new AccessDeniedException("У вас нет доступа к этому чату");
        }
        return chat;
    }

    public Chat getChatUsingUserId(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));
        if (!chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
            throw new AccessDeniedException("У вас нет доступа к этому чату");
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public Chat getGroupChat(Long chatId, String email) {
        Chat chat = getChat(chatId, email);
        if (!chatMemberRepository.existsByChatIdAndUserEmail(chatId, email)) {
            throw new AccessDeniedException("У вас нет доступа к этому чату");
        }
        if (chat.getType() != ChatType.GROUP) {
            throw new InvalidChatOperationException("Доступно только для групповых чатов");
        }
        return chat;
    }

    @Transactional(readOnly = true)
    public Page<ChatMemberDTO> getChatMembers(Long chatId, String email, Pageable pageable) {
        getChat(chatId, email);
        return chatMemberRepository.findByChatId(chatId, pageable).map(member -> {
            ChatMemberDTO dto = new ChatMemberDTO();
            dto.setUserId(member.getUser().getId());
            dto.setUsername(member.getUser().getUsername());
            dto.setEmail(member.getUser().getEmail());
            dto.setAdmin(member.isAdmin());
            dto.setLastActive(member.getUser().getLastActive());
            dto.setOnline(false); //доработать в будущем
            dto.setAvatarPath(member.getUser().getAvatarPath());
            return dto;
        });
    }

    @Transactional(readOnly = true)
    public ChatMember getChatMember(Long chatId, String email) {
        getChat(chatId, email);
        return chatMemberRepository.findByChatIdAndUserEmail(chatId, email)
                .orElseThrow(() -> new InvalidChatOperationException("Участник не найден в чате"));
    }

    @Transactional(readOnly = true)
    public User getChatContact(Long chatId, String currentUserEmail) {
        Chat chat = getChat(chatId, currentUserEmail);
        return chat.getMembers().stream()
                .map(ChatMember::getUser)
                .filter(user -> !user.getEmail().equals(currentUserEmail))
                .findFirst()
                .orElseThrow(() -> new InvalidChatOperationException("Собеседник не найден в чате"));
    }

    @Transactional
    public Chat createDirectChat(String currentUserEmail, String recipientEmail) {
        if (currentUserEmail.equals(recipientEmail)) {
            throw new InvalidChatOperationException("Нельзя создать чат с самим собой");
        }
        User user1 = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new UserNotFoundException(currentUserEmail));
        User user2 = userRepository.findByEmail(recipientEmail)
                .orElseThrow(() -> new UserNotFoundException(recipientEmail));

        if (userService.isBlocked(user1.getId(), user2.getId())) {
            throw new InvalidChatOperationException("Пользователь заблокирован");
        }

        Optional<Chat> existingChat = chatRepository.findPersonalChatBetweenUsers(user1.getId(), user2.getId());
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
    public Chat createGroupChat(String name, String creatorEmail, List<String> memberEmails, NotificationLevel notificationLevel) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidChatOperationException("Название чата не может быть пустым для группового чата");
        }

        User creator = userService.findByEmail(creatorEmail);
        Set<Long> memberIds = new HashSet<>();
        List<String> errors = new ArrayList<>();

        for (String email : memberEmails) {
            if (email == null || email.trim().isEmpty()) {
                continue;
            }
            try {
                User user = userService.findByEmail(email.trim());
                if (user.getId().equals(creator.getId())) {
                    continue;
                }
                if (userService.isBlocked(creator.getId(), user.getId())) {
                    errors.add("Пользователь с email " + email + " заблокирован");
                } else if (!memberIds.contains(user.getId())) {
                    memberIds.add(user.getId());
                }
            } catch (IllegalArgumentException e) {
                errors.add("Пользователь с email " + email + " не найден");
            }
        }

        if (!errors.isEmpty()) {
            throw new InvalidChatOperationException(String.join("; ", errors));
        }

        if (memberIds.isEmpty()) {
            throw new InvalidChatOperationException("Групповой чат должен содержать хотя бы одного участника помимо вас");
        }

        Chat chat = new Chat();
        chat.setName(name);
        chat.setType(ChatType.GROUP);
        chat.setCreatedBy(creator);
        chat.setInviteLink(generateUniqueInviteLink());
        chat.setLastMessageTimestamp(LocalDateTime.now());
        chat = chatRepository.save(chat);

        List<ChatMember> members = new ArrayList<>();
        members.add(createChatMember(chat, creator, true, notificationLevel));

        for (Long memberId : memberIds) {
            User member = userRepository.findById(memberId)
                    .orElseThrow(() -> new UserNotFoundException(memberId));
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
    public List<String> addMembersToGroup(Long chatId, List<String> userEmails, String email) {
        Chat chat = getChat(chatId, email);

        if (chat.getType() != ChatType.GROUP) {
            throw new InvalidChatOperationException("Можно добавлять участников только в групповые чаты");
        }

        User currentUser = userService.findByEmail(email);

        List<String> errors = new ArrayList<>();
        List<ChatMember> newMembers = new ArrayList<>();

        for (String userEmail : userEmails) {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new UserNotFoundException(userEmail));
            if (chatMemberRepository.existsByChatIdAndUserId(chatId, user.getId())) {
                errors.add("Пользователь " + userEmail + " уже в чате");
                continue;
            } else if(userService.isBlocked(currentUser.getId(), user.getId())){
                errors.add("Пользователь " + userEmail + " заблокирован");
                continue;
            }

            UserSettings settings = userSettingsRepository.findByUserId(user.getId()).orElse(null);
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
        } else {
            errors.add("Не выбрано ни одного нового участника");
        }

        for(ChatMember member : newMembers){
            messageRepository.markMessagesAsReadByUser(chatId, member.getUser().getId());
        }

        return errors;
    }

    @Transactional
    public void addMemberToGroup(Long chatId, Long userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));
        if (chat.getType() != ChatType.GROUP) {
            throw new InvalidChatOperationException("Можно добавлять участников только в групповые чаты");
        }

        if (chatMemberRepository.existsByChatIdAndUserId(chatId, userId)) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        UserSettings settings = userSettingsRepository.findByUserId(userId).orElse(null);
        NotificationLevel defaultLevel = (settings != null) ? settings.getGroupChatNotifications() : NotificationLevel.ALL;

        ChatMember member = new ChatMember();
        member.setChat(chat);
        member.setUser(user);
        member.setNotifications(defaultLevel);
        member.setAdmin(false);

        chatMemberRepository.save(member);

        messageRepository.markMessagesAsReadByUser(chatId, member.getUser().getId());
    }

    @Transactional
    public void removeMemberFromGroup(Long chatId, Long userId, String userEmail) {
        Chat chat = getChat(chatId, userEmail);
        if (chat.getType() != ChatType.GROUP) {
            throw new InvalidChatOperationException("Можно удалять участников только из групповых чатов");
        }
        ChatMember currentMember = getChatMember(chatId, userEmail);

        if (!currentMember.isAdmin()) {
            throw new InvalidChatOperationException("Только администратор может удалять участников!");
        }

        ChatMember member = chatMemberRepository.findByChatIdAndUserId(chatId, userId)
                .orElseThrow(() -> new InvalidChatOperationException("Нельзя удалить самого себя!"));

        if (currentMember.getId().equals(member.getId())) {
            throw new InvalidChatOperationException("");
        }

        if (member.isAdmin()) {
            throw new InvalidChatOperationException("Нельзя удалить администратора чата");
        }

        chatMemberRepository.delete(member);
    }

    @Transactional
    public void leaveGroup(Long chatId, String userEmail) {
        Chat chat = getChat(chatId, userEmail);
        if (chat.getType() != ChatType.GROUP) {
            throw new InvalidChatOperationException("Можно покидать только групповые чаты");
        }
        ChatMember member = chatMemberRepository.findByChatIdAndUserEmail(chatId, userEmail)
                .orElseThrow(() -> new InvalidChatOperationException("Вы не являетесь участником чата"));

        if (member.isAdmin()) {
            throw new InvalidChatOperationException("Администратор не может покинуть чат");
        }

        chatMemberRepository.delete(member);
    }

    @Transactional
    public void deleteChat(Long chatId, String userEmail) {
        Chat chat = getChat(chatId, userEmail);
        if (chat.getType() == ChatType.GROUP) {
            ChatMember member = chatMemberRepository.findByChatIdAndUserEmail(chatId, userEmail)
                    .orElseThrow(() -> new InvalidChatOperationException("Участник не найден в чате"));
            if (!member.isAdmin()) {
                throw new AccessDeniedException("Только администратор может удалить групповой чат");
            }
        }
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

    @Transactional(readOnly = true)
    public Page<MessageDTO> getMessages(Long chatId, Long currentUserId, int page, int size) {
        getChatUsingUserId(chatId,currentUserId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<Message> messagePage = messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable);
        return messagePage.map(message -> mapToMessageDTO(message, currentUserId));
    }

    @Transactional(readOnly = true)
    public String getInviteLink(Long chatId, String userEmail, HttpServletRequest request) {
        Chat chat = getChat(chatId, userEmail);

        if (chat.getType() != ChatType.GROUP) {
            throw new InvalidChatOperationException("Пригласительные ссылки доступны только для групповых чатов");
        }

        if (!chatMemberRepository.existsByChatIdAndUserEmail(chatId, userEmail)) {
            throw new AccessDeniedException("У вас нет доступа к этому чату");
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

    @Transactional
    public Chat joinGroupByLinkAndGetChat(String link, String userEmail) {
        Chat chat = chatRepository.findByInviteLink(link)
                .orElseThrow(() -> new ChatNotFoundException(0L));

        if (chat.getType() != ChatType.GROUP) {
            throw new InvalidChatOperationException("Ссылка недействительна");
        }

        User user = userService.findByEmail(userEmail);

        boolean alreadyMember = chat.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(user.getId()));
        if (alreadyMember) {
            throw new InvalidChatOperationException("Вы уже состоите в этом чате");
        }

        addMemberToGroup(chat.getId(), user.getId());

        return chat;
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

    public boolean isUserInChat(Long chatId, String userEmail) {
        return chatMemberRepository.existsByChatIdAndUserEmail(chatId, userEmail);
    }

    @Transactional(readOnly = true)
    public Chat getChatWithMembers(Long chatId, String email) {
        Chat chat = getChat(chatId, email);
        boolean isMember = chat.getMembers().stream()
                .anyMatch(member -> member.getUser().getEmail().equals(email));
        if (!isMember) {
            throw new AccessDeniedException("Пользователь " + email + " не является участником чата " + chatId);
        }
        return chat;
    }
}