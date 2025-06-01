package com.project.messenger.controller;

import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.model.dto.BlockedUserDTO;
import com.project.messenger.model.dto.ChangePasswordDTO;
import com.project.messenger.service.UserServiceInterface;
import com.project.messenger.service.UserSettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class UserController extends BaseController{

    @Autowired
    private UserServiceInterface userService;

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
        userService.updateUsername(auth.getName(), username);
        return "redirect:/settings?page=" + page + "&size=" + size;
    }

    @PostMapping("/profile/upload-avatar")
    public String uploadAvatar(@RequestParam(name = "avatar") MultipartFile file, Authentication auth, Model model) {
        User user = userService.updateAvatarAndGetUser(auth.getName(), file);
        model.addAttribute("user", user);
        model.addAttribute("userEmail", auth.getName());
        return "profile";
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
        User user = userService.findByEmail(auth.getName());
        model.addAttribute("blockedUsersPage", userService.getBlockedUsers(user.getId(), PageRequest.of(page, size)));
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
        User user = userService.findByEmail(auth.getName());
        userService.unblockUser(user.getId(), id);
        return "redirect:/blocked-users?page=" + page + "&size=" + size + "&scrollPosition=" + scrollPosition;
    }

    @GetMapping("/blocked-users/list")
    @ResponseBody
    public ResponseEntity<Page<BlockedUserDTO>> getBlockedUsersAjax(@RequestParam(name = "page") int page,
                                                                    @RequestParam(name = "size") int size,
                                                                    Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        Page<BlockedUserDTO> blockedUsersPage = userService.getBlockedUsers(user.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(blockedUsersPage);
    }

    @GetMapping("/notification-settings")
    public String notificationSettings(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        UserSettings settings = userSettingsService.getUserSettings(user);
        model.addAttribute("settings", settings);
        model.addAttribute("userEmail", auth.getName());
        return "notification-settings";
    }

    @PostMapping("/notification-settings/update")
    public String updateNotificationSettings(
            @RequestParam(name = "personalChatNotifications", value = "personalChatNotifications", defaultValue = "false") boolean personalChatNotifications,
            @RequestParam(name = "groupChatNotifications", value = "groupChatNotifications", defaultValue = "false") boolean groupChatNotifications,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        userSettingsService.updateNotificationSettings(auth.getName(), personalChatNotifications, groupChatNotifications);
        return "redirect:/notification-settings?page=" + page + "&size=" + size;
    }

    @PostMapping("/notification-settings/reset")
    public String resetNotificationSettings(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        userSettingsService.resetNotificationSettings(auth.getName());
        return "redirect:/notification-settings?page=" + page + "&size=" + size;
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