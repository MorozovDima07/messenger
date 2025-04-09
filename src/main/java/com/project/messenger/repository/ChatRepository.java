package com.project.messenger.repository;

import com.project.messenger.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    @Query("SELECT DISTINCT c FROM Chat c JOIN FETCH c.members m WHERE EXISTS " +
            "(SELECT cm FROM ChatMember cm WHERE cm.chat.id = c.id AND cm.user.id = :userId)")
    List<Chat> findByMembersUserId(@Param("userId") Long userId);
    @Query("SELECT c FROM Chat c JOIN c.members m1 JOIN c.members m2 " +
            "WHERE c.type = 'PERSONAL' " +
            "AND m1.user.id = :userId1 AND m2.user.id = :userId2 " +
            "AND (SELECT COUNT(m) FROM ChatMember m WHERE m.chat.id = c.id) = 2")
    Optional<Chat> findPersonalChatBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
    @Query("SELECT c FROM Chat c JOIN FETCH c.members WHERE c.id = :id")
    Optional<Chat> findChatWithMembersById(@Param("id") Long id);
    Optional<Chat> findByInviteLink(String inviteLink);

    @Query("SELECT c FROM Chat c JOIN FETCH c.members m JOIN FETCH m.user WHERE c.id = :id")
    Optional<Chat> findChatWithMembersWithUsersById(@Param("id") Long id);
}