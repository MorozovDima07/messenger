package com.project.messenger.model.dto;

import lombok.Data;

@Data
public class BlockedUserDTO {
    private Long id;
    private String username;
    private String email;
    private boolean emailVisible;
    private String avatarPath;
}
