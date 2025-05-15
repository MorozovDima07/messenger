package com.project.messenger.service;

import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.repository.UserSettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserSettingsService {

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    public UserSettings getUserSettings(User user) {
        return userSettingsRepository.findByUser(user)
                .orElseGet(() -> createDefaultSettings(user));
    }

    public void updateNotificationSettings(User user, NotificationLevel personalChatNotifications, NotificationLevel groupChatNotifications) {
        UserSettings settings = getUserSettings(user);
        settings.setPersonalChatNotifications(personalChatNotifications);
        settings.setGroupChatNotifications(groupChatNotifications);
        userSettingsRepository.save(settings);
    }

    public void resetNotificationSettings(User user) {
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