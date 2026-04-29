package com.chat.repository;

import com.chat.entity.ChatRoomParticipant;
import com.chat.repository.dtos.MessageUnreadMemberCount;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.repository.dtos.RoomUnreadMessageCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {

    @Query("SELECT crp.chatRoom.id" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.member.id IN :memberIds" +
            " GROUP BY crp.chatRoom.id" +
            " HAVING COUNT(DISTINCT crp.member.id) = :size" +
            " AND COUNT(DISTINCT crp.member.id) =" +
            " (SELECT COUNT(DISTINCT sub.member.id)" +
            " FROM ChatRoomParticipant sub" +
            " WHERE sub.chatRoom.id = crp.chatRoom.id)")
    List<Long> findChatRoomIdsByExactMembers(@Param("memberIds") List<Long> memberIds,
                                             @Param("size") long size);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " JOIN FETCH crp.chatRoom" +
            " WHERE crp.member.id = :memberId")
    List<ChatRoomParticipant> findAllFetchChatRoomBy(@Param("memberId") Long memberId);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " JOIN FETCH crp.member" +
            " WHERE crp.chatRoom.id = :chatRoomId")
    List<ChatRoomParticipant> findAllFetchMemberBy(@Param("chatRoomId") Long chatRoomId);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " JOIN FETCH crp.member" +
            " WHERE crp.chatRoom.id IN :chatRoomIds")
    List<ChatRoomParticipant> findAllFetchMemberBy(@Param("chatRoomIds") List<Long> chatRoomIds);

    @Query("SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.chatRoom.id = :chatRoomId" +
            " AND crp.member.id = :memberId")
    ChatRoomParticipant findChatRoomBy(@Param("chatRoomId") Long chatRoomId,
                                       @Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM ChatRoomParticipant crp" +
            " WHERE crp.chatRoom.id = :chatRoomId AND crp.member.id = :memberId")
    void deleteBy(@Param("chatRoomId") Long chatRoomId, @Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE ChatRoomParticipant crp" +
            " SET crp.lastReadChatId = :chatId" +
            " WHERE crp.member.id = :memberId" +
            " AND crp.chatRoom.id = :chatRoomId" +
            " AND (crp.lastReadChatId IS NULL OR crp.lastReadChatId < :chatId)")
    int updateLastReadChatId(@Param("memberId") Long memberId,
                             @Param("chatRoomId") Long chatRoomId,
                             @Param("chatId") Long chatId);

    @Query("SELECT new com.chat.repository.dtos.RoomUnreadMessageCount(crp.chatRoom.id, COUNT(c))" +
            " FROM ChatRoomParticipant crp" +
            " JOIN Chat c ON c.chatRoom.id = crp.chatRoom.id" +
            " WHERE crp.chatRoom.id IN :chatRoomIds" +
            " AND crp.member.id = :memberId" +
            " AND (crp.lastReadChatId IS NULL OR c.id > crp.lastReadChatId)" +
            " GROUP BY crp.chatRoom.id")
    List<RoomUnreadMessageCount> findCursorUnreadCountsBy(@Param("chatRoomIds") List<Long> chatRoomIds,
                                                          @Param("memberId") Long memberId);

    @Query("SELECT new com.chat.repository.dtos.MemberUnreadCount(crp.member.id, COUNT(c))" +
            " FROM ChatRoomParticipant crp" +
            " JOIN Chat c ON c.chatRoom.id = crp.chatRoom.id" +
            " WHERE crp.chatRoom.id = :chatRoomId" +
            " AND crp.member.id IN :memberIds" +
            " AND (crp.lastReadChatId IS NULL OR c.id > crp.lastReadChatId)" +
            " GROUP BY crp.member.id")
    List<MemberUnreadCount> findCursorUnreadCountsByMembers(@Param("chatRoomId") Long chatRoomId,
                                                            @Param("memberIds") List<Long> memberIds);

    @Query("SELECT crp.lastReadChatId" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.member.id = :memberId" +
            " AND crp.chatRoom.id = :chatRoomId")
    Long findLastReadChatIdBy(@Param("memberId") Long memberId,
                              @Param("chatRoomId") Long chatRoomId);

    @Query("SELECT COUNT (crp)" +
            " FROM Chat c" +
            " JOIN ChatRoomParticipant crp ON crp.chatRoom.id = c.chatRoom.id" +
            " WHERE c.id = :messageId" +
            " AND (crp.lastReadChatId IS NULL OR crp.lastReadChatId < :messageId)")
    Long countUnreadMembers(@Param("messageId") Long messageId);

    @Query("SELECT new com.chat.repository.dtos.MessageUnreadMemberCount(c.id, COUNT(crp))" +
            " FROM Chat c" +
            " JOIN ChatRoomParticipant crp ON crp.chatRoom.id = c.chatRoom.id" +
            " WHERE c.id IN :messageIds" +
            " AND (crp.lastReadChatId IS NULL OR crp.lastReadChatId < c.id)" +
            " GROUP BY c.id")
    List<MessageUnreadMemberCount> countUnreadMembers(@Param("messageIds") List<Long> messageIds);
}
