package com.capsule.dialogue.client;

import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.domain.LlmUsage;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.ProviderRequest;
import com.capsule.dialogue.engine.Llm;
import com.capsule.dialogue.repository.LlmUsageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM 호출의 유일한 입구 (ADR 0021). 서비스·엔진·실험실은 용도(LlmPurpose)만 말하고, 여기서
 * ① 공급자·모델·한도를 정하고(설정 + 실험 덮어쓰기) ② 실패하면 재시도 → 대체 공급자 ③ 호출마다 llm_usages에 기록한다.
 * 기록이 실패해도 호출 결과는 그대로 돌려준다.
 */
@Component
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);
    private static final long RETRY_BACKOFF_MS = 300;

    private final Map<String, LlmProvider> providers;
    private final LlmProperties props;
    private final LlmUsageRepository usages;
    private final Clock clock;

    LlmGateway(List<LlmProvider> providers, LlmProperties props, LlmUsageRepository usages, Clock clock) {
        this.providers = providers.stream().collect(Collectors.toMap(LlmProvider::name, Function.identity()));
        this.props = props;
        this.usages = usages;
        this.clock = clock;
        provider(props.provider()); // 설정 오타는 앱 시작 때 잡는다
    }

    public Llm.Result complete(
            LlmPurpose purpose,
            List<Llm.SystemBlock> system,
            List<Llm.ChatTurn> messages,
            LlmContext ctx,
            Consumer<String> onText) {
        LlmProperties.Route route = props.route(purpose);
        String primary = primary(ctx, route);
        LlmCallException last = null;
        for (String name : order(ctx, route)) {
            LlmProvider provider = provider(name);
            ProviderRequest req = new ProviderRequest(
                    purpose,
                    ctx.model() != null && name.equals(primary) ? ctx.model() : props.model(name, route),
                    system,
                    messages,
                    ctx.maxTokens() != null ? ctx.maxTokens() : route.maxTokens(),
                    ctx.temperature() != null ? ctx.temperature() : route.temperature(),
                    reasoningEffort(name));
            for (int attempt = 0; ; attempt++) {
                if (ctx.deadline() != null && !Instant.now(clock).isBefore(ctx.deadline())) {
                    // 서비스가 이미 대체 대사로 넘어갔다: 결과가 버려질 호출은 시작하지 않는다 (기록할 호출도 없음)
                    throw last != null ? last : LlmCallException.deadlinePassed(name);
                }
                long t0 = System.nanoTime();
                Llm.Result r;
                try {
                    r = provider.complete(req, onText);
                } catch (LlmCallException e) {
                    record(ctx, req, name, null, e.getMessage(), t0);
                    last = e;
                    if (!e.retryable() || attempt >= props.maxRetries()) break;
                    pause();
                    continue;
                } catch (RuntimeException e) {
                    record(ctx, req, name, null, e.toString(), t0);
                    last = LlmCallException.unexpected(name, e);
                    break;
                }
                if (LlmCallException.REFUSAL.equals(r.stopReason())) {
                    // 거절은 다른 공급자로 넘기지 않는다: 안전 판단을 우회하게 되므로 바로 대체 대사로
                    record(ctx, req, name, r, LlmCallException.REFUSAL, t0);
                    throw LlmCallException.refusal(name);
                }
                record(ctx, req, name, r, null, t0);
                return r;
            }
        }
        throw last;
    }

    /**
     * 대화 엔진용 포트. 엔진의 역할(감독·배우·심사)을 용도로 바꿔 complete로 보낸다.
     * 모델은 엔진이 적은 이름 대신 설정(routes)을 따르고, 토큰 한도는 엔진이 정한 값(Engine.Settings)을 쓴다.
     */
    public Llm.Client engineClient(LlmContext ctx) {
        return new Llm.Client() {
            @Override
            public Llm.Result call(Llm.Call c) {
                return complete(
                        purposeOf(c.role()), c.system(), c.messages(), ctx.withMaxTokensIfAbsent(c.maxTokens()), null);
            }

            @Override
            public Llm.Result stream(Llm.Call c, Consumer<String> onText) {
                return complete(
                        purposeOf(c.role()),
                        c.system(),
                        c.messages(),
                        ctx.withMaxTokensIfAbsent(c.maxTokens()),
                        onText);
            }
        };
    }

    /** 이 맥락에서 이 용도에 실제로 쓸 모델 (엔진 비용 계산·실험실 표시용) */
    public String modelFor(LlmPurpose purpose, LlmContext ctx) {
        LlmProperties.Route route = props.route(purpose);
        return ctx.model() != null ? ctx.model() : props.model(primary(ctx, route), route);
    }

    /** 엔진 비용 계산용 가격표: 기본 가격(Llm.DEFAULT_PRICES) 위에 설정 가격 */
    public Map<String, Llm.Price> prices() {
        Map<String, Llm.Price> all = new HashMap<>(Llm.DEFAULT_PRICES);
        if (props.prices() != null) all.putAll(props.prices());
        return all;
    }

    /** 공급자 이름 → 부를 수 있는지 (실험실 화면) */
    public Map<String, Boolean> readiness() {
        return providers.values().stream().collect(Collectors.toMap(LlmProvider::name, LlmProvider::ready));
    }

    public String defaultProvider() {
        return props.provider();
    }

    /** 실험 고정 > 용도의 공급자(예: DECIDE → jev) > 기본 공급자 */
    private String primary(LlmContext ctx, LlmProperties.Route route) {
        if (ctx.provider() != null) return ctx.provider();
        return present(route.provider()) ? route.provider() : props.provider();
    }

    /**
     * 실험에서 공급자를 고정했으면 대체 공급자로 넘기지 않는다 (비교 실험이 섞이지 않게).
     * 용도의 공급자가 실패하면 기본 공급자 → 대체 공급자 순서로 한 번씩 (예: Jev가 죽어도 LLM이 결정한다)
     */
    private List<String> order(LlmContext ctx, LlmProperties.Route route) {
        String primary = primary(ctx, route);
        if (ctx.provider() != null) return List.of(primary);
        return java.util.stream.Stream.of(primary, props.provider(), props.fallbackProvider())
                .filter(LlmGateway::present)
                .distinct()
                .toList();
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private LlmProvider provider(String name) {
        LlmProvider p = providers.get(name);
        if (p == null) throw new IllegalStateException("모르는 LLM 공급자: " + name + " (있는 것: " + providers.keySet() + ")");
        return p;
    }

    private String reasoningEffort(String providerName) {
        LlmProperties.Provider p = props.providerSettings(providerName);
        return p == null ? null : p.reasoningEffort();
    }

    private void record(LlmContext ctx, ProviderRequest req, String provider, Llm.Result r, String error, long t0) {
        try {
            Llm.Usage u = r != null ? r.usage() : Llm.Usage.ZERO;
            Llm.Price price = props.price(req.model());
            boolean content = props.logContent() || !LlmContext.APP.equals(ctx.source());
            usages.save(new LlmUsage(
                    ctx.userId(),
                    ctx.sessionId(),
                    req.purpose().name(),
                    ctx.source(),
                    ctx.traceId(),
                    provider,
                    req.model(),
                    error,
                    r != null ? r.stopReason() : null,
                    u.input(),
                    u.output(),
                    u.cacheRead(),
                    u.cacheWrite(),
                    BigDecimal.valueOf(price == null ? 0 : price.cost(u)).setScale(6, RoundingMode.HALF_UP),
                    (int) (r != null ? r.totalMs() : LlmHttp.msSince(t0)),
                    r != null && r.ttftMs() != null ? r.ttftMs().intValue() : null,
                    ctx.promptVersionIds(),
                    content ? LlmJson.write(req) : null,
                    content && r != null ? r.text() : null,
                    Instant.now(clock)));
        } catch (RuntimeException e) {
            log.warn("llm_usages 기록 실패 (호출 결과는 그대로 씀)", e);
        }
    }

    private static LlmPurpose purposeOf(Llm.Role role) {
        return switch (role) {
            case DIRECTOR -> LlmPurpose.DIRECTOR;
            case ACTOR -> LlmPurpose.ACTOR;
            case JUDGE -> LlmPurpose.JUDGE;
        };
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
