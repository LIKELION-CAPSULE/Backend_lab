package com.capsule.dialogue.adapter;

import com.capsule.content.api.ConversationLookup;
import com.capsule.dialogue.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConversationLookupAdapter implements ConversationLookup {

    private final ChatRoomService chatRoom;

    @Override
    public boolean hasConversation(long userId, long mateId) {
        return chatRoom.hasConversation(userId, mateId);
    }
}
