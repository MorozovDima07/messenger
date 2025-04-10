package com.project.messenger.controller;

import com.project.messenger.model.dto.ChangePasswordDTO;
import com.project.messenger.service.UserServiceInterface;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

//@RequestMapping("/settings") //TODO Перенести сюда все настройки
@Controller
public class UserSettingsController {
    @Autowired
    private UserServiceInterface userService;

    @GetMapping("/settings/change-password") //TODO Исправить путь после переноса настроек
    public String showChangePasswordForm(Model model) {
        model.addAttribute("passwordDTO", new ChangePasswordDTO());
        return "settings/privacy/change-password";
    }

    @PostMapping("/settings/change-password") //TODO Исправить путь после переноса настроек
    public String changePassword(@Valid @ModelAttribute("passwordDTO") ChangePasswordDTO passwordDTO,
                                 BindingResult bindingResult,
                                 Authentication auth,
                                 Model model) {
        if (bindingResult.hasErrors()) { //TODO Валидация возможно не работает, не видит ошибки
            model.addAttribute("resultMessage", "Не удалось сменить пароль. Проверьте введённые данные.");
            model.addAttribute("passwordDTO", passwordDTO);

            System.out.println("Errors changePassword:"); //TODO Переделать на нормальный вывод ошибок
            bindingResult.getAllErrors().forEach(error -> {
                System.out.println("- " + error.getDefaultMessage());
            });

            return "settings/privacy/change-password";
        }

        boolean successChange = userService.changePassword(auth.getName(), passwordDTO.getCurrentPassword(), passwordDTO.getNewPassword(), passwordDTO.getConfirmPassword());

        if (successChange) {
            model.addAttribute("resultMessage", "Пароль успешно изменён.");
        } else {
            model.addAttribute("resultMessage", "Не удалось сменить пароль. Проверьте введённые данные.");
        }

        return "settings/privacy/change-password";
    }
}
