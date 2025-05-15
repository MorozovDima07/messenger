package com.project.messenger.controller;

import com.project.messenger.model.ChatType;
import com.project.messenger.model.User;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.NotificationService;
import com.project.messenger.service.UserService;
import com.project.messenger.service.UserServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
                                      Authentication auth,
                                      Model model) {
        try {
            User user = userService.findByEmail(auth.getName());
            notificationService.toggleNotifications(id, user.getId(), enabled);
            String redirect = chatService.getChat(id, auth.getName()).getType() == ChatType.PERSONAL ? "direct-set" : "group-set";
            return "redirect:/" + redirect + "?id=" + id;
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", chatService.getChat(id, auth.getName()).getType());
            return "error";
        }
    }
}