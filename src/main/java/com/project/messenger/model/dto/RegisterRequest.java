package com.project.messenger.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email обязателен")
    @Email(message = "Неверный формат email")
    @Size(max = 255, message = "Email: максимум 255 символов")
    private String email;

    //@Pattern(regexp = "(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[A-Za-z\\d@$!%*?&]{8,}",
    //         message = "Пароль: минимум 8 символов, включая буквы разного регистра и цифры")
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, max = 50, message = "Пароль: 8-50 символов")
    private String password;

    @NotBlank(message = "Имя обязательно")
    @Size(min = 2, max = 50, message = "Имя: 2-50 символов")
    @Pattern(regexp = "[A-Za-zА-Яа-яЁё\\s-]{2,}", message = "Имя: только буквы, пробелы или дефисы")
    private String username;
}