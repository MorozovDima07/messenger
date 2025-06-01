package com.project.messenger.service;

import com.project.messenger.exception.UserNotFoundException;
import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.repository.UserRepository;
import com.project.messenger.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserSettingsService {

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    public UserSettings getUserSettings(User user) {
        return userSettingsRepository.findByUser(user)
                .orElseGet(() -> createDefaultSettings(user));
    }

    public void updateNotificationSettings(String email, boolean personalChatNotifications, boolean groupChatNotifications) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
        UserSettings settings = getUserSettings(user);
        NotificationLevel personalLevel = personalChatNotifications ? NotificationLevel.ALL : NotificationLevel.NONE;
        NotificationLevel groupLevel = groupChatNotifications ? NotificationLevel.ALL : NotificationLevel.NONE;
        settings.setPersonalChatNotifications(personalLevel);
        settings.setGroupChatNotifications(groupLevel);
        userSettingsRepository.save(settings);
    }

    public void resetNotificationSettings(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
        UserSettings settings = getUserSettings(user);
        settings.setPersonalChatNotifications(NotificationLevel.ALL);
        settings.setGroupChatNotifications(NotificationLevel.ALL);
        userSettingsRepository.save(settings);
    }

    private UserSettings createDefaultSettings(User user) {
        UserSettings settings = new UserSettings();
        settings.setUser(user);
        settings.setPersonalChatNotifications(NotificationLevel.ALL);
        settings.setGroupChatNotifications(NotificationLevel.ALL);
        settings.setTheme("light");
        return userSettingsRepository.save(settings);
    }
}