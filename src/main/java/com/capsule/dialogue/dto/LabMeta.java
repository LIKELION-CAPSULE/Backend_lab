package com.capsule.dialogue.dto;

import java.util.List;
import java.util.Map;

/** 실험실 첫 화면에 필요한 설정 값 */
public record LabMeta(
        String defaultProvider,
        List<Provider> providers,
        List<String> pipelines,
        String defaultPipeline,
        List<Route> routes,
        List<Key> promptKeys,
        List<Character> characters,
        List<String> triggers,
        List<String> emotions,
        boolean tokenRequired) {

    public record Provider(String name, boolean ready) {}

    /** models = 공급자 → 이 용도에 쓸 모델 */
    public record Route(String purpose, String tier, int maxTokens, Double temperature, Map<String, String> models) {}

    public record Key(String key, String format, String description) {}

    public record Character(long id, String name) {}
}
