package com.project.messenger.controller;

import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.model.dto.BlockedUserDTO;
import com.project.messenger.model.dto.ChangePasswordDTO;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.UserService;
import com.project.messenger.service.UserServiceInterface;
import com.project.messenger.service.UserSettingsService;
import jakarta.validation.Valid;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Controller
public class UserController extends BaseController{

    @Autowired
    private UserServiceInterface userService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/profile")
    public String profile(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        model.addAttribute("user", user);
        model.addAttribute("userEmail", auth.getName());
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam(name = "username") String username,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                @RequestParam(name = "size", defaultValue = "10") int size,
                                Authentication auth,
                                Model model) {
        User user = userService.findByEmail(auth.getName());
        user.setUsername(username);
        userService.updateUser(user);
        UserSettings settings = userService.getUserSettings(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("settings", settings);
        return "redirect:/settings?page=" + page + "&size=" + size;
    }

    @PostMapping("/profile/upload-avatar")
    public String uploadAvatar(@RequestParam(name = "avatar") MultipartFile file, Authentication auth, Model model) throws IOException {
        if (!file.isEmpty()) {
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                if (file.getSize() <= 5 * 1024 * 1024) { // Максимум 5 МБ
                    User user = userService.findByEmail(auth.getName());
                    String avatarPath = saveAvatarFile(file, user.getId());
                    user.setAvatarPath(avatarPath);
                    userService.updateUser(user);
                } else {
                    model.addAttribute("userEmail", auth.getName());
                    model.addAttribute("error", "Файл слишком большой (максимум 5 МБ)");
                    return "profile";
                }
            } else {
                model.addAttribute("userEmail", auth.getName());
                model.addAttribute("error", "Допустимы только изображения");
                return "profile";
            }
        }
        return "redirect:/profile";
    }

    private String saveAvatarFile(MultipartFile file, Long userId) throws IOException {
        String uploadDir = "uploads/avatars/";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("Файл должен быть изображением (JPEG, PNG)");
        }

        User user = userService.findById(userId);
        if (user.getAvatarPath() != null) {
            File oldAvatar = new File(user.getAvatarPath().substring(1)); // Убираем начальный слэш
            if (oldAvatar.exists()) {
                oldAvatar.delete();
            }
        }

        String fileName = "user_" + userId + "_" + System.currentTimeMillis() + "." + getFileExtension(file.getOriginalFilename());
        File dest = new File(dir.getAbsolutePath() + File.separator + fileName);

        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        int targetSize = 400;
        BufferedImage resizedImage;

        if (originalImage.getWidth() < originalImage.getHeight()) {
            resizedImage = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_WIDTH, targetSize);
        } else {
            resizedImage = Scalr.resize(originalImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_TO_HEIGHT, targetSize);
        }

        ImageIO.write(resizedImage, getFileExtension(file.getOriginalFilename()), dest);

        return "/" + uploadDir + fileName;
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "jpg";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    @GetMapping("/settings")
    public String settings(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        UserSettings settings = userService.getUserSettings(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("settings", settings);
        model.addAttribute("userEmail", auth.getName());
        return "settings";
    }

    @PostMapping("/settings")
    public String updateSettings(@RequestParam(name = "personalChatNotifications") NotificationLevel personalChatNotifications,
                                 @RequestParam(name = "groupChatNotifications") NotificationLevel groupChatNotifications,
                                 @RequestParam(name = "theme") String theme,
                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "size", defaultValue = "10") int size,
                                 Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        userService.updateUserSettings(user.getId(), personalChatNotifications, groupChatNotifications, theme);
        return "redirect:/settings?page=" + page + "&size=" + size;
    }

    @GetMapping("/blocked-users")
    public String blockedUsers(@RequestParam(name = "page", value = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", value = "size", defaultValue = "10") int size,
                               @RequestParam(name = "scrollPosition", value = "scrollPosition", defaultValue = "0") int scrollPosition,
                               Model model,
                               Authentication auth) {
        String email = auth.getName();
        User user = userService.findByEmail(email);
        Pageable pageable = PageRequest.of(page, size);
        model.addAttribute("blockedUsersPage", userService.getBlockedUsers(user.getId(), pageable));
        model.addAttribute("chatType", null); // Покажем все чаты
        model.addAttribute("scrollPosition", scrollPosition);
        model.addAttribute("userEmail", auth.getName());
        return "blocked-users";
    }

    @PostMapping("/blocked-users/unblock")
    public String unblockUser(@RequestParam(name = "id") Long id,
                              @RequestParam(name = "page", value = "page", defaultValue = "0") int page,
                              @RequestParam(name = "size", value = "size", defaultValue = "10") int size,
                              @RequestParam(name = "scrollPosition", value = "scrollPosition", defaultValue = "0") int scrollPosition,
                              Authentication auth,
                              Model model) {
        try {
            User user = userService.findByEmail(auth.getName());
            userService.unblockUser(user.getId(), id);
            model.addAttribute("success", "Пользователь разблокирован");
            return "redirect:/blocked-users?page=" + page + "&size=" + size + "&scrollPosition=" + scrollPosition;
        } catch (IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", null);
            return "blocked-users";
        }
    }

    @GetMapping("/blocked-users/list")
    @ResponseBody
    public ResponseEntity<Page<BlockedUserDTO>> getBlockedUsersAjax(@RequestParam(name = "page") int page,
                                                                    @RequestParam(name = "size") int size,
                                                                    Authentication auth) {
        try {
            String email = auth.getName();
            User user = userService.findByEmail(email);
            Pageable pageable = PageRequest.of(page, size);
            Page<BlockedUserDTO> blockedUsersPage = userService.getBlockedUsers(user.getId(), pageable);
            return ResponseEntity.ok(blockedUsersPage);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/notification-settings")
    public String notificationSettings(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        try {
            String email = auth.getName();
            User user = userService.findByEmail(email);
            UserSettings settings = userSettingsService.getUserSettings(user);
            model.addAttribute("settings", settings);
            model.addAttribute("userEmail", auth.getName());
            return "notification-settings";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", null);
            model.addAttribute("userEmail", auth.getName());
            return "error";
        }
    }

    @PostMapping("/notification-settings/update")
    public String updateNotificationSettings(
            @RequestParam(name = "personalChatNotifications", value = "personalChatNotifications", defaultValue = "false") boolean personalChatNotifications,
            @RequestParam(name = "groupChatNotifications", value = "groupChatNotifications", defaultValue = "false") boolean groupChatNotifications,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        try {
            User user = userService.findByEmail(auth.getName());
            NotificationLevel personalLevel = personalChatNotifications ? NotificationLevel.ALL : NotificationLevel.NONE;
            NotificationLevel groupLevel = groupChatNotifications ? NotificationLevel.ALL : NotificationLevel.NONE;
            userSettingsService.updateNotificationSettings(user, personalLevel, groupLevel);
            model.addAttribute("success", "Настройки уведомлений обновлены");
            model.addAttribute("settings", userSettingsService.getUserSettings(user));
            return "redirect:/notification-settings?page=" + page + "&size=" + size;
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("settings", userSettingsService.getUserSettings(userService.findByEmail(auth.getName())));
            model.addAttribute("chatType", null);
            model.addAttribute("userEmail", auth.getName());
            return "notification-settings";
        }
    }

    @PostMapping("/notification-settings/reset")
    public String resetNotificationSettings(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        try {
            User user = userService.findByEmail(auth.getName());
            userSettingsService.resetNotificationSettings(user);
            model.addAttribute("success", "Настройки уведомлений сброшены");
            model.addAttribute("settings", userSettingsService.getUserSettings(user));
            return "redirect:/notification-settings?page=" + page + "&size=" + size;
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("settings", userSettingsService.getUserSettings(userService.findByEmail(auth.getName())));
            model.addAttribute("chatType", null);
            return "notification-settings";
        }
    }

    @GetMapping("/change-password")
    public String showChangePasswordForm(Model model) {
        model.addAttribute("passwordDTO", new ChangePasswordDTO());
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute("passwordDTO") ChangePasswordDTO passwordDTO,
                                 BindingResult bindingResult,
                                 Authentication auth,
                                 Model model) {

        if (!passwordDTO.getNewPassword().equals(passwordDTO.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Пароли не совпадают");
        }

        String storedCurrentPassword = userService.getPasswordHashByEmail(auth.getName());
        if (!passwordEncoder.matches(passwordDTO.getCurrentPassword(), storedCurrentPassword)) {
            bindingResult.rejectValue("currentPassword", "error.currentPassword", "Старый пароль введён неверно");
        }

        if (bindingResult.hasErrors()) {
            return "change-password";
        }

        userService.changePassword(auth.getName(), passwordDTO.getNewPassword());

        model.addAttribute("successMessage", "Пароль успешно изменён");

        return "change-password";
    }
}