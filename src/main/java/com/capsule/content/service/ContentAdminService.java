package com.capsule.content.service;

import com.capsule.common.error.ApiException;
import com.capsule.content.domain.ContentErrorCode;
import com.capsule.content.dto.ContentAdminRequests.CreateMate;
import com.capsule.content.dto.ContentAdminRequests.Expression;
import com.capsule.content.dto.ContentAdminRequests.MateSaved;
import com.capsule.content.dto.ContentAdminRequests.ModelRegistered;
import com.capsule.content.dto.ContentAdminRequests.Motion;
import com.capsule.content.dto.ContentAdminRequests.Reaction;
import com.capsule.content.dto.ContentAdminRequests.RegisterModel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리용 API(ADR 0033)의 쓰기. 엔티티는 읽기 전용이라 행은 SQL로 넣는다. 넣기 전에 값을 모두 검사하고, 한 요청은 한 트랜잭션.
 * 토큰이 설정됐을 때만 생긴다(ContentAdminSecurityConfig)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "capsule.content.admin.token")
public class ContentAdminService {

    static final Set<String> FORMATS = Set.of("CUBISM", "ALIVING2D_V2");
    /** 버킷 경로 <이름>/v<번호>/ (영문 소문자 · 숫자, ADR 0024: 한글 · 공백 키는 Storage가 거부) */
    static final Pattern PREFIX = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}/v[0-9]{1,4}/");
    /** prefix 안의 상대 경로: 영문 · 숫자 · _ · - · . · /, '..'와 맨 앞 '/' 금지 */
    static final Pattern FILE = Pattern.compile("(?!/)(?!.*\\.\\.)[A-Za-z0-9_./-]{1,200}");

    private final JdbcClient jdbc;

    public ContentAdminService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Transactional
    public ModelRegistered registerModel(RegisterModel r) {
        String format = r.format() == null || r.format().isBlank() ? "ALIVING2D_V2" : r.format();
        check(FORMATS.contains(format), "format은 CUBISM 또는 ALIVING2D_V2");
        check(
                r.storagePrefix() != null && PREFIX.matcher(r.storagePrefix()).matches(),
                "storagePrefix는 <영문 이름>/v<번호>/ (예: male/v1/)");
        String json = r.modelJsonPath() == null || r.modelJsonPath().isBlank() ? "model.json" : r.modelJsonPath();
        check(FILE.matcher(json).matches(), "modelJsonPath는 prefix 안의 상대 경로");
        double scale = r.scale() == null ? 1.0 : r.scale();
        check(scale > 0.05 && scale < 20, "scale은 0.05~20");
        List<Expression> ex = r.expressions() == null ? List.of() : r.expressions();
        List<Motion> mo = r.motions() == null ? List.of() : r.motions();
        List<Reaction> re = r.reactions() == null ? List.of() : r.reactions();
        Set<String> exNames = new HashSet<>(), moNames = new HashSet<>();
        for (Expression e : ex) {
            check(text(e.name(), 40) && exNames.add(e.name()), "표정 name이 비었거나 겹침: " + e.name());
            check(e.label() == null || e.label().length() <= 40, "표정 label은 40자까지");
            check(e.file() != null && FILE.matcher(e.file()).matches(), "표정 file은 prefix 안의 상대 경로: " + e.file());
        }
        for (Motion m : mo) {
            check(text(m.group(), 40), "모션 group이 비었음");
            check(text(m.name(), 40) && moNames.add(m.name()), "모션 name이 비었거나 겹침: " + m.name());
            check(m.label() == null || m.label().length() <= 40, "모션 label은 40자까지");
            check(m.file() != null && FILE.matcher(m.file()).matches(), "모션 file은 prefix 안의 상대 경로: " + m.file());
        }
        for (Reaction x : re) {
            check(text(x.trigger(), 60), "반응 trigger가 비었음");
            check(x.intensity() == null || (x.intensity() >= 1 && x.intensity() <= 3), "반응 intensity는 1~3 또는 null");
            check(x.expression() != null || x.motion() != null, "반응은 표정이나 모션 중 하나는 있어야 함: " + x.trigger());
            check(x.expression() == null || exNames.contains(x.expression()), "반응이 가리키는 표정이 없음: " + x.expression());
            check(x.motion() == null || moNames.contains(x.motion()), "반응이 가리키는 모션이 없음: " + x.motion());
        }
        check(
                jdbc.sql("SELECT count(*) FROM live2d_models WHERE storage_prefix = ?")
                                .param(r.storagePrefix())
                                .query(Long.class)
                                .single()
                        == 0,
                "이미 등록된 경로: " + r.storagePrefix() + " (새 버전 폴더로 올릴 것)");

        long modelId = jdbc.sql("""
                        INSERT INTO live2d_models (storage_prefix, model_json_path, scale, offset_x, offset_y, format)
                        VALUES (?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(r.storagePrefix(), json, scale, nz(r.offsetX()), nz(r.offsetY()), format)
                .query(Long.class)
                .single();
        Map<String, Long> exIds = new HashMap<>(), moIds = new HashMap<>();
        for (Expression e : ex)
            exIds.put(
                    e.name(),
                    jdbc.sql("""
                            INSERT INTO live2d_expressions (model_id, name, label, file_path)
                            VALUES (?, ?, ?, ?) RETURNING id""")
                            .params(modelId, e.name(), e.label(), e.file())
                            .query(Long.class)
                            .single());
        for (Motion m : mo)
            moIds.put(
                    m.name(),
                    jdbc.sql("""
                            INSERT INTO live2d_motions (model_id, group_name, name, label, file_path)
                            VALUES (?, ?, ?, ?, ?) RETURNING id""")
                            .params(modelId, m.group(), m.name(), m.label(), m.file())
                            .query(Long.class)
                            .single());
        for (Reaction x : re)
            jdbc.sql("""
                            INSERT INTO live2d_reaction_maps (model_id, trigger_type, intensity, motion_id, expression_id)
                            VALUES (?, ?, ?, ?, ?)""")
                    .params(modelId, x.trigger(), x.intensity(), moIds.get(x.motion()), exIds.get(x.expression()))
                    .update();
        log.info(
                "콘텐츠 관리: 모델 등록 id={} prefix={} format={} 표정 {} 모션 {} 반응 {}",
                modelId,
                r.storagePrefix(),
                format,
                ex.size(),
                mo.size(),
                re.size());
        return new ModelRegistered(modelId, r.storagePrefix(), ex.size(), mo.size(), re.size());
    }

    @Transactional
    public MateSaved createMate(CreateMate m) {
        check(text(m.name(), 40), "메이트 name은 1~40자");
        check(m.oneLiner() == null || m.oneLiner().length() <= 100, "oneLiner는 100자까지");
        check(m.intro() == null || m.intro().length() <= 2000, "intro는 2000자까지");
        List<String> tags = m.hashtags() == null ? List.of() : m.hashtags();
        check(tags.size() <= 10 && tags.stream().allMatch(t -> text(t, 20)), "hashtags는 10개까지, 하나에 20자까지");
        if (m.modelId() != null) usable(m.modelId(), null);
        boolean comingSoon = m.modelId() == null;
        long id = jdbc.sql("""
                        INSERT INTO characters (name, one_liner, intro, hashtags, model_id, coming_soon, sort_order)
                        VALUES (?, ?, ?, ?, ?, ?, (SELECT coalesce(max(sort_order), 0) + 1 FROM characters))
                        RETURNING id""")
                .params(m.name(), m.oneLiner(), m.intro(), tags.toArray(String[]::new), m.modelId(), comingSoon)
                .query(Long.class)
                .single();
        log.info("콘텐츠 관리: 메이트 만듦 id={} name={} model={}", id, m.name(), m.modelId());
        return new MateSaved(id, m.modelId(), comingSoon);
    }

    /** 메이트의 모델을 바꾼다(새 버전). 앞 모델 행은 기록으로 남는다 */
    @Transactional
    public MateSaved setModel(long mateId, Long modelId) {
        check(modelId != null, "modelId가 필요");
        boolean exists = jdbc.sql("SELECT count(*) FROM characters WHERE id = ?")
                        .param(mateId)
                        .query(Long.class)
                        .single()
                > 0;
        if (!exists) throw new ApiException(ContentErrorCode.MATE_NOT_FOUND);
        usable(modelId, mateId);
        jdbc.sql("UPDATE characters SET model_id = ?, coming_soon = false WHERE id = ?")
                .params(modelId, mateId)
                .update();
        log.info("콘텐츠 관리: 메이트 {}의 모델 → {}", mateId, modelId);
        return new MateSaved(mateId, modelId, false);
    }

    /** 있는 모델이고, 다른 메이트 · 아바타가 쓰지 않음 */
    private void usable(long modelId, Long forMate) {
        boolean exists = jdbc.sql("SELECT count(*) FROM live2d_models WHERE id = ?")
                        .param(modelId)
                        .query(Long.class)
                        .single()
                > 0;
        if (!exists) throw new ApiException(ContentErrorCode.MODEL_NOT_FOUND);
        long used = jdbc.sql("""
                        SELECT (SELECT count(*) FROM characters WHERE model_id = ? AND id <> coalesce(?, -1))
                             + (SELECT count(*) FROM avatars WHERE model_id = ?)""")
                .params(modelId, forMate, modelId)
                .query(Long.class)
                .single();
        if (used > 0) throw new ApiException(ContentErrorCode.MODEL_IN_USE);
    }

    private static boolean text(String s, int max) {
        return s != null && !s.isBlank() && s.length() <= max;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static void check(boolean ok, String reason) {
        if (!ok) throw new ApiException(ContentErrorCode.INVALID_CONTENT, Map.of("reason", reason));
    }
}
