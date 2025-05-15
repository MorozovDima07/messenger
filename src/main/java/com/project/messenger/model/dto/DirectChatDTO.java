package com.project.messenger.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DirectChatDTO {
    @NotBlank(message = "Email не может быть пустым")
    @Email(message = "Введите корректный email")
    private String email;
}