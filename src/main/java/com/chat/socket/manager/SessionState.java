package com.chat.socket.manager;

public class SessionState {

    private final Long memberId;
    private volatile Long activeRoomId;

    public SessionState(Long memberId) {
        this.memberId = memberId;
        this.activeRoomId = null;
    }

    public void activate(Long roomId) {
        this.activeRoomId = roomId;
    }

    public void deactivatedIfRoom(Long roomId) {
        if (roomId.equals(this.activeRoomId)) {
            this.activeRoomId = null;
        }
    }

    public Long getMemberId() {
        return memberId;
    }

    public Long getActiveRoomId() {
        return activeRoomId;
    }
}
