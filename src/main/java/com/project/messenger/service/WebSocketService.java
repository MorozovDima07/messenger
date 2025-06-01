package com.project.messenger.service;

import com.project.messenger.model.dto.OnlineStatusDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketService {
    private final ConcurrentHashMap<String, Long> activeUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> activeChats = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;
    private static final long INACTIVITY_THRESHOLD = 30 * 60 * 1000;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void handleConnect(String email) {
        activeUsers.put(email, System.currentTimeMillis());
        broadcastOnlineStatus(email, true);
    }

    public void handleDisconnect(String email) {
        activeUsers.remove(email);
        activeChats.remove(email);
        broadcastOnlineStatus(email, false);
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupInactiveUsers() {
        long currentTime = System.currentTimeMillis();
        activeUsers.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > INACTIVITY_THRESHOLD) {
                String email = entry.getKey();
                activeChats.remove(email);
                broadcastOnlineStatus(email, false);
                return true;
            }
            return false;
        });
    }

    public void joinChat(String email, Long chatId) {
        activeChats.computeIfAbsent(email, k -> new HashSet<>()).add(chatId);
        activeUsers.put(email, System.currentTimeMillis());
    }

    public void leaveChat(String email, Long chatId) {
        Set<Long> userChats = activeChats.get(email);
        if (userChats != null) {
            userChats.remove(chatId);
            if (userChats.isEmpty()) {
                activeChats.remove(email);
            }
        }
        activeUsers.put(email, System.currentTimeMillis());
    }

    public void updateHeartbeat(String email) {
        activeUsers.put(email, System.currentTimeMillis());
    }

    public Set<Long> getActiveChats(String email) {
        return activeChats.getOrDefault(email, Collections.emptySet());
    }

    public List<String> getOnlineUsers() {
        return new ArrayList<>(activeUsers.keySet());
    }

    private void broadcastOnlineStatus(String email, boolean isOnline) {
        OnlineStatusDTO statusDTO = new OnlineStatusDTO();
        statusDTO.setEmail(email);
        statusDTO.setOnline(isOnline);
        messagingTemplate.convertAndSend("/topic/online-status", statusDTO);
    }
}