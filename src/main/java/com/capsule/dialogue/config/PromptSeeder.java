package com.capsule.dialogue.config;

import com.capsule.dialogue.domain.PromptKey;
import com.capsule.dialogue.repository.PromptVersionRepository;
import com.capsule.dialogue.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 시작할 때 classpath:prompts/의 공통 시드를 DB에 맞춘다: 같은 본문의 공통 버전이 아직 없으면 새 버전으로 넣고 바로 켠다.
 * - 처음 시작: 버전 1이 ACTIVE
 * - git에서 시드 파일을 고쳐 배포: 그 본문이 새 ACTIVE (운영의 공통 프롬프트는 git이 원본. 운영에는 실험실이 없으므로)
 * - 시드가 그대로면 아무것도 안 한다: 실험실에서 켠 버전·되돌린 버전이 유지된다
 * 실험실에서 좋았던 공통 프롬프트를 운영에 내보내려면 그 본문을 시드 파일에 옮겨 커밋한다.
 */
@Component
class PromptSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PromptSeeder.class);

    private final PromptService prompts;
    private final PromptVersionRepository versions;

    PromptSeeder(PromptService prompts, PromptVersionRepository versions) {
        this.prompts = prompts;
        this.versions = versions;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (PromptKey key : PromptKey.values()) {
            String seed = CharacterContentSeeder.read(key.seedPath()).replace("\r\n", "\n"); // Windows 체크아웃도 같은 본문으로
            if (versions.existsByPromptKeyAndCharacterIdIsNullAndBody(key.key(), seed)) continue;
            try {
                prompts.create(key, null, seed, "시드: classpath:" + key.seedPath(), true);
                log.info("프롬프트 시드를 새 버전으로 켬: {}", key.key());
            } catch (DataIntegrityViolationException e) {
                log.info("프롬프트 시드는 다른 인스턴스가 먼저 넣음: {}", key.key());
            }
        }
    }
}
