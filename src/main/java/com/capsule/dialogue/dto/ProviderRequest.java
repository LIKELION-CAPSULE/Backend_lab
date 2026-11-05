package com.capsule.dialogue.dto;

import com.capsule.dialogue.client.Llm;
import com.capsule.dialogue.config.LlmProperties;
import java.util.List;

/**
 * 공급자(Anthropic·OpenAI·Fake)에 그대로 보낼 요청. 게이트웨이가 용도·설정·덮어쓰기를 풀어서 만든다.
 *
 * @param system cache=true인 블록에 프롬프트 캐시 지점을 둔다 (Anthropic만. OpenAI는 앞부분이 같으면 자동 캐시)
 * @param temperature null이면 보내지 않는다 (GPT-5 계열 추론 모델은 기본값만 받음)
 * @param reasoningEffort OpenAI 추론 모델만. null이면 보내지 않는다
 * @param thinking Anthropic 모델의 생각 설정 (#92). null이면 보내지 않는다 (모델 기본값)
 */
public record ProviderRequest(
        LlmPurpose purpose,
        String model,
        List<Llm.SystemBlock> system,
        List<Llm.ChatTurn> messages,
        int maxTokens,
        Double temperature,
        String reasoningEffort,
        LlmProperties.Thinking thinking) {}
