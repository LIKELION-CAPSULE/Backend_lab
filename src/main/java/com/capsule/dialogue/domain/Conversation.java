package com.capsule.dialogue.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 × 캐릭터 대화방. 세션을 넘어 남는 건 세션 카드(conversation_recaps) · 아는 사실(character_memories).
 * 행은 ConversationRepository.insertIfAbsent로 만든다 (첫 대화 동시 요청 대비)
 */
@Entity
@Table(name = "conversations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long userId;
    private long characterId;
    private Instant createdAt;
}
