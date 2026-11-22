package com.capsule.dialogue.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.dialogue.domain.ChatMessage;
import com.capsule.dialogue.domain.DialogueErrorCode;
import com.capsule.dialogue.dto.ChatRoomResponse;
import com.capsule.dialogue.repository.ChatMessageRepository;
import com.capsule.dialogue.repository.ConversationRepository;
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
                .map(c -> messages.existsByConversationIdAndSender(c.getId(), ChatMessage.CHARACTER))
                .orElse(false);
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
        return new ChatRoomResponse.Message(
                m.getRequestId(), sender, m.getContent(), null, null, m.getCreatedAt(), m.getSessionId());
    }
}
