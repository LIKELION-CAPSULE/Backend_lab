package com.capsule.user.adapter;

import com.capsule.user.api.MemoryQuery;
import com.capsule.user.api.UserTypes.MemoryInfo;
import com.capsule.user.service.MemoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** MemoryQuery 계약 구현. 계약마다 얇은 클래스로 따로 둔다 (이유는 {@link UserQueryAdapter}) */
@Component
@RequiredArgsConstructor
public class MemoryQueryAdapter implements MemoryQuery {

    private final MemoryService memories;

    @Override
    public List<MemoryInfo> memoriesForConversation(long userId) {
        return memories.memoriesForConversation(userId);
    }
}
