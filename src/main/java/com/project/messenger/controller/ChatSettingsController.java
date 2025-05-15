package com.project.messenger.controller;

import com.project.messenger.model.*;
import com.project.messenger.model.dto.AddMembersDTO;
import com.project.messenger.model.dto.ChatMemberDTO;
import com.project.messenger.model.dto.FileDTO;
import com.project.messenger.model.dto.MessageDTO;
import com.project.messenger.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller
public class ChatSettingsController extends BaseController{

    @Autowired
    private ChatService chatService;

    @Autowired
    private FileService fileService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserServiceInterface userService;

    private static final String DIRECT_PATH = "/direct-set";
    private static final String GROUP_PATH = "/group-set";

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
        return null;
    }

    @GetMapping("/direct-set")
    public String directSettings(
            @RequestParam(name = "id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Model model,
            Authentication auth) {
        model.addAttribute("chatType", ChatType.PERSONAL);
        User currentUser = userService.findByEmail(auth.getName());
        model.addAttribute("userEmail", auth.getName());

        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);

        Page<MessageDTO> messagePage = chatService.getMessages(id, currentUser.getId(), page, size);
        List<MessageDTO> reversedMessages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(reversedMessages);
        model.addAttribute("messages", reversedMessages);
        model.addAttribute("currentPage", messagePage.getNumber());
        model.addAttribute("totalPages", messagePage.getTotalPages());

        ChatMember currentMember = chatService.getChatMember(id, auth.getName());
        model.addAttribute("notifications", currentMember.getNotifications());
        User contact = chatService.getChatContact(id, auth.getName());
        model.addAttribute("contact", contact);

        return "direct-set";
    }

    @PostMapping("/direct/{id}/block")
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
    public String groupSettings(@RequestParam(name = "id") Long id,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                @RequestParam(name = "size", defaultValue = "10") int size,
                                Model model,
                                Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        ChatMember currentMember = chatService.getChatMember(id, currentUser.getEmail());

        model.addAttribute("chat", chat);
        model.addAttribute("currentMember", currentMember);
        Page<MessageDTO> messagePage = chatService.getMessages(id, currentUser.getId(), 0, size);
        List<MessageDTO> reversedMessages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(reversedMessages);
        model.addAttribute("messages", reversedMessages);
        model.addAttribute("currentPage", messagePage.getNumber());
        model.addAttribute("totalPages", messagePage.getTotalPages());
        model.addAttribute("chatType", ChatType.GROUP);
        model.addAttribute("userEmail", auth.getName());

        return "group-set";
    }

    @GetMapping("/group/{id}/edit")
    public String changeGroup(@PathVariable("id") Long id,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "size", defaultValue = "10") int size,
                              Model model,
                              Authentication auth) {
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        model.addAttribute("chatType", ChatType.GROUP);
        model.addAttribute("userEmail", auth.getName());
        return "group-edit";
    }

    @PostMapping("/group/{id}/edit")
    public String saveGroup(@PathVariable("id") Long id,
                            @RequestParam("name") String name,
                            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                            Authentication auth,
                            Model model) throws IOException {
        Chat chat = chatService.getChat(id, auth.getName());

        if (avatar != null && !avatar.isEmpty()) {
            String contentType = avatar.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                if (avatar.getSize() <= 5 * 1024 * 1024) { // Максимум 5 МБ
                    chatService.updateChatAvatar(id, avatar);
                } else {
                    model.addAttribute("chat", chat);
                    model.addAttribute("chatType", ChatType.GROUP);
                    model.addAttribute("error", "Файл слишком большой (максимум 5 МБ)");
                    return "group-edit";
                }
            } else {
                model.addAttribute("chat", chat);
                model.addAttribute("chatType", ChatType.GROUP);
                model.addAttribute("error", "Допустимы только изображения");
                return "group-edit";
            }
        }

        // Обновление названия
        chatService.updateChatName(id, name);
        return "redirect:/group-set?id=" + id;
    }

    //add member
    @PostMapping("/group/{id}/members/add")
    public String addMembers(@PathVariable("id") Long id, @Valid @ModelAttribute AddMembersDTO dto,
                             BindingResult result, Authentication auth, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("error", "Проверьте email участников");
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            model.addAttribute("chatType", ChatType.GROUP);
            model.addAttribute("currentMember", chatService.getChatMember(id, auth.getName()));
            return "group-set";
        }
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        ChatMember currentMember = chatService.getChatMember(id, currentUser.getEmail());

        List<String> errors = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();

        for (String email : dto.getEmails()) {
            if (email == null || email.trim().isEmpty()) {
                continue;
            }
            try {
                User user = userService.findByEmail(email.trim());
                if (chatService.existsByChatIdAndUserId(id, user.getId())) {
                    errors.add("Пользователь " + email + " уже в чате");
                } else if (userService.isBlocked(currentUser.getId(), user.getId())) {
                    errors.add("Пользователь " + email + " заблокирован");
                } else {
                    userIds.add(user.getId());
                }
            } catch (IllegalArgumentException e) {
                errors.add("Пользователь с email " + email + " не найден");
            }
        }

        if (!errors.isEmpty()) {
            model.addAttribute("error", String.join("; ", errors));
            model.addAttribute("chat", chat);
            model.addAttribute("chatType", ChatType.GROUP);
            model.addAttribute("currentMember", currentMember);
            return "group-set";
        }

        if (userIds.isEmpty()) {
            model.addAttribute("error", "Не выбрано ни одного нового участника");
            model.addAttribute("chat", chat);
            model.addAttribute("chatType", ChatType.GROUP);
            model.addAttribute("currentMember", currentMember);
            return "group-set";
        }

        chatService.addMembersToGroup(id, userIds);
        return "redirect:/group-set?id=" + id;
    }

    @PostMapping("/group/{id}/members/remove")
    public String removeMember(@PathVariable("id") Long id,
                               @RequestParam(name = "userId") Long userId,
                               @RequestParam(name = "page", value = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", value = "size", defaultValue = "10") int size,
                               @RequestParam(name = "scrollPosition", value = "scrollPosition", defaultValue = "0") int scrollPosition,
                               Authentication auth,
                               Model model) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getGroupChat(id, auth.getName());
        ChatMember currentMember = chatService.getChatMember(id, currentUser.getEmail());

        if (!currentMember.isAdmin()) {
            return addErrorToModel(model, id, chat, currentMember, page, size, "Только администратор может удалять участников!", "group-list");
        }

        if (currentUser.getId().equals(userId)) {
            return addErrorToModel(model, id, chat, currentMember, page, size, "Нельзя удалить самого себя!", "group-list");
        }

        try {
            chatService.removeMemberFromGroup(id, userId);
            return "redirect:/group-list/" + id + "?page=" + page + "&size=" + size + "&scrollPosition=" + scrollPosition;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return addErrorToModel(model, id, chat, currentMember, page, size, e.getMessage(), "group-list");
        }
    }

    @PostMapping("/group/{id}/leave")
    public String leaveGroup(@PathVariable("id") Long id, Authentication auth, Model model) {
        User currentUser = userService.findByEmail(auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        ChatMember currentMember = chatService.getChatMember(id, auth.getName());

        try {
            chatService.leaveGroup(id, currentUser.getId());
            return "redirect:/chats";
        } catch (IllegalArgumentException | IllegalStateException e) {
            return addErrorToModel(model, id, chat, currentMember, 0, 10, e.getMessage(), "group-set");
        }
    }

    private String addErrorToModel(Model model, Long chatId, Chat chat, ChatMember currentMember, int page, int size, String error, String view) {
        Pageable pageable = PageRequest.of(page, size);
        model.addAttribute("error", error);
        model.addAttribute("chat", chat);
        model.addAttribute("chatType", ChatType.GROUP);
        model.addAttribute("currentMember", currentMember);
        if (view.equals("group-list")) {
            model.addAttribute("chatId", chatId);
            model.addAttribute("membersPage", chatService.getChatMembers(chatId, currentMember.getUser().getEmail(), pageable));
            model.addAttribute("currentUserId", currentMember.getUser().getId());
            model.addAttribute("isAdmin", currentMember.isAdmin());
        }
        return view;
    }

    @GetMapping("/group/{id}/invite")
    public String inviteToGroup(@PathVariable("id") Long id,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                @RequestParam(name = "size", defaultValue = "10") int size,
                                Model model,
                                Authentication auth,
                                HttpServletRequest request) {
        try {
            User currentUser = userService.findByEmail(auth.getName());
            Chat chat = chatService.getChat(id, auth.getName());
            String link = chatService.getInviteLink(id, auth.getName(), request);
            model.addAttribute("addMembersDTO", new AddMembersDTO());
            model.addAttribute("chat", chat);
            model.addAttribute("inviteLink", link);
            model.addAttribute("chatType", ChatType.GROUP);
            model.addAttribute("userEmail", auth.getName());
            return "invite";
        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            model.addAttribute("userEmail", auth.getName());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", ChatType.GROUP);
            return "error";
        }
    }

    @PostMapping("/group/{id}/reset-invite")
    public String resetInviteLink(@PathVariable("id") Long id, Model model, Authentication auth) {
        try {
            chatService.resetInviteLink(id, auth.getName());
            return "redirect:/group/" + id + "/invite";
        } catch (IllegalArgumentException | SecurityException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", ChatType.GROUP);
            model.addAttribute("userEmail", auth.getName());
            return "error";
        }
    }

    @GetMapping("/group/join")
    public String joinGroup(@RequestParam(name = "link") String link, Authentication auth, Model model) {
        try {
            User user = userService.findByEmail(auth.getName());
            chatService.joinGroupByLink(link, user.getId());
            Chat chat = chatService.findByInviteLink(link);
            model.addAttribute("userEmail", auth.getName());
            return "redirect:/group?id=" + chat.getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", ChatType.GROUP);
            model.addAttribute("userEmail", auth.getName());
            return "error";
        }
    }

    @PostMapping("/chats/{id}/delete")
    public String deleteChat(@PathVariable("id") Long id, Authentication auth, Model model) {
        try {
            User currentUser = userService.findByEmail(auth.getName());
            Chat chat = chatService.getChat(id, auth.getName());
            if (chat.getType() == ChatType.PERSONAL || chatService.getChatMember(id, currentUser.getEmail()).isAdmin()) {
                chatService.deleteChat(id);
                return "redirect:/chats";
            }
            throw new IllegalStateException("Только администратор может удалить чат");
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("userEmail", auth.getName());
            return "error";
        }
    }

    @GetMapping("/chat/{id}/files")
    public String chatFiles(@PathVariable("id") Long id,
                            @RequestParam(name = "page", defaultValue = "0") int page,
                            @RequestParam(name = "size", defaultValue = "10") int size,
                            @RequestParam(name = "sort", defaultValue = "uploadedAt,desc") String sort,
                            @RequestParam(name = "scrollPosition", defaultValue = "0") int scrollPosition,
                            Model model,
                            Authentication auth) {
        try {
            User currentUser = userService.findByEmail(auth.getName());
            Chat chat = chatService.getChat(id, auth.getName());
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
            Page<FileDTO> files = fileService.getChatFiles(id, pageable);

            model.addAttribute("chat", chat);
            model.addAttribute("files", files.getContent());
            model.addAttribute("page", files);
            model.addAttribute("chatType", chat.getType());
            model.addAttribute("scrollPosition", scrollPosition);
            model.addAttribute("currentSort", sort);
            model.addAttribute("userEmail", auth.getName());
            return "chat-files";
        } catch (IllegalArgumentException e) {
            model.addAttribute("userEmail", auth.getName());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", chatService.getChat(id, auth.getName()).getType());
            return "error";
        } catch (Exception e) {
            model.addAttribute("userEmail", auth.getName());
            model.addAttribute("error", "Возникла непредвиденная ошибка во время загрузки файлов");
            model.addAttribute("chatType", chatService.getChat(id, auth.getName()).getType());
            return "error";
        }
    }

    @GetMapping("/chat/{id}/files/list")
    @ResponseBody
    public ResponseEntity<Page<FileDTO>> getChatFilesAjax(@PathVariable("id") Long id,
                                                          @RequestParam(name = "page") int page,
                                                          @RequestParam(name = "size") int size,
                                                          @RequestParam(name = "sort", value = "sort", defaultValue = "uploadedAt,desc") String sort,
                                                          Authentication auth) {
        try {
            String email = auth.getName();
            chatService.getChat(id, email);
            String[] sortParams = sort.split(",");
            Sort.Direction direction = sortParams[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
            Page<FileDTO> files = fileService.getChatFiles(id, pageable);
            return ResponseEntity.ok(files);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            System.err.println("Непредвиденная ошибка в getChatFilesAjax: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/group-list/{id}")
    public String groupList(@PathVariable("id") Long id,
                            @RequestParam(name = "page", value = "page", defaultValue = "0") int page,
                            @RequestParam(name = "size", value = "size", defaultValue = "10") int size,
                            @RequestParam(name = "scrollPosition", value = "scrollPosition", defaultValue = "0") int scrollPosition,
                            Model model,
                            Authentication auth) {
        try {
            String email = auth.getName();
            User currentUser = userService.findByEmail(email);
            ChatMember currentMember = chatService.getChatMember(id, email);
            Pageable pageable = PageRequest.of(page, size);
            model.addAttribute("chat", chatService.getGroupChat(id, email));
            model.addAttribute("members", chatService.getChatMembers(id, email, pageable));
            model.addAttribute("currentUserId", currentUser.getId());
            model.addAttribute("chatId", id);
            model.addAttribute("isAdmin", currentMember.isAdmin());
            model.addAttribute("chatType", ChatType.GROUP);
            model.addAttribute("scrollPosition", scrollPosition);
            model.addAttribute("userEmail", auth.getName());
            return "group-list";
        } catch (IllegalArgumentException e) {
            model.addAttribute("userEmail", auth.getName());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("chatType", ChatType.GROUP);
            return "error";
        }
    }

    @GetMapping("/group-list/{id}/members")
    @ResponseBody
    public ResponseEntity<Page<ChatMemberDTO>> getChatMembersAjax(@PathVariable("id") Long id,
                                                                  @RequestParam(name = "page") int page,
                                                                  @RequestParam(name = "size") int size,
                                                                  Authentication auth) {
        try {
            String email = auth.getName();
            Pageable pageable = PageRequest.of(page, size);
            Page<ChatMemberDTO> membersPage = chatService.getChatMembers(id, email, pageable);
            return ResponseEntity.ok(membersPage);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}