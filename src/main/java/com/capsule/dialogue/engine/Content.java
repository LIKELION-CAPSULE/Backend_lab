package com.capsule.dialogue.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 캐릭터 콘텐츠(바이블·수·비트·세계 지식·사건·고정 대사). Spring 쪽에서 YAML로 읽어 만든다. 엔진은 읽기만 한다. */
public final class Content {
    private Content() {}

    public record Example(String user, String reply) {}

    public record Move(String id, String name, String how, List<Example> examples) {}
    /** mode가 null이면 모드 무관. cooldown이 null이면 쿨다운 없음. */
    public record Beat(
            String id,
            String name,
            String text,
            String mode,
            int tMin,
            int tMax,
            int dt,
            Integer cooldown,
            boolean once,
            int minTurn) {}

    public record LoreCard(String id, String title, List<String> keys, String text) {}

    public record WorldEvent(String id, String text, String hook, String lore, int dt) {}

    public record Character(
            String id,
            String displayName,
            String speaker,
            String actorSystem,
            String directorTemplate,
            String judgeSystem,
            List<Move> moves,
            List<Beat> beats,
            List<LoreCard> lore,
            List<WorldEvent> events,
            List<String> emotions,
            Map<String, List<String>> fixedLines,
            String greetingWithName,
            String greetingNoName) {

        /** 감독 프롬프트의 {{MOVES}} 자리에 수 목록을 채운다(원본 JS와 같은 형식). */
        public String directorSystem() {
            String list = moves.stream()
                    .map(m -> m.id() + ": " + m.name() + " — " + m.how())
                    .collect(Collectors.joining("\n"));
            return directorTemplate.replace("{{MOVES}}", list);
        }

        public Move move(String id) {
            return moves.stream().filter(m -> m.id().equals(id)).findFirst().orElse(moves.get(0));
        }

        public boolean hasMove(String id) {
            return id != null && moves.stream().anyMatch(m -> m.id().equals(id));
        }

        public Optional<Beat> beat(String id) {
            return beats.stream().filter(b -> b.id().equals(id)).findFirst();
        }

        public Optional<LoreCard> loreCard(String id) {
            return lore.stream().filter(l -> l.id().equals(id)).findFirst();
        }

        public String greeting(String name) {
            return name == null || name.isBlank() ? greetingNoName : greetingWithName.replace("{name}", name);
        }
    }
}
