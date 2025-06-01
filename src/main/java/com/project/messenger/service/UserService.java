package com.project.messenger.service;

import com.project.messenger.exception.AccessDeniedException;
import com.project.messenger.exception.ChatNotFoundException;
import com.project.messenger.exception.FileUploadException;
import com.project.messenger.exception.UserNotFoundException;
import com.project.messenger.model.*;
import com.project.messenger.model.dto.BlockedUserDTO;
import com.project.messenger.model.dto.RegisterRequest;
import com.project.messenger.repository.BlockedUserRepository;
import com.project.messenger.repository.ChatRepository;
import com.project.messenger.repository.UserRepository;
import com.project.messenger.repository.UserSettingsRepository;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class UserService implements UserServiceInterface {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BlockedUserRepository blockedUserRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ChatRepository chatRepository;


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с таким email не найден: " + email));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), new ArrayList<>());
    }

    @Transactional
    public User registerUser(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email уже зарегистрирован");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setUsername(request.getUsername());
        user.setLastActive(LocalDateTime.now());
        user.setEmailVisible(true);
        user = userRepository.save(user);

        UserSettings settings = new UserSettings();
        settings.setUser(user);
        settings.setPersonalChatNotifications(NotificationLevel.ALL);
        settings.setGroupChatNotifications(NotificationLevel.ALL);
        settings.setTheme("light");
        userSettingsRepository.save(settings);

        return user;
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с email " + email + " не найден"));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с id " + id + " не найден"));
    }

    @Transactional
    public void updateUser(User user) {
        User existingUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (user.getUsername().length() < 2 || user.getUsername().length() > 50) {
            throw new IllegalArgumentException("Имя пользователя должно быть от 2 до 50 символов");
        }

        existingUser.setUsername(user.getUsername());
        existingUser.setEmailVisible(user.isEmailVisible());
        existingUser.setAvatarPath(user.getAvatarPath());
        existingUser.setLastActive(LocalDateTime.now());
        userRepository.save(existingUser);
    }

    @Transactional
    public void updateUsername(String email, String name) {
        User user = findByEmail(email);
        if (user.getUsername().length() < 2 || user.getUsername().length() > 50) {
            throw new IllegalArgumentException("Имя пользователя должно быть от 2 до 50 символов");
        }
        user.setUsername(name);
        userRepository.save(user);
    }

    @Transactional
    public User updateAvatarAndGetUser(String email, MultipartFile file){
        User user = findByEmail(email);
        if (!file.isEmpty()) {
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                if (file.getSize() <= 5 * 1024 * 1024) { // Максимум 5 МБ
                    String avatarPath = saveAvatarFile(file, user.getId());
                    user.setAvatarPath(avatarPath);
                    updateUser(user);
                } else {
                    throw new FileUploadException("Файл слишком большой (максимум 5 МБ)");
                }
            } else {
                throw new FileUploadException("Допустимы только изображения");
            }
        }
        return user;
    }

    private String saveAvatarFile(MultipartFile file, Long userId) {
        String uploadDir = "uploads/avatars/";
        java.io.File dir = new java.io.File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new FileUploadException("Файл должен быть изображением (JPEG, PNG)");
        }

        User user = findById(userId);
        if (user.getAvatarPath() != null) {
            java.io.File oldAvatar = new java.io.File(user.getAvatarPath().substring(1)); // Убираем начальный слэш
            if (oldAvatar.exists()) {
                oldAvatar.delete();
            }
        }

        String fileName = "user_" + userId + "_" + System.currentTimeMillis() + "." + getFileExtension(file.getOriginalFilename());
        java.io.File dest = new java.io.File(dir.getAbsolutePath() + File.separator + fileName);

        try{
            BufferedImage originalImage = ImageIO.read(file.getInputStream());
            int targetSize = 400;
            BufferedImage resizedImage;

            if (originalImage.getWidth() < originalImage.getHeight()) {
                resizedImage = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_WIDTH, targetSize);
            } else {
                resizedImage = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_HEIGHT, targetSize);
            }

            ImageIO.write(resizedImage, getFileExtension(file.getOriginalFilename()), dest);
        } catch (IOException e){
            throw new FileUploadException("Не удалось сохранить файл: " + e);
        }
        return "/" + uploadDir + fileName;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "jpg";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    @Transactional
    public void updateUserSettings(Long userId, NotificationLevel personalChatNotifications,
                                   NotificationLevel groupChatNotifications, String theme) {
        UserSettings settings = userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Настройки пользователя не найдены"));
        settings.setPersonalChatNotifications(personalChatNotifications);
        settings.setGroupChatNotifications(groupChatNotifications);
        settings.setTheme(theme);
        userSettingsRepository.save(settings);
    }

    public UserSettings getUserSettings(Long userId) {
        return userSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Настройки пользователя не найдены"));
    }


    @Transactional
    public void blockUser(String userEmail, Long chatId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));

        Long blockedUserId = chat.getMembers().stream()
                .filter(m -> !m.getUser().getEmail().equals(userEmail))
                .findFirst()
                .map(m -> m.getUser().getId())
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        if (user.getId().equals(blockedUserId)) {
            throw new AccessDeniedException("Нельзя заблокировать самого себя");
        }

        User blockedUser = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь для блокировки не найден"));

        if (blockedUserRepository.existsByUserIdAndBlockedUserId(user.getId(), blockedUserId)) {
            throw new IllegalStateException("Пользователь уже заблокирован");
        }

        BlockedUser blocked = new BlockedUser();
        blocked.setUser(user);
        blocked.setBlockedUser(blockedUser);
        blockedUserRepository.save(blocked);
    }

    @Transactional
    public void unblockUser(Long userId, Long blockedUserId) {
        if (!blockedUserRepository.existsByUserIdAndBlockedUserId(userId, blockedUserId)) {
            throw new IllegalStateException("Пользователь не находится в списке заблокированных");
        }
        blockedUserRepository.deleteByUserIdAndBlockedUserId(userId, blockedUserId);
    }

    public Page<BlockedUserDTO> getBlockedUsers(Long userId, Pageable pageable) {
        return blockedUserRepository.findByUserId(userId, pageable).map(blockedUser -> {
            BlockedUserDTO dto = new BlockedUserDTO();
            dto.setId(blockedUser.getBlockedUser().getId());
            dto.setUsername(blockedUser.getBlockedUser().getUsername());
            dto.setEmail(blockedUser.getBlockedUser().getEmail());
            dto.setEmailVisible(blockedUser.getBlockedUser().isEmailVisible());
            dto.setAvatarPath(blockedUser.getBlockedUser().getAvatarPath());
            return dto;
        });
    }

    public boolean isBlocked(Long userId, Long targetUserId) {
        return blockedUserRepository.existsByUserIdAndBlockedUserId(userId, targetUserId);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public String getPasswordHashByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с email " + email + " не найден"));
        return user.getPassword();
    }

    @Override
    public void changePassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с таким email не найден: " + email));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public List<String> searchUsersByEmail(String query) {
        return userRepository.findByEmailContainingIgnoreCase(query).stream()
                .map(User::getEmail)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> searchUsersWithDirectChats(String currentUserEmail, String query) {
        User currentUser = findByEmail(currentUserEmail);

        List<Chat> directChats = chatRepository.findByTypeAndMembers_UserId(ChatType.PERSONAL, currentUser.getId());

        List<String> emails = directChats.stream()
                .flatMap(chat -> chat.getMembers().stream()
                        .filter(member -> !member.getUser().getEmail().equals(currentUserEmail))
                        .map(member -> member.getUser().getEmail()))
                .filter(email -> email.toLowerCase().contains(query.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());

        return emails;
    }
}