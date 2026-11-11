package com.capsule.dialogue.client;

import com.capsule.common.settings.RuntimeSettings;
import com.capsule.dialogue.config.LlmProperties;
import com.capsule.dialogue.domain.LlmUsage;
import com.capsule.dialogue.dto.LlmContext;
import com.capsule.dialogue.dto.LlmPurpose;
import com.capsule.dialogue.dto.ProviderRequest;
import com.capsule.dialogue.repository.LlmUsageRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * LLM 호출의 유일한 입구 (ADR 0022). 서비스·실험실은 용도(LlmPurpose)만 말하고, 여기서
 * ① 공급자·모델·한도를 정하고(설정 + 실험 덮어쓰기) ② 실패하면 재시도 → 대체 공급자 ③ 호출마다 llm_usages에 기록한다.
 * 기록이 실패해도 호출 결과는 그대로 돌려준다. 설정은 호출마다 지금 값(실험실 '운영 설정'에서 바꾼 값, ADR 0026)을 읽는다.
 */
@Component
public class LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(LlmGateway.class);

    private final Map<String, LlmProvider> providers;
    private final RuntimeSettings<LlmProperties> props;
    private final LlmUsageRepository usages;
    private final Clock clock;

    public LlmGateway(
            List<LlmProvider> providers, RuntimeSettings<LlmProperties> props, LlmUsageRepository usages, Clock clock) {
        this.providers = providers.stream().collect(Collectors.toMap(LlmProvider::name, Function.identity()));
        this.props = props;
        this.usages = usages;
        this.clock = clock;
        provider(props.defaults().provider()); // 설정 오타는 앱 시작 때 잡는다
    }

    public Llm.Result complete(
            LlmPurpose purpose,
            List<Llm.SystemBlock> system,
            List<Llm.ChatTurn> messages,
            LlmContext ctx,
            Consumer<String> onText) {
        // 호출 한 번은 한 사본으로: 저장하는 순간에 걸쳐도 용도(옛 값) · 모델(새 값)이 섞여 깨지지 않게
        LlmProperties settings = props.current();
        LlmProperties.Route route = settings.route(purpose);
        String primary = primary(settings, ctx, route);
        LlmCallException last = null;
        for (String name : order(settings, ctx, route)) {
            LlmProvider provider = provider(name);
            String model = ctx.model() != null && name.equals(primary) ? ctx.model() : settings.model(name, route);
            LlmProperties.Provider p = settings.providerSettings(name);
            ProviderRequest req = new ProviderRequest(
                    purpose,
                    model,
                    system,
                    messages,
                    ctx.maxTokens() != null ? ctx.maxTokens() : route.maxTokens(),
                    ctx.temperature() != null ? ctx.temperature() : route.temperature(),
                    p == null ? null : p.reasoningEffort(),
                    p == null ? null : p.thinkingFor(model)); // 표에 없는 모델(실험에서 고정한 모델 등)은 null = 안 보냄
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
                    if (!e.retryable() || attempt >= settings.maxRetries()) break;
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

    /** 공급자 이름 → 부를 수 있는지 (실험실 화면) */
    public Map<String, Boolean> readiness() {
        return providers.values().stream().collect(Collectors.toMap(LlmProvider::name, LlmProvider::ready));
    }

    public String defaultProvider() {
        return props.current().provider();
    }

    /** 실험 고정 > 용도의 공급자(예: DECIDE → jev) > 기본 공급자 */
    private static String primary(LlmProperties settings, LlmContext ctx, LlmProperties.Route route) {
        if (ctx.provider() != null) return ctx.provider();
        return present(route.provider()) ? route.provider() : settings.provider();
    }

    /**
     * 실험에서 공급자를 고정했으면 대체 공급자로 넘기지 않는다 (비교 실험이 섞이지 않게).
     * 용도의 공급자가 실패하면 기본 공급자 → 대체 공급자 순서로 한 번씩 (예: Jev가 죽어도 LLM이 결정한다)
     */
    private static List<String> order(LlmProperties settings, LlmContext ctx, LlmProperties.Route route) {
        String primary = primary(settings, ctx, route);
        if (ctx.provider() != null) return List.of(primary);
        return java.util.stream.Stream.of(primary, settings.provider(), settings.fallbackProvider())
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

    private void record(LlmContext ctx, ProviderRequest req, String provider, Llm.Result r, String error, long t0) {
        try {
            Llm.Usage u = r != null ? r.usage() : Llm.Usage.ZERO;
            Llm.Price price = props.current().price(req.model());
            boolean content = props.current().logContent() || !LlmContext.APP.equals(ctx.source());
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

    private void pause() {
        try {
            Thread.sleep(props.current().retryBackoff());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
