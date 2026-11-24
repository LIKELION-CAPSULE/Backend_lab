package com.capsule.dialogue.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.DialogueErrorCode;
import com.capsule.dialogue.dto.ChatRoomResponse;
import com.capsule.dialogue.repository.CharacterMemoryRepository;
import com.capsule.dialogue.repository.ChatMessageRepository;
import com.capsule.dialogue.repository.ConversationRecapRepository;
import com.capsule.dialogue.repository.ConversationRepository;
import com.capsule.user.api.ActiveSessionLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 대화방 = 사용자 × 메이트 하나 (conversations). 연결이 끝나도 메시지가 남는다 (api.md 32 · 31, 2026-11-23) */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatRoomService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final MateQuery mates;
    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final ConversationRecapRepository recaps;
    private final CharacterMemoryRepository characterMemories;
    private final ActiveSessionLookup activeSessions;
    private final Clock clock;

    /** 32. cursor = 앞 페이지 마지막 메시지 id. 하나 더 읽어서 다음 페이지가 있는지 본다 */
    public ChatRoomResponse page(long userId, long mateId, String cursor, Integer size) {
        mates.find(mateId)
                .filter(MateInfo::active)
                .orElseThrow(() -> new ApiException(DialogueErrorCode.MATE_NOT_FOUND));
        int n = size == null ? DEFAULT_SIZE : size;
        if (n < 1 || n > MAX_SIZE) throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        long before = before(cursor);
        List<ChatMessage> rows = conversations
                .findByUserIdAndCharacterId(userId, mateId)
                .map(c -> messages.findPage(c.getId(), before, PageRequest.of(0, n + 1)))
                .orElse(List.of());
        boolean more = rows.size() > n;
        List<ChatMessage> shown = more ? rows.subList(0, n) : rows;
        return new ChatRoomResponse(
                shown.stream().map(ChatRoomService::view).toList(),
                more ? String.valueOf(shown.getLast().getId()) : null);
    }

    /** 31 hasConversation: 이 메이트와 답장까지 받은 대화가 하나라도 있는지 */
    public boolean hasConversation(long userId, long mateId) {
        return conversations
                .findByUserIdAndCharacterId(userId, mateId)
                .map(c -> messages.existsByConversationIdAndSenderAndHiddenAtIsNull(c.getId(), ChatMessage.CHARACTER))
                .orElse(false);
    }

    /**
     * 47 대화 초기화: 이 메이트와의 대화를 처음부터. 메시지는 가리고(지우지 않음), 지난 대화 카드와 메이트가 기억한 사실은 지운다.
     * 친밀도 · 호감도 · 해금 · 크레딧 · 안전 정지는 그대로. 이 메이트와 연결 중이면 409 (답장 저장과 겹치지 않게)
     */
    // TODO(judgment): SCR-36 '초기화'가 사용자 버튼인지 안전 제재 단계인지 확정 필요 (명세 47). 제재로 정해지면 SafetyGuard에서 이걸 부른다
    @Transactional
    public void reset(long userId, long mateId) {
        mates.find(mateId)
                .filter(MateInfo::active)
                .orElseThrow(() -> new ApiException(DialogueErrorCode.MATE_NOT_FOUND));
        if (activeSessions.find(userId).filter(a -> a.mateId() == mateId).isPresent()) {
            throw new ApiException(DialogueErrorCode.CONNECTION_IN_PROGRESS);
        }
        conversations.findByUserIdAndCharacterId(userId, mateId).ifPresent(c -> {
            messages.hideAll(c.getId(), Instant.now(clock));
            recaps.deleteByConversationId(c.getId());
        });
        characterMemories.deleteByUserIdAndCharacterId(userId, mateId);
    }

    private static long before(String cursor) {
        if (cursor == null || cursor.isBlank()) return Long.MAX_VALUE;
        try {
            long id = Long.parseLong(cursor);
            if (id < 1) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private static ChatRoomResponse.Message view(ChatMessage m) {
        // DB 값 CHARACTER는 화면 용어로 MATE (명세 32)
        String sender = ChatMessage.USER.equals(m.getSender()) ? "USER" : "MATE";
        // inputType은 사용자 말에만, rating은 답장에만 (2026-11-23)
        return new ChatRoomResponse.Message(
                m.getRequestId(),
                sender,
                m.getContent(),
                m.getInputType(),
                m.getRating(),
                m.getCreatedAt(),
                m.getSessionId());
    }
}
