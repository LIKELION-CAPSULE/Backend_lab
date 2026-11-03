package com.capsule.user.api;

import com.capsule.user.api.UserTypes.MemoryInfo;
import java.util.List;

public interface MemoryQuery {

    /** 대화(LLM) 맥락에 넣을 일정: 오늘 이후 일정 + 마감일 없는 목표 (api.md 13 "모든 캐릭터가 기억해서 대화에 활용") */
    List<MemoryInfo> memoriesForConversation(long userId);
}
