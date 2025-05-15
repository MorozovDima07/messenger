package com.project.messenger.service;

import com.project.messenger.model.File;
import com.project.messenger.model.Message;
import com.project.messenger.model.dto.FileDTO;
import com.project.messenger.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    private static final String UPLOAD_DIR = "uploads/";

    @Transactional
    public File uploadFile(MultipartFile file, Message message) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Файл превышает лимит в 10 МБ");
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(UPLOAD_DIR, fileName);

        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath);
            System.out.println("Файл сохранён: " + filePath);
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении файла на диск: " + file.getOriginalFilename() + ": " + e.getMessage());
            throw new IOException("Ошибка при загрузке файла: " + file.getOriginalFilename(), e);
        }

        File fileEntity = new File();
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(filePath.toString());
        fileEntity.setFileSize(file.getSize());
        fileEntity.setMessage(message);
        fileEntity.setUploadedAt(LocalDateTime.now());
        try {
            String contentType = Files.probeContentType(filePath);
            fileEntity.setContentType(contentType != null ? contentType : file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        } catch (IOException e) {
            System.err.println("Не удалось определить contentType для файла: " + file.getOriginalFilename() + ": " + e.getMessage());
            fileEntity.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        }

        try {
            fileEntity = fileRepository.save(fileEntity);
            System.out.println("Файл успешно сохранён в базе данных: " + fileEntity.getId());
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении файла в базе данных: " + file.getOriginalFilename() + ": " + e.getMessage());
            throw new RuntimeException("Ошибка при сохранении файла в базе: " + file.getOriginalFilename(), e);
        }

        if (message != null) {
            message.getFiles().add(fileEntity);
        }

        return fileEntity;
    }

    public File getFile(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));
    }

    public Page<FileDTO> getChatFiles(Long chatId, Pageable pageable) {
        return fileRepository.findByMessageChatId(chatId, pageable).map(file -> {
            FileDTO dto = new FileDTO();
            dto.setId(file.getId());
            dto.setFileName(file.getFileName());
            dto.setUploadedAt(file.getUploadedAt().format(DateTimeFormatter.ofPattern("dd.MM.yy HH:mm")));
            dto.setFileSize(file.getFileSize());
            dto.setSenderUsername(file.getMessage().getSender().getUsername());
            try {
                Path path = Paths.get(file.getFilePath());
                String contentType = Files.probeContentType(path);
                dto.setContentType(contentType != null ? contentType : "application/octet-stream");
            } catch (IOException e) {
                dto.setContentType("application/octet-stream");
            }
            return dto;
        });
    }

    @Transactional
    public File save(File file) {
        if (file == null) {
            throw new IllegalArgumentException("Файл не может быть null");
        }
        File savedFile = fileRepository.save(file);
        System.out.println("Сохранён файл: id=" + savedFile.getId() + ", name=" + savedFile.getFileName());
        return savedFile;
    }
}