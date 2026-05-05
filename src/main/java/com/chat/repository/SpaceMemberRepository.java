package com.chat.repository;

import com.chat.entity.Member;
import com.chat.entity.SpaceMember;
import com.chat.repository.dtos.MessageUnreadMemberCount;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.repository.dtos.RoomUnreadMessageCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    @Query("SELECT crp.space.id" +
            " FROM SpaceMember crp" +
            " WHERE crp.member.id IN :memberIds" +
            " GROUP BY crp.space.id" +
            " HAVING COUNT(DISTINCT crp.member.id) = :size" +
            " AND COUNT(DISTINCT crp.member.id) =" +
            " (SELECT COUNT(DISTINCT sub.member.id)" +
            " FROM SpaceMember sub" +
            " WHERE sub.space.id = crp.space.id)")
    List<Long> findChatRoomIdsByExactMembers(@Param("memberIds") List<Long> memberIds,
                                             @Param("size") long size);

    @Query(value = "SELECT crp" +
            " FROM SpaceMember crp" +
            " JOIN FETCH crp.space" +
            " WHERE crp.member.id = :memberId")
    List<SpaceMember> findAllFetchChatRoomBy(@Param("memberId") Long memberId);

    @Query(value = "SELECT crp" +
            " FROM SpaceMember crp" +
            " JOIN FETCH crp.member" +
            " WHERE crp.space.id = :chatRoomId")
    List<SpaceMember> findAllFetchMemberBy(@Param("chatRoomId") Long chatRoomId);

    @Query(value = "SELECT crp" +
            " FROM SpaceMember crp" +
            " JOIN FETCH crp.member" +
            " WHERE crp.space.id IN :chatRoomIds")
    List<SpaceMember> findAllFetchMemberBy(@Param("chatRoomIds") List<Long> chatRoomIds);

    @Query("SELECT crp" +
            " FROM SpaceMember crp" +
            " WHERE crp.space.id = :chatRoomId" +
            " AND crp.member.id = :memberId")
    SpaceMember findChatRoomBy(@Param("chatRoomId") Long chatRoomId,
                               @Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM SpaceMember crp" +
            " WHERE crp.space.id = :chatRoomId AND crp.member.id = :memberId")
    void deleteBy(@Param("chatRoomId") Long chatRoomId, @Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE SpaceMember crp" +
            " SET crp.lastReadMessageId = :chatId" +
            " WHERE crp.member.id = :memberId" +
            " AND crp.space.id = :chatRoomId" +
            " AND (crp.lastReadMessageId IS NULL OR crp.lastReadMessageId < :chatId)")
    int updateLastReadMessageId(@Param("memberId") Long memberId,
                             @Param("chatRoomId") Long chatRoomId,
                             @Param("chatId") Long chatId);

    @Query("SELECT new com.chat.repository.dtos.RoomUnreadMessageCount(crp.space.id, COUNT(c))" +
            " FROM SpaceMember crp" +
            " JOIN Message c ON c.space.id = crp.space.id" +
            " WHERE crp.space.id IN :chatRoomIds" +
            " AND crp.member.id = :memberId" +
            " AND (crp.lastReadMessageId IS NULL OR c.id > crp.lastReadMessageId)" +
            " GROUP BY crp.space.id")
    List<RoomUnreadMessageCount> findRoomUnreadMessageCountsBy(@Param("chatRoomIds") List<Long> chatRoomIds,
                                                               @Param("memberId") Long memberId);

    @Query("SELECT new com.chat.repository.dtos.MemberUnreadCount(crp.member.id, COUNT(c))" +
            " FROM SpaceMember crp" +
            " JOIN Message c ON c.space.id = crp.space.id" +
            " WHERE crp.space.id = :chatRoomId" +
            " AND crp.member.id IN :memberIds" +
            " AND (crp.lastReadMessageId IS NULL OR c.id > crp.lastReadMessageId)" +
            " GROUP BY crp.member.id")
    List<MemberUnreadCount> findMemberUnreadMessageCountsBy(@Param("chatRoomId") Long chatRoomId,
                                                             @Param("memberIds") List<Long> memberIds);

    @Query("SELECT crp.lastReadMessageId" +
            " FROM SpaceMember crp" +
            " WHERE crp.member.id = :memberId" +
            " AND crp.space.id = :chatRoomId")
    Long findLastReadMessageIdBy(@Param("memberId") Long memberId,
                              @Param("chatRoomId") Long chatRoomId);

    @Query("SELECT COUNT (crp)" +
            " FROM Message c" +
            " JOIN SpaceMember crp ON crp.space.id = c.space.id" +
            " WHERE c.id = :messageId" +
            " AND (crp.lastReadMessageId IS NULL OR crp.lastReadMessageId < :messageId)")
    Long countMessageUnreadMembers(@Param("messageId") Long messageId);

    @Query("SELECT new com.chat.repository.dtos.MessageUnreadMemberCount(c.id, COUNT(crp))" +
            " FROM Message c" +
            " JOIN SpaceMember crp ON crp.space.id = c.space.id" +
            " WHERE c.id IN :messageIds" +
            " AND (crp.lastReadMessageId IS NULL OR crp.lastReadMessageId < c.id)" +
            " GROUP BY c.id")
    List<MessageUnreadMemberCount> countMessageUnreadMembers(@Param("messageIds") List<Long> messageIds);

    Long member(Member member);
}
