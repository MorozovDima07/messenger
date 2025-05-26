package com.project.messenger.controller;

import com.project.messenger.exception.InvalidChatOperationException;
import com.project.messenger.model.*;
import com.project.messenger.model.dto.ChatDTO;
import com.project.messenger.model.dto.DirectChatDTO;
import com.project.messenger.model.dto.GroupChatDTO;
import com.project.messenger.model.dto.MessageDTO;
import com.project.messenger.service.ChatService;
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
    private static final String CHATS_LOAD_PATH = "/chats/load";

    @Autowired
    private ChatService chatService;

    @Autowired
    private UserServiceInterface userService;

    @ModelAttribute("chatType")
    public ChatType getChatType(HttpServletRequest request, @RequestParam(name = "chatType", required = false) ChatType chatTypeParam) {
        String uri = request.getRequestURI();
        if (uri.endsWith(DIRECT_PATH)) {
            return ChatType.PERSONAL;
        } else if (uri.endsWith(GROUP_PATH)) {
            return ChatType.GROUP;
        } else if (uri.equals(CHATS_LOAD_PATH)) {
            return chatTypeParam;
        }
        return null;
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
            Model model,
            Authentication auth) {
        viewChat(id, page, size, model, auth);
        return "direct";
    }

    @GetMapping("/group")
    public String group(
            @RequestParam(name = "id", required = false) Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        viewChat(id, page, size, model, auth);
        return "group";
    }

    private void viewChat(Long chatId, int page, int size, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        model.addAttribute("userEmail", auth.getName());
        if (chatId != null) {
            Chat chat = chatService.getChat(chatId, auth.getName());
            model.addAttribute("chat", chat);
            Page<MessageDTO> messagePage = chatService.getMessages(chatId, currentUser.getId(), 0, size);
            List<MessageDTO> reversedMessages = new ArrayList<>(messagePage.getContent());
            Collections.reverse(reversedMessages);
            model.addAttribute("messages", reversedMessages);
        }
    }

    @GetMapping("/chats/new")
    public String newChat(Model model, Authentication auth) {
        model.addAttribute("DirectChatDTO", new DirectChatDTO());
        model.addAttribute("GroupChatDTO", new GroupChatDTO());
        model.addAttribute("userEmail", auth.getName());
        return "new-chat";
    }

    @PostMapping("/new/direct")
    public String createDirectChat(@Valid @ModelAttribute DirectChatDTO dto,
                                   BindingResult result,
                                   Authentication auth,
                                   Model model) {
        if (result.hasErrors()) {
            model.addAttribute("error", result.getFieldError("email") != null
                    ? result.getFieldError("email").getDefaultMessage()
                    : "Ошибка валидации");
            model.addAttribute("userEmail", auth.getName());
            return prepareNewChatModel(model);
        }

        try {
            Chat chat = chatService.createDirectChat(auth.getName(), dto.getEmail());
            return "redirect:/chats/" + chat.getId();
        } catch (InvalidChatOperationException e) {
            model.addAttribute("error", e.getMessage());
            return prepareNewChatModel(model);
        }
    }

    @PostMapping("/new/group")
    public String createGroupChat(@Valid @ModelAttribute GroupChatDTO dto,
                                  BindingResult result,
                                  Authentication auth,
                                  Model model) {
        if (result.hasErrors()) {
            model.addAttribute("error", "Проверьте название группы и email участников");
            return prepareNewChatModel(model);
        }

        try {
            Chat chat = chatService.createGroupChat(dto.getName(), auth.getName(), dto.getEmails(), null);
            return "redirect:/chats/" + chat.getId();
        } catch (InvalidChatOperationException e) {
            model.addAttribute("error", e.getMessage());
            return prepareNewChatModel(model);
        }
    }

    private String prepareNewChatModel(Model model) {
        model.addAttribute("DirectChatDTO", new DirectChatDTO());
        model.addAttribute("GroupChatDTO", new GroupChatDTO());
        model.addAttribute("userEmail", model.containsAttribute("userEmail")
                ? model.getAttribute("userEmail")
                : "");
        return "new-chat";
    }

    @GetMapping("/chats/load")
    @ResponseBody
    public Page<ChatDTO> loadChats(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "chatType", required = false) ChatType chatType,
            @RequestParam(name = "chatName", required = false) String chatName,
            Authentication auth) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessageTimestamp"));
        return chatService.getChatsPage(auth.getName(), chatType, chatName, pageable);
    }
}