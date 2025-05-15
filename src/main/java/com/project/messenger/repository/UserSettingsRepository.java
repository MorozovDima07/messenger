package com.project.messenger.repository;

import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
    Optional<UserSettings> findByUserId(Long userId);
    Optional<UserSettings> findByUser(User user);
}