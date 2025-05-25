package com.project.messenger.controller;

import com.project.messenger.model.ChatType;
import com.project.messenger.model.dto.ChatDTO;
import com.project.messenger.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BaseController {

    @Autowired
    private ChatService chatService;

    @ModelAttribute("chatsPage")
    public Page<ChatDTO> addChatsToModel(
            @ModelAttribute("chatType") ChatType chatType,
            @ModelAttribute("chatName") String chatName,
            Authentication auth,
            @ModelAttribute("page") Integer page,
            @ModelAttribute("size") Integer size) {
        if (auth == null || auth.getName() == null) {
            return Page.empty();
        }
        Pageable pageable = PageRequest.of(
                page != null ? page : 0,
                size != null ? size : 10,
                Sort.by(Sort.Direction.DESC, "lastMessageTimestamp")
        );
        return chatService.getChatsPage(auth.getName(), chatType, chatName, pageable);
    }

    @ModelAttribute("page")
    public Integer getPage(@RequestParam(name = "page", defaultValue = "0") int page) {
        return page;
    }

    @ModelAttribute("size")
    public Integer getSize(@RequestParam(name = "size", defaultValue = "10") int size) {
        return size;
    }

    @ModelAttribute("chatType")
    public ChatType getChatType() {
        return null;
    }
}