package com.project.messenger.controller;

import com.project.messenger.model.*;
import com.project.messenger.model.dto.ChatDTO;
import com.project.messenger.model.dto.DirectChatDTO;
import com.project.messenger.model.dto.GroupChatDTO;
import com.project.messenger.model.dto.MessageDTO;
import com.project.messenger.service.ChatService;
import com.project.messenger.service.MessageService;
import com.project.messenger.service.UserServiceInterface;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@Controller
public class ChatController extends BaseController {

    private static final String DIRECT_PATH = "/direct";
    private static final String GROUP_PATH = "/group";
    private static final String CHATS_PATH_PREFIX = "/chats/";
    private static final String CHATS_LOAD_PATH = "/chats/load";

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserServiceInterface userService;

    @ModelAttribute("chatType")
    public ChatType getChatType(
            HttpServletRequest request,
            Authentication auth,
            @RequestParam(name = "chatType", required = false) ChatType chatTypeParam,
            @PathVariable(value = "id", required = false) Long chatId) {
        String uri = request.getRequestURI();

        if (uri.endsWith(DIRECT_PATH)) {
            return ChatType.PERSONAL;
        }
        if (uri.endsWith(GROUP_PATH)) {
            return ChatType.GROUP;
        }
        if (uri.startsWith(CHATS_PATH_PREFIX) && !uri.equals(CHATS_LOAD_PATH) && chatId != null) {
            try {
                if (auth != null && auth.getName() != null) {
                    Chat chat = chatService.getChat(chatId, auth.getName());
                    return chat.getType();
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (uri.equals(CHATS_LOAD_PATH)) {
            return chatTypeParam;
        }
        return null;
    }

    @ModelAttribute("chatType")
    public ChatType getChatType(Model model) {
        return (ChatType) model.getAttribute("chatType");
    }

    @GetMapping("/chats")
    public String chats(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        model.addAttribute("userEmail", auth.getName());
        return "chats";
    }

    @GetMapping("/direct")
    public String direct(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "chatType", defaultValue = "PERSONAL") ChatType chatType,
            Model model,
            Authentication auth) {
        model.addAttribute("chatType", ChatType.PERSONAL);
        User currentUser = userService.findByEmail(auth.getName());
        model.addAttribute("userEmail", auth.getName());
        if (id != null) {
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            Page<MessageDTO> messagePage = chatService.getMessages(id, currentUser.getId(), 0, size);
            List<MessageDTO> reversedMessages = new ArrayList<>(messagePage.getContent());
            Collections.reverse(reversedMessages);
            model.addAttribute("messages", reversedMessages);
            model.addAttribute("currentPage", messagePage.getNumber());
            model.addAttribute("totalPages", messagePage.getTotalPages());
        }
        return "direct";
    }

    @GetMapping("/chat/{chatId}/messages/load")
    @ResponseBody
    public Page<MessageDTO> loadMoreMessages(
            @PathVariable("chatId") Long chatId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Page<MessageDTO> messages = chatService.getMessages(chatId, currentUser.getId(), page, size);
        return messages;
    }

    @GetMapping("/group")
    public String group(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        model.addAttribute("chatType", ChatType.GROUP);
        User currentUser = userService.findByEmail(auth.getName());
        model.addAttribute("userEmail", auth.getName());
        if (id != null) {
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            Page<MessageDTO> messagePage = chatService.getMessages(id, currentUser.getId(), 0, size);
            List<MessageDTO> reversedMessages = new ArrayList<>(messagePage.getContent());
            Collections.reverse(reversedMessages);
            model.addAttribute("messages", reversedMessages);
            model.addAttribute("currentPage", messagePage.getNumber());
            model.addAttribute("totalPages", messagePage.getTotalPages());
        }
        model.addAttribute("currentUserId", currentUser.getId());
        return "group";
    }

    @GetMapping("/chats/new")
    public String newChat(Model model, Authentication auth) {
        model.addAttribute("DirectChatDTO", new DirectChatDTO());
        model.addAttribute("GroupChatDTO", new GroupChatDTO());
        model.addAttribute("userEmail", auth.getName());
        return "new-chat";
    }

    @PostMapping("/chats/new/direct")
    public String createDirectChat(@Valid @ModelAttribute DirectChatDTO dto,
                                   BindingResult result,
                                   Authentication auth,
                                   Model model) {
        if (result.hasErrors()) {
            model.addAttribute("error", result.getFieldError("email").getDefaultMessage());
            model.addAttribute("userEmail", auth.getName());
            return "new-chat";
        }

        User currentUser = userService.findByEmail(auth.getName());
        try {
            User user2 = userService.findByEmail(dto.getEmail());
            if (user2.getId().equals(currentUser.getId())) {
                model.addAttribute("error", "Нельзя создать чат с самим собой!");
            } else if (userService.isBlocked(currentUser.getId(), user2.getId())) {
                model.addAttribute("error", "Этот пользователь заблокирован!");
            } else {
                Chat chat = chatService.createDirectChat(currentUser.getId(), user2.getId());
                return "redirect:/chats/" + chat.getId();
            }
        } catch (Exception e) {
            model.addAttribute("error", "Пользователь с email " + dto.getEmail() + " не найден!");
        }
        model.addAttribute("DirectChatDTO", new DirectChatDTO());
        model.addAttribute("GroupChatDTO", new GroupChatDTO());
        model.addAttribute("userEmail", auth.getName());
        return "new-chat";
    }

    @PostMapping("/chats/new/group")
    public String createGroupChat(@Valid @ModelAttribute GroupChatDTO dto,
                                  BindingResult result,
                                  Authentication auth,
                                  Model model) {
        if (result.hasErrors()) {
            model.addAttribute("DirectChatDTO", new DirectChatDTO());
            model.addAttribute("GroupChatDTO", new GroupChatDTO());
            model.addAttribute("error", "Проверьте название группы и email участников");
            model.addAttribute("userEmail", auth.getName());
            return "new-chat";
        }

        User currentUser = userService.findByEmail(auth.getName());
        List<Long> memberIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String email : dto.getEmails()) {
            if (email == null || email.trim().isEmpty()) {
                continue;
            }
            try {
                User user = userService.findByEmail(email.trim());
                if (!user.getId().equals(currentUser.getId()) && !memberIds.contains(user.getId())) {
                    if (userService.isBlocked(currentUser.getId(), user.getId())) {
                        errors.add("Пользователь " + email + " заблокирован");
                    } else {
                        memberIds.add(user.getId());
                    }
                }
            } catch (IllegalArgumentException e) {
                errors.add("Пользователь с email " + email + " не найден");
            }
        }

        if (!errors.isEmpty()) {
            model.addAttribute("DirectChatDTO", new DirectChatDTO());
            model.addAttribute("GroupChatDTO", new GroupChatDTO());
            model.addAttribute("userEmail", auth.getName());
            model.addAttribute("error", String.join("; ", errors));
            return "new-chat";
        }

        if (memberIds.isEmpty()) {
            model.addAttribute("DirectChatDTO", new DirectChatDTO());
            model.addAttribute("GroupChatDTO", new GroupChatDTO());
            model.addAttribute("userEmail", auth.getName());
            model.addAttribute("error", "Групповой чат должен содержать хотя бы одного участника помимо вас!");
            return "new-chat";
        }

        try {
            Chat chat = chatService.createGroupChat(dto.getName(), currentUser.getId(), memberIds, null);
            return "redirect:/chats/" + chat.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("DirectChatDTO", new DirectChatDTO());
            model.addAttribute("GroupChatDTO", new GroupChatDTO());
            model.addAttribute("userEmail", auth.getName());
            return "new-chat";
        }
    }


    @GetMapping("/chats/{id}")
    public String viewChat(@PathVariable("id") Long id,
                           @RequestParam(name = "page", defaultValue = "0") int page,
                           @RequestParam(name = "size", defaultValue = "10") int size,
                           Model model,
                           Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        Page<MessageDTO> messagePage = chatService.getMessages(id, currentUser.getId(), 0, size);
        List<MessageDTO> reversedMessages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(reversedMessages);
        model.addAttribute("messages", reversedMessages);
        model.addAttribute("currentPage", messagePage.getNumber());
        model.addAttribute("totalPages", messagePage.getTotalPages());
        model.addAttribute("chatType", chat.getType());
        messageService.markMessagesAsRead(id, auth.getName());
        model.addAttribute("userEmail", auth.getName());

        if (chat.getType() == ChatType.PERSONAL) {
            return "direct";
        } else {
            return "group";
        }
    }

    @GetMapping("/chats/load")
    @ResponseBody
    public Page<ChatDTO> loadChats(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "chatType", required = false) ChatType chatType,
            Authentication auth) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessageTimestamp"));
        Page<ChatDTO> chatsPage = chatService.getChatsPage(auth.getName(), chatType, pageable);
        System.out.println("Loaded " + chatsPage.getNumberOfElements() + " chats for page " + page);
        return chatsPage;
    }

    @GetMapping("/chats/search")
    public String searchChats(@RequestParam("chatName") String chatName,
                              @RequestParam("chatType") String chatType,
                              Model model, Authentication auth,
                              HttpServletRequest request) {
        ChatType type = null;

        if (!"ALL".equals(chatType)) {
            type = ChatType.valueOf(chatType);
        }

        List<ChatDTO> chats = chatService.searchChatsByType(auth.getName(), chatName, type);
        model.addAttribute("chats", chats);
        model.addAttribute("chatName", chatName);

        return "fragments/chat-list :: chat-list-items";
    }
}