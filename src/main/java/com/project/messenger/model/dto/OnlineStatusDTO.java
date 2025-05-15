package com.project.messenger.model.dto;

import lombok.Data;

@Data
public class OnlineStatusDTO {
    private String email;
    private boolean isOnline;

    public boolean isOnline() {
        return isOnline;
    }
}