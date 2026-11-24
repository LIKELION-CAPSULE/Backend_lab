package com.capsule.user.api;

/**
 * 알림 적기 (api.md 43, ADR 0035). 부르는 쪽 트랜잭션에 같이 들어간다: 정산이 롤백되면 알림도 남지 않는다.
 * 문구는 user가 만든다 (부르는 쪽은 사실만 넘김)
 */
public interface NotificationCommands {

    /**
     * 연결을 끝내 친밀도 단계가 올랐다 (22). 같은 연결은 한 번만
     *
     * @param stageName 오른 단계 이름 (메이트마다 다름), 없으면 null
     * @param unlocked 이번에 열린 해금 콘텐츠가 있으면 true ('· 새 표정')
     */
    void stageUp(long userId, long connectionId, String mateName, int stage, String stageName, boolean unlocked);
}
