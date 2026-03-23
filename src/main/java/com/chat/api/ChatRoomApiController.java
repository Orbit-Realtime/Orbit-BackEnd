package com.chat.api;

import com.chat.api.request.chatroom.InviteMembersRequest;
import com.chat.api.request.chatroom.RenameChatRoomRequest;
import com.chat.api.response.chatroom.ChatRoomMemberResponse;
import com.chat.api.response.chatroom.ChatRoomResponse;
import com.chat.api.response.chatroom.ChatRoomsResponse;
import com.chat.api.request.chatroom.SaveChatRooomRequest;
import com.chat.utils.consts.SessionConst;
import com.chat.service.ChatRoomService;
import com.chat.service.dtos.SaveChatRoomDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatRoomApiController {

    private final ChatRoomService chatRoomService;

    @PostMapping("/api/chat/room")
    public Result<ChatRoomResponse> chatRoom(@RequestBody SaveChatRooomRequest request,
                                             @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        SaveChatRoomDTO saveChatRooomDto = SaveChatRoomDTO
                .builder()
                .senderId(loginMemberId)
                .receiverIds(request.getReceiverIds())
                .title(request.getTitle())
                .build();
        Long chatRoomId = chatRoomService.saveChatRoom(saveChatRooomDto);

        return Result
                .<ChatRoomResponse>builder()
                .data(new ChatRoomResponse(chatRoomId))
                .status(HttpStatus.OK)
                .message("채팅방 생성이 완료됐습니다.")
                .build();
    }

    @GetMapping("/api/chat/rooms")
    public Result<List<ChatRoomsResponse>> chatRooms(@SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(loginMemberId);

        return Result
                .<List<ChatRoomsResponse>>builder()
                .data(chatRooms)
                .status(HttpStatus.OK)
                .message("채팅방 목록 조회가 완료됐습니다.")
                .build();
    }

    @DeleteMapping("/api/chat/room/{chatRoomId}")
    public Result<Void> leaveChatRoom(@PathVariable Long chatRoomId,
                                      @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        chatRoomService.leaveChatRoom(loginMemberId, chatRoomId);

        return Result.<Void>builder()
                .status(HttpStatus.OK)
                .message("채팅방에서 나갔습니다.")
                .build();
    }

    @PatchMapping("/api/chat/room/{chatRoomId}")
    public Result<Void> renameChatRoom(@PathVariable Long chatRoomId,
                                       @RequestBody RenameChatRoomRequest request,
                                       @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        chatRoomService.renameChatRoom(loginMemberId, chatRoomId, request.getTitle());

        return Result.<Void>builder()
                .status(HttpStatus.OK)
                .message("채팅방 이름이 변경됐습니다.")
                .build();
    }

    @GetMapping("/api/chat/room/{chatRoomId}/members")
    public Result<List<ChatRoomMemberResponse>> chatRoomMembers(@PathVariable Long chatRoomId,
                                                                @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {
        List<ChatRoomMemberResponse> members = chatRoomService.findChatRoomMembers(loginMemberId, chatRoomId);

        return Result.<List<ChatRoomMemberResponse>>builder()
                .data(members)
                .status(HttpStatus.OK)
                .message("멤버 목록 조회가 완료됐습니다.")
                .build();
    }

    @PostMapping("/api/chat/room/{chatRoomId}/members")
    public Result<Void> inviteMembers(@PathVariable Long chatRoomId,
                                      @RequestBody InviteMembersRequest request,
                                      @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        chatRoomService.inviteMembers(loginMemberId, chatRoomId, request.getMemberIds());

        return Result.<Void>builder()
                .status(HttpStatus.OK)
                .message("멤버 초대가 완료됐습니다.")
                .build();
    }
}
