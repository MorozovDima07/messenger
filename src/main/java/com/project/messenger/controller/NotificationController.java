package com.project.messenger.controller;

import com.project.messenger.model.User;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.NotificationService;
import com.project.messenger.service.UserService;
import com.project.messenger.service.UserServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserServiceInterface userService;

    @Autowired
    private ChatService chatService;

    @PostMapping("/chats/{id}/notifications")
    public String toggleNotifications(@PathVariable("id") Long id,
                                      @RequestParam("enabled") boolean enabled,
                                      Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        notificationService.toggleNotifications(id, user.getId(), enabled);
        return "redirect:/group-set?id=" + id;
    }
}