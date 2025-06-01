package com.project.messenger.controller;

import com.project.messenger.exception.InvalidChatOperationException;
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
        return viewChatSettings(id, size, model, auth);
    }

    @GetMapping("/group-set")
    public String groupSettings(@RequestParam(name = "id") Long id,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                @RequestParam(name = "size", defaultValue = "10") int size,
                                Model model,
                                Authentication auth) {
        return viewChatSettings(id, size, model, auth);
    }

    private String viewChatSettings(Long id, int size, Model model, Authentication auth) {
        User currentUser = userService.findByEmail(auth.getName());
        model.addAttribute("userEmail", auth.getName());
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        Page<MessageDTO> messagePage = chatService.getMessages(id, currentUser.getId(), 0, size);
        List<MessageDTO> reversedMessages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(reversedMessages);
        model.addAttribute("messages", reversedMessages);
        ChatMember currentMember = chatService.getChatMember(id, auth.getName());
        if(chat.getType() == ChatType.PERSONAL){
            model.addAttribute("notifications", currentMember.getNotifications());
            User contact = chatService.getChatContact(id, auth.getName());
            model.addAttribute("contact", contact);
        } else if (chat.getType() == ChatType.GROUP){
            model.addAttribute("currentMember", currentMember);
        }
        return chat.getType() == ChatType.PERSONAL ? "direct-set" : "group-set";
    }

    @PostMapping("/direct/{id}/block")
    public String blockUser(@PathVariable("id") Long id, Authentication auth) {
        userService.blockUser(auth.getName(), id);
        return "redirect:/chats";
    }

    @GetMapping("/group/{id}/edit")
    public String changeGroup(@PathVariable("id") Long id,
                              @RequestParam(name = "page", defaultValue = "0") int page,
                              @RequestParam(name = "size", defaultValue = "10") int size,
                              Model model,
                              Authentication auth) {
        Chat chat = chatService.getChat(id, auth.getName());
        model.addAttribute("chat", chat);
        model.addAttribute("userEmail", auth.getName());
        return "group-edit";
    }

    @PostMapping("/group/{id}/edit")
    public String saveGroup(@PathVariable("id") Long id,
                            @RequestParam("name") String name,
                            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                            Authentication auth,
                            Model model) throws IOException {
        if (avatar != null && !avatar.isEmpty()) {
            String contentType = avatar.getContentType();
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            if (contentType != null && contentType.startsWith("image/")) {
                if (avatar.getSize() <= 5 * 1024 * 1024) {
                    chatService.updateChatAvatar(id, avatar, auth.getName());
                } else {
                    model.addAttribute("error", "Файл слишком большой (максимум 5 МБ)");
                    return "group-edit";
                }
            } else {
                model.addAttribute("error", "Допустимы только изображения");
                return "group-edit";
            }
        }

        chatService.updateChatName(id, name, auth.getName());
        return "redirect:/group-set?id=" + id;
    }

    //add member
    @PostMapping("/group/{id}/members/add")
    public String addMembers(@PathVariable("id") Long id, @Valid @ModelAttribute AddMembersDTO dto,
                             BindingResult result, Authentication auth, Model model) {
        if (result.hasErrors()) {
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            model.addAttribute("currentMember", chatService.getChatMember(id, auth.getName()));
            model.addAttribute("error", "Проверьте email участников");
            return "group-set";
        }

        List<String> errors = chatService.addMembersToGroup(id, dto.getEmails(), auth.getName());

        if (!errors.isEmpty()) {
            Chat chat = chatService.getChat(id, auth.getName());
            model.addAttribute("chat", chat);
            model.addAttribute("currentMember", chatService.getChatMember(id, auth.getName()));
            model.addAttribute("error", String.join("; ", errors));
            return "group-set";
        }

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
        try {
            chatService.removeMemberFromGroup(id, userId, auth.getName());
            return "redirect:/group-list/" + id + "?page=" + page + "&size=" + size + "&scrollPosition=" + scrollPosition;
        } catch (InvalidChatOperationException e) {
            return addErrorToModel(model, id, auth.getName(), page, size, e.getMessage(), "group-list");
        }
    }

    @PostMapping("/group/{id}/leave")
    public String leaveGroup(@PathVariable("id") Long id, Authentication auth, Model model) {
        try {
            chatService.leaveGroup(id, auth.getName());
            return "redirect:/chats";
        } catch (InvalidChatOperationException e) {
            return addErrorToModel(model, id, auth.getName(), 0, 10, e.getMessage(), "group-set");
        }
    }

    private String addErrorToModel(Model model, Long chatId, String userEmail, int page, int size, String error, String view) {
        model.addAttribute("error", error);
        Chat chat = chatService.getChat(chatId, userEmail);
        ChatMember currentMember = chatService.getChatMember(chatId, userEmail);
        model.addAttribute("chat", chat);
        model.addAttribute("currentMember", currentMember);
        if (view.equals("group-list")) {
            Pageable pageable = PageRequest.of(page, size);
            model.addAttribute("chatId", chatId);
            model.addAttribute("membersPage", chatService.getChatMembers(chatId, currentMember.getUser().getEmail(), pageable));
            model.addAttribute("currentUserId", currentMember.getUser().getId());
            model.addAttribute("isAdmin", currentMember.isAdmin());
        }
        return view;
    }

    @GetMapping("/group-list/{id}")
    public String groupList(@PathVariable("id") Long id,
                            @RequestParam(name = "page", value = "page", defaultValue = "0") int page,
                            @RequestParam(name = "size", value = "size", defaultValue = "10") int size,
                            @RequestParam(name = "scrollPosition", value = "scrollPosition", defaultValue = "0") int scrollPosition,
                            Model model,
                            Authentication auth) {
        String email = auth.getName();
        User currentUser = userService.findByEmail(email);
        ChatMember currentMember = chatService.getChatMember(id, email);
        Pageable pageable = PageRequest.of(page, size);
        model.addAttribute("chat", chatService.getGroupChat(id, email));
        model.addAttribute("members", chatService.getChatMembers(id, email, pageable));
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("chatId", id);
        model.addAttribute("isAdmin", currentMember.isAdmin());
        model.addAttribute("scrollPosition", scrollPosition);
        model.addAttribute("userEmail", auth.getName());
        return "group-list";
    }

    @GetMapping("/group-list/{id}/members")
    @ResponseBody
    public ResponseEntity<Page<ChatMemberDTO>> getChatMembersAjax(@PathVariable("id") Long id,
                                                                  @RequestParam(name = "page") int page,
                                                                  @RequestParam(name = "size") int size,
                                                                  Authentication auth) {
        Page<ChatMemberDTO> membersPage = chatService.getChatMembers(id, auth.getName(), PageRequest.of(page, size));
        return ResponseEntity.ok(membersPage);
    }

    @GetMapping("/group/{id}/invite")
    public String inviteToGroup(@PathVariable("id") Long id,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                @RequestParam(name = "size", defaultValue = "10") int size,
                                Model model,
                                Authentication auth,
                                HttpServletRequest request) {
        Chat chat = chatService.getChat(id, auth.getName());
        String link = chatService.getInviteLink(id, auth.getName(), request);
        model.addAttribute("addMembersDTO", new AddMembersDTO());
        model.addAttribute("chat", chat);
        model.addAttribute("inviteLink", link);
        model.addAttribute("userEmail", auth.getName());
        return "invite";
    }

    @GetMapping("/group/join")
    public String joinGroup(@RequestParam(name = "link") String link, Authentication auth, Model model) {
        Chat chat = chatService.joinGroupByLinkAndGetChat(link, auth.getName());
        return "redirect:/group?id=" + chat.getId();
    }

    @PostMapping("/chats/{id}/delete")
    public String deleteChat(@PathVariable("id") Long id, Authentication auth, Model model) {
        chatService.deleteChat(id, auth.getName());
        return "redirect:/chats";
    }

    @GetMapping("/chat/{id}/files")
    public String chatFiles(@PathVariable("id") Long id,
                            @RequestParam(name = "page", defaultValue = "0") int page,
                            @RequestParam(name = "size", defaultValue = "10") int size,
                            @RequestParam(name = "sort", defaultValue = "uploadedAt,desc") String sort,
                            @RequestParam(name = "scrollPosition", defaultValue = "0") int scrollPosition,
                            Model model,
                            Authentication auth) {
        Chat chat = chatService.getChat(id, auth.getName());
        Page<FileDTO> files = fileService.getChatFiles(id, sort, page, size);

        model.addAttribute("chat", chat);
        model.addAttribute("files", files.getContent());
        model.addAttribute("page", files);
        model.addAttribute("chatType", chat.getType());
        model.addAttribute("scrollPosition", scrollPosition);
        model.addAttribute("currentSort", sort);
        model.addAttribute("userEmail", auth.getName());
        return "chat-files";
    }

    @GetMapping("/chat/{id}/files/list")
    @ResponseBody
    public ResponseEntity<Page<FileDTO>> getChatFilesAjax(@PathVariable("id") Long id,
                                                          @RequestParam(name = "page") int page,
                                                          @RequestParam(name = "size") int size,
                                                          @RequestParam(name = "sort", value = "sort", defaultValue = "uploadedAt,desc") String sort,
                                                          Authentication auth) {
        chatService.getChat(id, auth.getName());
        Page<FileDTO> files = fileService.getChatFiles(id, sort, page, size);
        return ResponseEntity.ok(files);
    }
}