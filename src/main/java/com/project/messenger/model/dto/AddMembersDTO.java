package com.project.messenger.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddMembersDTO {
    @NotEmpty(message = "Добавьте хотя бы одного участника")
    private List<@Email(message = "Некорректный email") String> emails;
}