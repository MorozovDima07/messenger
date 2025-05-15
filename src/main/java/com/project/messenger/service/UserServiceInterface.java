package com.project.messenger.service;

import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.model.dto.BlockedUserDTO;
import com.project.messenger.model.dto.RegisterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserServiceInterface extends UserDetailsService {
    User registerUser(RegisterRequest request);
    User findByEmail(String email);
    User findById(Long id);
    Long getUserIdByEmail(String email);
    void updateUser(User user);
    void updateUserSettings(Long userId, NotificationLevel personalChatNotifications,
                            NotificationLevel groupChatNotifications, String theme);
    UserSettings getUserSettings(Long userId);
    void blockUser(Long userId, Long blockedUserId);
    void unblockUser(Long userId, Long blockedUserId);
    Page<BlockedUserDTO> getBlockedUsers(Long userId, Pageable pageable);
    boolean isBlocked(Long userId, Long targetUserId);
    UserDetails loadUserByUsername(String email);
    List<User> getAllUsers();
    void changePassword(String username, String newPassword);
    String getPasswordHashByEmail(String email);
    List<String> searchUsersByEmail(String query);
    List<String> searchUsersWithDirectChats(String currentUserEmail, String query);
}