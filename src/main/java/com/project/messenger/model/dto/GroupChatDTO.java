package com.project.messenger.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class GroupChatDTO {
    @NotBlank(message = "Название группы обязательно")
    @Size(min = 1, max = 100, message = "Название должно быть от 1 до 100 символов")
    private String name;

    @NotEmpty(message = "Добавьте хотя бы одного участника")
    private List<@Email(message = "Некорректный email") String> emails;
}