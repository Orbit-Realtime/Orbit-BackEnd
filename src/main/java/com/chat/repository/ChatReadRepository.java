package com.chat.repository;

import com.chat.entity.ChatRead;
import com.chat.repository.dtos.MemberUnreadCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatReadRepository extends JpaRepository<ChatRead, Long> {

    @Query("SELECT cr" +
            " FROM ChatRead cr" +
            " WHERE cr.chat.id = :chatId" +
            " and cr.member.id = :memberId")
    ChatRead findBy(@Param("chatId") Long chatId, @Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE ChatRead cr " +
            "SET cr.isRead = true " +
            "WHERE cr.chat.chatRoom.id = :chatRoomId " +
            "AND cr.member.id = :memberId " +
            "AND cr.isRead = false")
    int updateUnreadChatReadsToRead(@Param("memberId") Long memberId,
                                    @Param("chatRoomId") Long chatRoomId);

    @Query("SELECT new com.chat.repository.dtos.MemberUnreadCount(cre.member.id, COUNT(cre))" +
            " FROM ChatRead cre" +
            " JOIN cre.chat c" +
            " WHERE c.chatRoom.id = :chatRoomId" +
            " AND cre.member.id IN :memberIds" +
            " AND cre.isRead = false" +
            " GROUP BY cre.member.id")
    List<MemberUnreadCount> findUnReadCountsBy(@Param("chatRoomId") Long chatRoomId,
                                                      @Param("memberIds") List<Long> memberIds);
}
