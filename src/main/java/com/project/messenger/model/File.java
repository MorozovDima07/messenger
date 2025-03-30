package com.project.messenger.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_message_id", columnList = "message_id")
})
@Data
public class File {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Сообщение обязательно")
    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @NotBlank(message = "Путь к файлу не может быть пустым")
    @Column(nullable = false)
    private String filePath;

    @NotBlank(message = "Имя файла не может быть пустым")
    @Column(nullable = false)
    private String fileName;

    private LocalDateTime uploadedAt;
}