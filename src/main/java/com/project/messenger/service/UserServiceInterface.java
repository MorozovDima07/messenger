package com.project.messenger.service;

import com.project.messenger.model.BlockedUser;
import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserServiceInterface extends UserDetailsService {
    User registerUser(String email, String password, String username);
    User findByEmail(String email);
    Long getUserIdByEmail(String email);
    void updateUser(User user);
    void updateUserSettings(Long userId, NotificationLevel personalChatNotifications,
                            NotificationLevel groupChatNotifications, String theme);
    UserSettings getUserSettings(Long userId);
    void blockUser(Long userId, Long blockedUserId);
    void unblockUser(Long userId, Long blockedUserId);
    List<BlockedUser> getBlockedUsers(Long userId);
    boolean isBlocked(Long userId, Long targetUserId);
    UserDetails loadUserByUsername(String email);
    List<User> getAllUsers();
    boolean changePassword(String username, String currentPassword, String newPassword, String confirmPassword);
}