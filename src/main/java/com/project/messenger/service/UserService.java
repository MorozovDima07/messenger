package com.project.messenger.service;

import com.project.messenger.model.BlockedUser;
import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.repository.BlockedUserRepository;
import com.project.messenger.repository.UserRepository;
import com.project.messenger.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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


    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с таким email не найден: " + email));
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), new ArrayList<>());
    }

    @Transactional
    public User registerUser(String email, String password, String username) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email уже зарегистрирован");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Пароль должен содержать минимум 8 символов");
        }
        if (username.length() < 2 || username.length() > 50) {
            throw new IllegalArgumentException("Имя пользователя должно быть от 2 до 50 символов");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setUsername(username);
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

    public Long getUserIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с email " + email + " не найден"));
        return user.getId();
    }

    public String getPasswordHashByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с email " + email + " не найден"));
        return user.getPassword();
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
    public void blockUser(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new IllegalArgumentException("Нельзя заблокировать самого себя");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        User blockedUser = userRepository.findById(blockedUserId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь для блокировки не найден"));

        if (blockedUserRepository.existsByUserIdAndBlockedUserId(userId, blockedUserId)) {
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


    public List<BlockedUser> getBlockedUsers(Long userId) {
        return blockedUserRepository.findByUserIdWithBlockedUser(userId);
    }


    public boolean isBlocked(Long userId, Long targetUserId) {
        return blockedUserRepository.existsByUserIdAndBlockedUserId(userId, targetUserId);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public void changePassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь с таким email не найден: " + email));

//        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
//            return false;
//        }
//
//        if (!newPassword.equals(confirmPassword)) {
//            return false;
//        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
//        return true;
    }
}