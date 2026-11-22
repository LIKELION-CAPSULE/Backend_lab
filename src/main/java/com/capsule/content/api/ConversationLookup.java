package com.capsule.content.api;

/**
 * 메이트와 나눈 대화가 있는지 (api.md 31 hasConversation, 2026-11-23). content는 인터페이스만 두고 dialogue가 구현한다.
 * dialogue가 이미 content를 부르므로, content가 dialogue를 직접 부르면 순환이 생겨서 의존을 뒤집었다 (user ActiveSessionLookup과 같은 방식)
 */
public interface ConversationLookup {

    boolean hasConversation(long userId, long mateId);
}
