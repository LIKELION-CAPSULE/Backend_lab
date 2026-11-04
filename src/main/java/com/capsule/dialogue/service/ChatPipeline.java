package com.capsule.dialogue.service;

import com.capsule.dialogue.dto.ChatInput;
import com.capsule.dialogue.dto.ChatOutcome;
import com.capsule.dialogue.dto.LlmContext;

/**
 * 메시지 답장(명세 20)을 만드는 방법. 서비스와 실험실이 같은 구현을 부른다 (실험실에서 본 품질 = 서비스 품질).
 * 서비스가 쓸 구현은 capsule.dialogue.chat-pipeline, 실험실은 대화방마다 고른다.
 * 실패하면 예외를 던진다 (서비스는 대체 대사로, 실험실은 오류를 보여 줌). 시간 제한은 부르는 쪽이 지킨다.
 */
public interface ChatPipeline {

    String SIMPLE = "SIMPLE";
    String ENGINE = "ENGINE";

    String name();

    ChatOutcome reply(ChatInput in, LlmContext ctx);
}
