package com.project.messenger.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ChatNotFoundException.class)
    public Object handleChatNotFoundException(ChatNotFoundException ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(UserNotFoundException.class)
    public Object handleUserNotFoundException(UserNotFoundException ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(InvalidChatOperationException.class)
    public Object handleInvalidChatOperationException(InvalidChatOperationException ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(BindException.class)
    public String handleValidationException(BindException ex, Model model) {
        model.addAttribute("error", "Ошибка валидации: " + ex.getAllErrors().get(0).getDefaultMessage());
        return "error";
    }

    @ExceptionHandler(FileUploadException.class)
    public Object handleFileUploadException(FileUploadException ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(FileDownloadException.class)
    public Object handleFileDownloadxception(FileUploadException ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(MessageNotFoundException.class)
    public Object handleMessageNotFoundException(MessageNotFoundException ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        }
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneralException(Exception ex, HttpServletRequest request, Model model) {
        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Произошла ошибка на сервере: " + ex.getMessage());
        }
        model.addAttribute("error", "Произошла непредвиденная ошибка: " + ex.getMessage());
        return "error";
    }
}