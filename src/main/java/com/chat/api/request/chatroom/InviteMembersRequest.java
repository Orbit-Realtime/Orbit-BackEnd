package com.chat.api.request.chatroom;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@NoArgsConstructor
public class InviteMembersRequest {
    private Set<Long> memberIds;
}
