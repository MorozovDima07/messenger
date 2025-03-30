package com.project.messenger.controller;

import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.model.UserSettings;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.UserService;
import com.project.messenger.service.UserServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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