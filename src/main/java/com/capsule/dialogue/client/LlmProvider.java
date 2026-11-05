package com.capsule.dialogue.client;

import com.capsule.dialogue.dto.ProviderRequest;
import java.util.function.Consumer;

/**
 * LLM 공급자 하나(Anthropic·OpenAI·Fake). 공급자마다 다른 요청·응답 형식은 구현 안에서 끝나고, 위로는 {@link Llm.Result}만 올라간다.
 * 새 공급자는 이 인터페이스를 구현한 @Component 하나로 붙는다 (LlmGateway가 name()으로 찾음).
 */
public interface LlmProvider {

    /** capsule.llm.provider에 쓰는 이름 */
    String name();

    /** 부를 수 있는지 (API 키가 있음). 실험실 화면에 보여 준다 */
    boolean ready();

    /**
     * @param onText null이 아니면 스트리밍으로 받으며 지금까지 쌓인 글을 넘긴다 (첫 글자까지 시간 ttftMs를 잰다)
     * @throws LlmCallException HTTP 오류·연결 실패·응답 형식 오류
     */
    Llm.Result complete(ProviderRequest request, Consumer<String> onText);
}
