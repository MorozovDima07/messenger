package com.project.messenger.controller;

import com.project.messenger.model.Chat;
import com.project.messenger.model.ChatType;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.NotificationService;
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
                                      @RequestParam(name = "enabled") boolean enabled,
                                      Authentication auth) {
        Chat chat = notificationService.toggleNotificationsAndGetChat(id, auth.getName(), enabled);
        String redirect = chat.getType() == ChatType.PERSONAL ? "direct-set" : "group-set";
        return "redirect:/" + redirect + "?id=" + id;
    }
}