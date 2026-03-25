package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id @GeneratedValue
    @Column(name = "member_id")
    private Long id;

    private String username;
    private String password;
    private String nickname;

    private Member(String username, String password, String nickname) {

        if (username == null || username.isEmpty()) {
            throw new CustomException(ErrorCode.EMPTY_USERNAME);
        }

        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }

    public static Member of(String username, String password, String nickname) {
        return new Member(
                username,
                password,
                nickname
        );
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
