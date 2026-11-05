package com.capsule.dialogue.service;

import com.capsule.dialogue.repository.CharacterMemoryRepository;
import com.capsule.dialogue.repository.ConversationRepository;
import com.capsule.user.api.UserWithdrawnEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 때 대화 기억을 지운다 (api.md 27, schema.sql '탈퇴 시 서버가 삭제').
 * 탈퇴 트랜잭션 안에서 동기로 받는다: 여기서 실패하면 탈퇴도 되돌아간다.
 * llm_usages는 비용 근거라 남긴다.
 */
@Component
class WithdrawalCleaner {

    private final ConversationRepository conversations;
    private final CharacterMemoryRepository memories;

    WithdrawalCleaner(ConversationRepository conversations, CharacterMemoryRepository memories) {
        this.conversations = conversations;
        this.memories = memories;
    }

    @EventListener
    void on(UserWithdrawnEvent event) {
        conversations.deleteByUserId(event.userId()); // 대화 원문 · 세션 카드는 CASCADE
        memories.deleteByUserId(event.userId());
    }
}
