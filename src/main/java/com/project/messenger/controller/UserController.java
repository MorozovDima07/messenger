package com.project.messenger.controller;

import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.UserService;
import com.project.messenger.service.UserServiceInterface;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Controller
public class UserController {

    @Autowired
    private UserServiceInterface userService;

    @Autowired
    private ChatService chatService;

    @GetMapping("/profile")
    public String profile(Model model, Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        model.addAttribute("user", user);
        return "profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@RequestParam("username") String username, Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        user.setUsername(username);
        userService.updateUser(user);
        return "redirect:/profile";
    }

    @PostMapping("/profile/upload-avatar")
    public String uploadAvatar(@RequestParam("avatar") MultipartFile file, Authentication auth, Model model) throws IOException {
        if (!file.isEmpty()) {
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                if (file.getSize() <= 5 * 1024 * 1024) { // Максимум 5 МБ
                    User user = userService.findByEmail(auth.getName());
                    String avatarPath = saveAvatarFile(file, user.getId());
                    user.setAvatarPath(avatarPath);
                    userService.updateUser(user);
                } else {
                    model.addAttribute("error", "Файл слишком большой (максимум 5 МБ)");
                    return "profile";
                }
            } else {
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
    public String settings(Model model, Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        UserSettings settings = userService.getUserSettings(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("settings", settings);
        model.addAttribute("chats", chatService.getUserChats(auth.getName()));
        return "settings";
    }

    @PostMapping("/settings")
    public String updateSettings(@RequestParam("personalChatNotifications") NotificationLevel personalChatNotifications,
                                 @RequestParam("groupChatNotifications") NotificationLevel groupChatNotifications,
                                 @RequestParam("theme") String theme,
                                 Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        userService.updateUserSettings(user.getId(), personalChatNotifications, groupChatNotifications, theme);
        return "redirect:/settings";
    }

    @GetMapping("/blocked-users")
    public String blockedUsers(Model model, Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        model.addAttribute("blockedUsers", userService.getBlockedUsers(user.getId()));
        model.addAttribute("chats", chatService.getUserChats(auth.getName()));
        return "blocked-users";
    }

    @PostMapping("/blocked-users/unblock")
    public String unblockUser(@RequestParam("id") Long id, Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        userService.unblockUser(user.getId(), id);
        return "redirect:/blocked-users";
    }
}