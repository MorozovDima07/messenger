package com.project.messenger.controller;

import com.project.messenger.model.Chat;
import com.project.messenger.model.ChatMember;
import com.project.messenger.model.NotificationLevel;
import com.project.messenger.model.User;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.UserService;
import com.project.messenger.service.UserServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ChatSettingsController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserServiceInterface userService;

    @GetMapping("/direct-set")
    public String directSettings(@RequestParam("id") Long id, Model model, Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return "redirect:/login";
        }
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        model.addAttribute("messages", chatService.getMessages(id, currentUser.getId()));
        model.addAttribute("chats", chatService.getDirectChats(auth.getName()));

        String currentUserEmail = auth.getName();
        ChatMember currentMember = chatService.getChatMember(id, currentUserEmail);
        model.addAttribute("notifications", currentMember.getNotifications());

        User contact = chatService.getChatContact(id, currentUserEmail);
        model.addAttribute("contact", contact);

        return "direct-set";
    }

    @PostMapping("/chats/{id}/block")
    public String blockUser(@PathVariable("id") Long id, Authentication auth) {
        Chat chat = chatService.getChat(id, auth.getName());
        Long blockedUserId = chat.getMembers().stream()
                .filter(m -> !m.getUser().getEmail().equals(auth.getName()))
                .findFirst()
                .map(m -> m.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));
        userService.blockUser(userService.findByEmail(auth.getName()).getId(), blockedUserId);
        return "redirect:/chats";
    }

    @GetMapping("/group-set")
    public String groupSettings(@RequestParam("id") Long id, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        model.addAttribute("messages", chatService.getMessages(id,currentUser.getId()));
        model.addAttribute("chats", chatService.getGroupChats(auth.getName()));
        return "group-set";
    }


    @GetMapping("/group/{id}/edit")
    public String editGroup(@PathVariable("id") Long id, Model model, Authentication auth) {
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        model.addAttribute("chats", chatService.getGroupChats(auth.getName()));
        return "group-edit";
    }


    @PostMapping("/group/{id}/edit")
    public String saveGroup(@PathVariable("id") Long id, @RequestParam("name") String name, Authentication auth) {
        Chat chat = chatService.getChat(id, auth.getName());
        chat.setName(name);
        chatService.createGroupChat(name, chat.getCreatedBy().getId(), chat.getMembers().stream()
                .map(m -> m.getUser().getId()).collect(Collectors.toList()));
        return "redirect:/group-set?id=" + id;
    }

    @PostMapping("/group/{id}/members/add")
    public String addMember(@PathVariable("id") Long id, @RequestParam("userId") Long userId, Authentication auth) {
        chatService.addMemberToGroup(id, userId);
        return "redirect:/group-set?id=" + id;
    }

    @PostMapping("/group/{id}/members/remove")
    public String removeMember(@PathVariable("id") Long id, @RequestParam("userId") Long userId, Authentication auth) {
        chatService.removeMemberFromGroup(id, userId);
        return "redirect:/group-set?id=" + id;
    }

    @PostMapping("/group/{id}/leave")
    public String leaveGroup(@PathVariable("id") Long id, Authentication auth) {
        chatService.leaveGroup(id, userService.findByEmail(auth.getName()).getId());
        return "redirect:/chats";
    }

    @GetMapping("/group/{id}/invite")
    public String inviteToGroup(@PathVariable("id") Long id, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        List<User> users = userService.getAllUsers().stream()
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .filter(user -> !chatService.getChat(id, auth.getName()).getMembers().stream()
                        .anyMatch(m -> m.getUser().getId().equals(user.getId())))
                .collect(Collectors.toList());
        String link = chatService.generateInviteLink(id);
        model.addAttribute("chat", chat);
        model.addAttribute("users", users);
        model.addAttribute("inviteLink", link);
        model.addAttribute("chats", chatService.getGroupChats(auth.getName()));
        return "invite";
    }

    @GetMapping("/group/{id}/link")
    public String getInviteLink(@PathVariable("id") Long id, Model model) {
        String link = chatService.generateInviteLink(id);
        model.addAttribute("inviteLink", link);
        return "invite-link";
    }

    @GetMapping("/join")
    public String joinGroup(@RequestParam("link") String link, Authentication auth) {
        chatService.joinGroupByLink(link, userService.findByEmail(auth.getName()).getId());
        return "redirect:/group";
    }

    @PostMapping("/direct/{id}/toggle-notifications")
    public String toggleNotifications(@PathVariable("id") Long id, Authentication auth) {
        String email = auth.getName();
        Chat chat = chatService.getChat(id, email);
        ChatMember currentMember = chat.getMembers().stream()
                .filter(member -> member.getUser().getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        // Переключение уведомлений
        if (currentMember.getNotifications() == NotificationLevel.ALL) {
            currentMember.setNotifications(NotificationLevel.NONE);
        } else {
            currentMember.setNotifications(NotificationLevel.ALL);
        }
        chatService.saveChatMember(currentMember);

        return "redirect:/direct-set?id=" + id;
    }
}