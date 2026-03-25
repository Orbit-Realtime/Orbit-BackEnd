package com.chat.api.request.member;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangeNicknameRequest {
    private String nickname;
}
