package com.chat.repository;

import com.chat.entity.Chat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("SELECT c" +
            " FROM Chat c" +
            " WHERE c.space.id = :chatRoomId ORDER BY c.id DESC")
    List<Chat> findLastChatBy(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    @Query("SELECT MAX(c.id)" +
            " FROM Chat c" +
            " WHERE c.space.id = :chatRoomId")
    Optional<Long> findLastChatIdBy(@Param("chatRoomId") Long chatRoomId);

    // todo delete (no usage)
    @Query("SELECT c" +
            " FROM Chat c" +
            " JOIN FETCH c.member" +
            " WHERE c.space.id = :chatRoomId ORDER BY c.createdDate ASC")
    List<Chat> findChatHistory(@Param("chatRoomId") Long chatRoomId);

    @Query("SELECT c" +
            " FROM Chat c" +
            " WHERE c.id IN (" +
            "   SELECT MAX(c2.id)" +
            "   FROM Chat c2" +
            "   WHERE c2.space.id IN :chatRoomIds" +
            "   GROUP BY c2.space.id" +
            " )")
    List<Chat> findLastChatsBy(@Param("chatRoomIds") List<Long> chatRoomIds);

    @Query("SELECT c" +
            " FROM Chat c" +
            " JOIN FETCH c.member" +
            " WHERE c.space.id = :chatRoomId" +
            " ORDER BY c.id DESC")
    List<Chat> findLatestChats(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    @Query("SELECT c" +
            " FROM Chat c" +
            " JOIN FETCH c.member" +
            " WHERE c.space.id = :chatRoomId" +
            " AND c.id < :beforeChatId" +
            " ORDER BY c.id DESC")
    List<Chat> findChatsBeforeId(@Param("chatRoomId") Long chatRoomId,
                                 @Param("beforeChatId") Long beforeChatId,
                                 Pageable pageable);
}
