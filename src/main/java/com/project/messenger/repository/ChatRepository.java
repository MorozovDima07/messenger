package com.project.messenger.repository;

import com.project.messenger.model.Chat;
import com.project.messenger.model.ChatType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    Page<Chat> findByMembersUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Chat c JOIN FETCH c.members m WHERE EXISTS " +
            "(SELECT cm FROM ChatMember cm WHERE cm.chat.id = c.id AND cm.user.id = :userId AND c.type = :type)")
    Page<Chat> findByMembersUserIdAndType(@Param("userId") Long userId, @Param("type") ChatType type, Pageable pageable);

    @Query("SELECT c FROM Chat c JOIN c.members m1 JOIN c.members m2 " +
            "WHERE c.type = 'PERSONAL' AND m1.user.id = :userId1 AND m2.user.id = :userId2")
    Optional<Chat> findPersonalChatBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    @Query("SELECT c FROM Chat c JOIN FETCH c.members WHERE c.inviteLink = :inviteLink")
    Optional<Chat> findByInviteLink(@Param("inviteLink") String inviteLink);

    boolean existsByInviteLink(String inviteLink);

    List<Chat> findByTypeAndMembers_UserId(ChatType type, Long userId);


    @Query("SELECT c FROM Chat c JOIN FETCH c.members m WHERE EXISTS " +
            "(SELECT cm FROM ChatMember cm WHERE cm.chat.id = c.id AND cm.user.id = :userId) " +
            "AND LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%')) AND c.type = 'GROUP'")
    Page<Chat> findGroupChatsByName(@Param("userId") Long userId, @Param("query") String query, Pageable pageable);

    @Query("""
    SELECT c FROM Chat c
    JOIN ChatMember cm1 ON cm1.chat = c
    JOIN ChatMember cm2 ON cm2.chat = c
    JOIN User u ON u = cm2.user
    WHERE c.type = 'PERSONAL'
      AND cm1.user.id = :userId
      AND cm2.user.id != :userId
      AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    Page<Chat> findPersonalChatsByOtherUsername(@Param("userId") Long userId, @Param("query") String query, Pageable pageable);

    @Query(
            value = """
            SELECT DISTINCT c
            FROM Chat c
            JOIN c.members cm1
              ON cm1.user.id = :userId
            LEFT JOIN c.members cm2
              ON cm2.chat = c
              AND cm2.user.id <> :userId
            LEFT JOIN cm2.user u
            WHERE
              (
                c.type = 'GROUP'
                AND LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
              )
              OR
              (
                c.type = 'PERSONAL'
                AND u.id IS NOT NULL
                AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
              )
        """,
            countQuery = """
            SELECT COUNT(DISTINCT c)
            FROM Chat c
            JOIN c.members cm1
              ON cm1.user.id = :userId
            LEFT JOIN c.members cm2
              ON cm2.chat = c
              AND cm2.user.id <> :userId
            LEFT JOIN cm2.user u
            WHERE
              (
                c.type = 'GROUP'
                AND LOWER(c.name) LIKE LOWER(CONCAT('%', :query, '%'))
              )
              OR
              (
                c.type = 'PERSONAL'
                AND u.id IS NOT NULL
                AND LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
              )
        """
    )
    Page<Chat> findChatsAmongAll(
            @Param("userId") Long userId,
            @Param("query") String query,
            Pageable pageable
    );
}