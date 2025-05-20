package com.project.messenger.exception;

public class ChatNotFoundException extends MessengerException {
    public ChatNotFoundException(Long chatId) {
        super("Чат с ID " + chatId + " не найден");
    }
}