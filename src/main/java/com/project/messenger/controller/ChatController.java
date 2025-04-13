package com.project.messenger.controller;

import com.project.messenger.model.Chat;
import com.project.messenger.model.ChatType;
import com.project.messenger.model.Message;
import com.project.messenger.model.User;
import com.project.messenger.model.dto.ChatDTO;
import com.project.messenger.model.dto.MessageDTO;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.MessageService;
import com.project.messenger.service.UserService;
import com.project.messenger.service.UserServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

//@RequestMapping("/chats") //TODO Добавить это, чтобы в начале пути писалось /chats!
@Controller
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserServiceInterface userService;


    @GetMapping("/chats/search")
    public String searchChats(@RequestParam("chatName") String chatName, Model model, Authentication auth) {
        List<ChatDTO> chats = chatService.searchChatsByUserAndChatName(auth.getName(), chatName);
        model.addAttribute("chats", chats);
        model.addAttribute("chatName", chatName);
        return "chats";
    }

    @GetMapping("/chats")
    public String chats(Model model, Authentication auth) {
//        List<ChatDTO> chats;
//        String userName = auth.getName();

//        if (chatName == null || chatName.isBlank()) {
//            chats = chatService.getUserChats(userName);
//        } else {
//            chats = chatService.searchChatsByUserAndChatName(userName, chatName);
//        }

//        List<ChatDTO> chats = (chatName == null || chatName.isBlank())
//                ? chatService.getUserChats(auth.getName())
//                : chatService.searchChatsByUserAndChatName(userName, chatName);

        List<ChatDTO> chats = chatService.getUserChats(auth.getName());
        model.addAttribute("chats", chats);
        return "chats";
    }

    @GetMapping("/direct")
    public String direct(@RequestParam(name = "id", required = false) Long id, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        if (id != null) {
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            model.addAttribute("messages", chatService.getMessages(id, currentUser.getId()));
        }
        model.addAttribute("chats", chatService.getDirectChats(auth.getName()));
        return "direct";
    }

    @GetMapping("/group")
    public String group(@RequestParam(name = "id", required = false) Long id, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        if (id != null) {
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            model.addAttribute("messages", chatService.getMessages(id, currentUser.getId()));
        }
        model.addAttribute("chats", chatService.getGroupChats(auth.getName()));
        model.addAttribute("currentUserId", currentUser.getId());
        return "group";
    }

    @GetMapping("/chats/new")
    public String newChat(Model model, Authentication auth) {
        model.addAttribute("chats", chatService.getUserChats(auth.getName()));
        return "new-chat";
    }

    @PostMapping("/chats/new/direct")
    public String createDirectChat(@RequestParam("email") String email,
                                   Authentication auth,
                                   Model model) {
        User currentUser = userService.findByEmail(auth.getName());
        try {
            User user2 = userService.findByEmail(email);
            if (user2.getId().equals(currentUser.getId())) {
                model.addAttribute("error", "Нельзя создать чат с самим собой!");
                model.addAttribute("chats", chatService.getUserChats(auth.getName()));
                return "new-chat";
            }
            if (userService.isBlocked(currentUser.getId(), user2.getId())) {
                model.addAttribute("error", "Этот пользователь заблокирован!");
                model.addAttribute("chats", chatService.getUserChats(auth.getName()));
                return "new-chat";
            }
            Chat chat = chatService.createDirectChat(currentUser.getId(), user2.getId());
            return "redirect:/chats/" + chat.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Пользователь с таким email не найден!");
        }
        model.addAttribute("chats", chatService.getUserChats(auth.getName()));
        return "new-chat";
    }

    @PostMapping("/chats/new/group")
    public String createGroupChat(@RequestParam("name") String name,
                                  @RequestParam("emails") List<String> emails,
                                  Authentication auth,
                                  Model model) {
        User currentUser = userService.findByEmail(auth.getName());
        List<Long> memberIds = new ArrayList<>();
        memberIds.add(currentUser.getId());

        for (String email : emails) {
            if (email == null) {
                continue;
            }
            try {
                User user = userService.findByEmail(email);
                if (!user.getId().equals(currentUser.getId()) && !memberIds.contains(user.getId())) {
                    if (userService.isBlocked(currentUser.getId(), user.getId())) {
                        model.addAttribute("error", "Один из пользователей заблокирован: " + email);
                        model.addAttribute("chats", chatService.getUserChats(auth.getName()));
                        return "new-chat";
                    }
                    memberIds.add(user.getId());
                }
            } catch (IllegalArgumentException e) {
                model.addAttribute("error", "Пользователь с email " + email + " не найден!");
                model.addAttribute("chats", chatService.getUserChats(auth.getName()));
                return "new-chat";
            }
        }

        if (memberIds.size() < 2) {
            model.addAttribute("error", "Групповой чат должен содержать хотя бы одного участника помимо вас!");
            model.addAttribute("chats", chatService.getUserChats(auth.getName()));
            return "new-chat";
        }

        Chat chat = chatService.createGroupChat(name, currentUser.getId(), memberIds);
        return "redirect:/chats/" + chat.getId();
    }

    @GetMapping("/chats/{id}")
    public String viewChat(@PathVariable("id") Long id, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        List<MessageDTO> messages = chatService.getMessages(id, currentUser.getId());
        model.addAttribute("messages", messages);

        // Фильтруем чаты по типу текущего чата
        List<ChatDTO> allChats = chatService.getUserChats(auth.getName());
        List<ChatDTO> filteredChats = allChats.stream()
                .filter(c -> c.getType().equals(chat.getType()))
                .collect(Collectors.toList());
        model.addAttribute("chats", filteredChats);

        messageService.markMessagesAsRead(id, currentUser.getId());

        if (chat.getType() == ChatType.PERSONAL) {
            return "direct";
        } else {
            return "group";
        }
    }
}