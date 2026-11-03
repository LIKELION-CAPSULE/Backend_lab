package com.capsule.dialogue.internal;

import com.capsule.session.api.SessionClosingHandler;
import com.capsule.session.api.SessionTypes.SessionClosing;
import org.springframework.stereotype.Component;

/**
 * session.api.SessionClosingHandler 구현. 계약마다 얇은 클래스로 따로 둔다:
 * 다른 모듈 테스트가 이 계약을 @MockitoBean으로 바꿔도 컨트롤러가 쓰는 DialogueService는 그대로 남게.
 */
@Component
class SessionClosingAdapter implements SessionClosingHandler {

    private final DialogueService dialogue;

    SessionClosingAdapter(DialogueService dialogue) {
        this.dialogue = dialogue;
    }

    @Override
    public String close(SessionClosing closing) {
        return dialogue.close(closing);
    }
}
