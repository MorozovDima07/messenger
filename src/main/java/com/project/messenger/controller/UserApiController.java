package com.project.messenger.controller;

import com.project.messenger.service.UserServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserApiController {
    @Autowired
    private UserServiceInterface userService;

    @GetMapping("/search")
    public List<String> searchUsers(@RequestParam(name = "query") String query,
                                    @RequestParam(name = "chatType", value = "chatType", required = false) String chatType,
                                    Authentication auth) {
        if ("group".equals(chatType)) {
            return userService.searchUsersWithDirectChats(auth.getName(), query);
        }
        return userService.searchUsersByEmail(query);
    }
}