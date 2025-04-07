package com.project.messenger.service;

import com.project.messenger.model.File;
import com.project.messenger.model.Message;
import com.project.messenger.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    private static final String UPLOAD_DIR = "uploads/";

    @Transactional
    public File uploadFile(MultipartFile file, Message message) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(UPLOAD_DIR, fileName);
        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при загрузке файла", e);
        }

        File fileEntity = new File();
        fileEntity.setMessage(message);
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(filePath.toString());
        fileEntity.setUploadedAt(LocalDateTime.now());
        fileEntity = fileRepository.save(fileEntity);

        // Добавляем файл в список files сообщения
        message.getFiles().add(fileEntity);
        return fileEntity;
    }

    public File getFile(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));
    }

    public List<File> getChatFiles(Long chatId) {
        return fileRepository.findByMessageChatId(chatId);
    }
}