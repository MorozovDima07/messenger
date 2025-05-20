package com.project.messenger.exception;

public class UserNotFoundException extends MessengerException {
    public UserNotFoundException(String email) {
        super("Пользователь с email " + email + " не найден");
    }

    public UserNotFoundException(Long userId) {
        super("Пользователь с ID " + userId + " не найден");
    }
}