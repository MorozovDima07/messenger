package com.project.messenger.controller;

import com.project.messenger.model.*;
import com.project.messenger.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class ChatSettingsController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private FileService fileService;

    @Autowired
    private MessageService messageService;

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

    @PostMapping("/direct/{id}/block")
    public String blockUser(@PathVariable("id") Long id, Authentication auth) {
        Chat chat = chatService.getChatWithMembers(id, auth.getName());
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
        ChatMember currentMember = chatService.getChatMember(id, currentUser.getEmail());

        model.addAttribute("chat", chat);
        model.addAttribute("currentMember", currentMember);
        model.addAttribute("messages", chatService.getMessages(id, currentUser.getId()));
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
    public String addMembers(@PathVariable("id") Long id, @RequestParam("emails") List<String> emails, Authentication auth) {
        for (String email : emails) {
            if (email != null && !email.trim().isEmpty()) {
                User userToAdd = userService.findByEmail(email.trim());
                if (userToAdd == null) {
                    return "redirect:/group-set?id=" + id + "&error=user_not_found";
                }
                chatService.addMemberToGroup(id, userToAdd.getId());
            }
        }
        return "redirect:/group-set?id=" + id;
    }

    @PostMapping("/group/{id}/members/remove")
    public String removeMember(@PathVariable("id") Long id, @RequestParam("userId") Long userId, Authentication auth) {
        chatService.removeMemberFromGroup(id, userId);
        return "redirect:/group-set?id=" + id;
    }

    @PostMapping("/group/{id}/leave")
    public String leaveGroup(@PathVariable("id") Long id, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        chatService.leaveGroup(id, currentUser.getId());
        return "redirect:/chats";
    }

    @GetMapping("/group/{id}/invite")
    public String inviteToGroup(@PathVariable("id") Long id, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        String link = chatService.getInviteLink(id);
        model.addAttribute("chat", chat);
        model.addAttribute("inviteLink", link);
        model.addAttribute("chats", chatService.getGroupChats(auth.getName()));
        return "invite";
    }

    @GetMapping("/join")
    public String joinGroup(@RequestParam("link") String link, Authentication auth) {
        User user = userService.findByEmail(auth.getName());
        chatService.joinGroupByLink(link, user.getId());
        return "redirect:/group";
    }

    @PostMapping("/chats/{id}/delete")
    public String deleteChat(@PathVariable("id") Long id, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        ChatMember member = chatService.getChatMember(id, currentUser.getEmail());
        if (!member.isAdmin()) {
            throw new IllegalStateException("Только администратор может удалить чат");
        }
        chatService.deleteChat(id);
        return "redirect:/chats";
    }

    @GetMapping("/chat/{id}/files")
    public String chatFiles(@PathVariable("id") Long id, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        List<File> files = fileService.getChatFiles(id);

        model.addAttribute("chat", chat);
        model.addAttribute("files", files);
        model.addAttribute("chats", chatService.getGroupChats(auth.getName()));
        return "chat-files";
    }

    @PostMapping("/direct/{id}/toggle-notifications")
    public String toggleNotifications(@PathVariable("id") Long id, Authentication auth) {
        String email = auth.getName();
        Chat chat = chatService.getChat(id, email);
        ChatMember currentMember = chat.getMembers().stream()
                .filter(member -> member.getUser().getEmail().equals(email))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден"));

        if (currentMember.getNotifications() == NotificationLevel.ALL) {
            currentMember.setNotifications(NotificationLevel.NONE);
        } else {
            currentMember.setNotifications(NotificationLevel.ALL);
        }
        chatService.saveChatMember(currentMember);

        return "redirect:/direct-set?id=" + id;
    }
}