package com.project.messenger.repository;

import com.project.messenger.model.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findByMessageChatId(Long chatId);
    Page<File> findByMessageChatId(Long chatId, Pageable pageable);
}