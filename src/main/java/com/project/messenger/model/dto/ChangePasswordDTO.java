package com.project.messenger.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDTO {
    @NotBlank(message = "Введите текущий пароль")
    private String currentPassword;

    @NotBlank(message = "Введите новый пароль")
    @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
    private String newPassword;

    @NotBlank(message = "Подтвердите новый пароль")
    private String confirmPassword;
}
