package com.chat.repository;

import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.repository.dtos.MessageUnreadMemberCount;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.repository.dtos.RoomUnreadMessageCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, Long> {

    @Query("SELECT crp.space.id" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.member.id IN :memberIds" +
            " GROUP BY crp.space.id" +
            " HAVING COUNT(DISTINCT crp.member.id) = :size" +
            " AND COUNT(DISTINCT crp.member.id) =" +
            " (SELECT COUNT(DISTINCT sub.member.id)" +
            " FROM ChatRoomParticipant sub" +
            " WHERE sub.space.id = crp.space.id)")
    List<Long> findChatRoomIdsByExactMembers(@Param("memberIds") List<Long> memberIds,
                                             @Param("size") long size);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " JOIN FETCH crp.space" +
            " WHERE crp.member.id = :memberId")
    List<ChatRoomParticipant> findAllFetchChatRoomBy(@Param("memberId") Long memberId);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " JOIN FETCH crp.member" +
            " WHERE crp.space.id = :chatRoomId")
    List<ChatRoomParticipant> findAllFetchMemberBy(@Param("chatRoomId") Long chatRoomId);

    @Query(value = "SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " JOIN FETCH crp.member" +
            " WHERE crp.space.id IN :chatRoomIds")
    List<ChatRoomParticipant> findAllFetchMemberBy(@Param("chatRoomIds") List<Long> chatRoomIds);

    @Query("SELECT crp" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.space.id = :chatRoomId" +
            " AND crp.member.id = :memberId")
    ChatRoomParticipant findChatRoomBy(@Param("chatRoomId") Long chatRoomId,
                                       @Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM ChatRoomParticipant crp" +
            " WHERE crp.space.id = :chatRoomId AND crp.member.id = :memberId")
    void deleteBy(@Param("chatRoomId") Long chatRoomId, @Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE ChatRoomParticipant crp" +
            " SET crp.lastReadChatId = :chatId" +
            " WHERE crp.member.id = :memberId" +
            " AND crp.space.id = :chatRoomId" +
            " AND (crp.lastReadChatId IS NULL OR crp.lastReadChatId < :chatId)")
    int updateLastReadChatId(@Param("memberId") Long memberId,
                             @Param("chatRoomId") Long chatRoomId,
                             @Param("chatId") Long chatId);

    @Query("SELECT new com.chat.repository.dtos.RoomUnreadMessageCount(crp.space.id, COUNT(c))" +
            " FROM ChatRoomParticipant crp" +
            " JOIN Chat c ON c.space.id = crp.space.id" +
            " WHERE crp.space.id IN :chatRoomIds" +
            " AND crp.member.id = :memberId" +
            " AND (crp.lastReadChatId IS NULL OR c.id > crp.lastReadChatId)" +
            " GROUP BY crp.space.id")
    List<RoomUnreadMessageCount> findRoomUnreadMessageCountsBy(@Param("chatRoomIds") List<Long> chatRoomIds,
                                                               @Param("memberId") Long memberId);

    @Query("SELECT new com.chat.repository.dtos.MemberUnreadCount(crp.member.id, COUNT(c))" +
            " FROM ChatRoomParticipant crp" +
            " JOIN Chat c ON c.space.id = crp.space.id" +
            " WHERE crp.space.id = :chatRoomId" +
            " AND crp.member.id IN :memberIds" +
            " AND (crp.lastReadChatId IS NULL OR c.id > crp.lastReadChatId)" +
            " GROUP BY crp.member.id")
    List<MemberUnreadCount> findMemberUnreadMessageCountsBy(@Param("chatRoomId") Long chatRoomId,
                                                             @Param("memberIds") List<Long> memberIds);

    @Query("SELECT crp.lastReadChatId" +
            " FROM ChatRoomParticipant crp" +
            " WHERE crp.member.id = :memberId" +
            " AND crp.space.id = :chatRoomId")
    Long findLastReadChatIdBy(@Param("memberId") Long memberId,
                              @Param("chatRoomId") Long chatRoomId);

    @Query("SELECT COUNT (crp)" +
            " FROM Chat c" +
            " JOIN ChatRoomParticipant crp ON crp.space.id = c.space.id" +
            " WHERE c.id = :messageId" +
            " AND (crp.lastReadChatId IS NULL OR crp.lastReadChatId < :messageId)")
    Long countMessageUnreadMembers(@Param("messageId") Long messageId);

    @Query("SELECT new com.chat.repository.dtos.MessageUnreadMemberCount(c.id, COUNT(crp))" +
            " FROM Chat c" +
            " JOIN ChatRoomParticipant crp ON crp.space.id = c.space.id" +
            " WHERE c.id IN :messageIds" +
            " AND (crp.lastReadChatId IS NULL OR crp.lastReadChatId < c.id)" +
            " GROUP BY c.id")
    List<MessageUnreadMemberCount> countMessageUnreadMembers(@Param("messageIds") List<Long> messageIds);

    Long member(Member member);
}
