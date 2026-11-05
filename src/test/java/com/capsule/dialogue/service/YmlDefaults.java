package com.capsule.dialogue.service;

import com.capsule.dialogue.config.DialogueProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** Spring 없이 도는 테스트가 application.yml의 capsule.dialogue 값을 그대로 쓰게 (숫자를 테스트에 다시 적지 않으려고) */
final class YmlDefaults {
    private YmlDefaults() {}

    static final DialogueProperties DIALOGUE = load();

    private static DialogueProperties load() {
        StandardEnvironment env = new StandardEnvironment();
        try {
            new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"))
                    .forEach(env.getPropertySources()::addLast);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Binder.get(env)
                .bind("capsule.dialogue", DialogueProperties.class)
                .get();
    }
}
