package com.project.messenger.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = true)
    @JsonIgnore
    private Message message;

    @NotBlank(message = "Путь к файлу не может быть пустым")
    @Column(nullable = false)
    private String filePath;

    private String contentType;

    private Long fileSize;

    @NotBlank(message = "Имя файла не может быть пустым")
    @Column(nullable = false)
    private String fileName;

    private LocalDateTime uploadedAt;
}