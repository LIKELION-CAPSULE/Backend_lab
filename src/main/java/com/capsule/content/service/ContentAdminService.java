package com.capsule.content.service;

import com.capsule.common.error.ApiException;
import com.capsule.content.domain.ContentErrorCode;
import com.capsule.content.dto.ContentAdminRequests.AvatarSaved;
import com.capsule.content.dto.ContentAdminRequests.CreateAvatar;
import com.capsule.content.dto.ContentAdminRequests.CreateMate;
import com.capsule.content.dto.ContentAdminRequests.Expression;
import com.capsule.content.dto.ContentAdminRequests.MateImages;
import com.capsule.content.dto.ContentAdminRequests.MateSaved;
import com.capsule.content.dto.ContentAdminRequests.MateState;
import com.capsule.content.dto.ContentAdminRequests.MateVoice;
import com.capsule.content.dto.ContentAdminRequests.ModelRegistered;
import com.capsule.content.dto.ContentAdminRequests.Motion;
import com.capsule.content.dto.ContentAdminRequests.Reaction;
import com.capsule.content.dto.ContentAdminRequests.RegisterModel;
import com.capsule.content.dto.ContentAdminRequests.SetMateImages;
import com.capsule.content.dto.ContentAdminRequests.SetMateVoice;
import com.capsule.content.dto.ContentAdminRequests.SetRuntime;
import com.capsule.content.dto.ContentAdminRequests.UpdateAvatar;
import com.capsule.content.dto.ContentAdminRequests.UpdateMate;
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
        checkRuntime(format, r.enginePath(), r.playerPath(), r.reactionsPath());
        check(
                jdbc.sql("SELECT count(*) FROM live2d_models WHERE storage_prefix = ?")
                                .param(r.storagePrefix())
                                .query(Long.class)
                                .single()
                        == 0,
                "이미 등록된 경로: " + r.storagePrefix() + " (새 버전 폴더로 올릴 것)");

        long modelId = jdbc.sql("""
                        INSERT INTO live2d_models (storage_prefix, model_json_path, scale, offset_x, offset_y, format,
                                                   engine_path, player_path, reactions_path)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(
                        r.storagePrefix(),
                        json,
                        scale,
                        nz(r.offsetX()),
                        nz(r.offsetY()),
                        format,
                        blank(r.enginePath()),
                        blank(r.playerPath()),
                        blank(r.reactionsPath()))
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

    /** 이미 등록된 모델의 Aliving2D 런타임 경로를 바꾼다 (운영 채린처럼 엔진 칸 전에 등록된 모델, 2026-11-25) */
    @Transactional
    public void setRuntime(long modelId, SetRuntime r) {
        String format = jdbc.sql("SELECT format FROM live2d_models WHERE id = ?")
                .param(modelId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new ApiException(ContentErrorCode.MODEL_NOT_FOUND));
        checkRuntime(format, r.enginePath(), r.playerPath(), r.reactionsPath());
        jdbc.sql("UPDATE live2d_models SET engine_path = ?, player_path = ?, reactions_path = ? WHERE id = ?")
                .params(blank(r.enginePath()), blank(r.playerPath()), blank(r.reactionsPath()), modelId)
                .update();
        log.info(
                "콘텐츠 관리: 모델 {} 런타임 engine={} player={} reactions={}",
                modelId,
                r.enginePath(),
                r.playerPath(),
                r.reactionsPath());
    }

    static final Pattern IMAGE = Pattern.compile("(?i).+\\.(png|jpe?g|webp)");
    static final Pattern AUDIO = Pattern.compile("(?i).+\\.mp3");

    /** 메이트 이미지 · 목소리 샘플 경로 (Aliving2D가 버킷에 올린 뒤, 2026-11-25). null = 그대로, "" = 지움 */
    @Transactional
    public MateImages setMateImages(long mateId, SetMateImages r) {
        checkAsset(r.illustrationPath(), IMAGE, "illustrationPath는 버킷 안 png · jpg · webp 경로");
        checkAsset(r.cardImagePath(), IMAGE, "cardImagePath는 버킷 안 png · jpg · webp 경로");
        checkAsset(r.voiceSamplePath(), AUDIO, "voiceSamplePath는 버킷 안 mp3 경로");
        int changed = jdbc.sql("""
                        UPDATE characters
                        SET illustration_url = CASE WHEN ? THEN illustration_url ELSE ? END,
                            card_image_url   = CASE WHEN ? THEN card_image_url ELSE ? END,
                            voice_sample_url = CASE WHEN ? THEN voice_sample_url ELSE ? END
                        WHERE id = ?""")
                .params(
                        r.illustrationPath() == null,
                        blank(r.illustrationPath()),
                        r.cardImagePath() == null,
                        blank(r.cardImagePath()),
                        r.voiceSamplePath() == null,
                        blank(r.voiceSamplePath()),
                        mateId)
                .update();
        if (changed == 0) throw new ApiException(ContentErrorCode.MATE_NOT_FOUND);
        MateImages saved = jdbc.sql(
                        "SELECT id, illustration_url, card_image_url, voice_sample_url FROM characters WHERE id = ?")
                .param(mateId)
                .query((rs, n) -> new MateImages(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .single();
        log.info(
                "콘텐츠 관리: 메이트 {} 이미지 illustration={} card={} voice={}",
                mateId,
                saved.illustrationPath(),
                saved.cardImagePath(),
                saved.voiceSamplePath());
        return saved;
    }

    static final Pattern VOICE = Pattern.compile("[a-z][a-z0-9_-]{0,39}");
    static final Pattern TTS_MODEL = Pattern.compile("[a-z0-9][a-z0-9._-]{0,59}");

    /** 메이트 목소리 · 샘플 (ADR 0036). 네 칸을 통째로 바꾼다 (null · 빈 값 = 비움 → 운영 설정 기본값) */
    @Transactional
    public MateVoice setMateVoice(long mateId, SetMateVoice r) {
        String voice = blank(r.voice()), model = blank(r.model()), instructions = blank(r.instructions());
        check(voice == null || VOICE.matcher(voice).matches(), "voice는 영문 소문자 이름 (예: coral)");
        check(model == null || TTS_MODEL.matcher(model).matches(), "model은 영문 소문자 · 숫자 이름 (예: gpt-4o-mini-tts)");
        check(instructions == null || instructions.length() <= 1000, "instructions는 1000자까지");
        checkAsset(r.samplePath(), AUDIO, "samplePath는 버킷 안 mp3 경로");
        String sample = blank(r.samplePath());
        int changed =
                jdbc.sql("""
                        UPDATE characters SET tts_voice = ?, tts_model = ?, tts_instructions = ?, voice_sample_url = ?
                        WHERE id = ?""").params(voice, model, instructions, sample, mateId).update();
        if (changed == 0) throw new ApiException(ContentErrorCode.MATE_NOT_FOUND);
        log.info("콘텐츠 관리: 메이트 {} 목소리 voice={} model={} sample={}", mateId, voice, model, sample);
        return new MateVoice(mateId, voice, model, instructions, sample);
    }

    /** null(그대로) · 빈 값(지움)은 통과. 아니면 버킷 안 상대 경로 + 확장자 */
    private static void checkAsset(String path, Pattern type, String reason) {
        if (blank(path) == null) return;
        String p = path.strip();
        check(FILE.matcher(p).matches() && type.matcher(p).matches(), reason + ": " + path);
    }

    /** 아바타 (api.md 9). 모델은 다른 메이트 · 아바타가 쓰지 않아야 함 */
    @Transactional
    public AvatarSaved createAvatar(CreateAvatar a) {
        check(text(a.name(), 40), "아바타 name은 1~40자");
        check(a.modelId() != null, "modelId가 필요");
        check(a.thumbnailPath() != null && FILE.matcher(a.thumbnailPath()).matches(), "thumbnailPath는 버킷 안 상대 경로");
        usable(a.modelId(), null);
        int order = a.sortOrder() != null
                ? a.sortOrder()
                : jdbc.sql("SELECT coalesce(max(sort_order), 0) + 1 FROM avatars")
                        .query(Integer.class)
                        .single();
        long id = jdbc.sql("""
                        INSERT INTO avatars (model_id, name, thumbnail_url, sort_order)
                        VALUES (?, ?, ?, ?) RETURNING id""")
                .params(a.modelId(), a.name(), a.thumbnailPath(), order)
                .query(Long.class)
                .single();
        log.info("콘텐츠 관리: 아바타 만듦 id={} name={} model={}", id, a.name(), a.modelId());
        return new AvatarSaved(id, a.modelId(), true, order);
    }

    /** 메이트 켜고 끄기 · 순서 (대화 · 친밀도 기록이 있으니 지우지 않음, 2026-11-26) */
    @Transactional
    public MateState updateMate(long mateId, UpdateMate u) {
        int changed = jdbc.sql("""
                        UPDATE characters SET is_active = coalesce(?, is_active), sort_order = coalesce(?, sort_order)
                        WHERE id = ?""").params(u.active(), u.sortOrder(), mateId).update();
        if (changed == 0) throw new ApiException(ContentErrorCode.MATE_NOT_FOUND);
        log.info("콘텐츠 관리: 메이트 {} active={} sort={}", mateId, u.active(), u.sortOrder());
        return jdbc.sql("SELECT id, is_active, sort_order FROM characters WHERE id = ?")
                .param(mateId)
                .query((rs, n) -> new MateState(rs.getLong(1), rs.getBoolean(2), rs.getInt(3)))
                .single();
    }

    /** 아바타 켜고 끄기 · 순서 (고른 사용자가 있으니 지우지 않음) */
    @Transactional
    public AvatarSaved updateAvatar(long avatarId, UpdateAvatar u) {
        int changed = jdbc.sql("""
                        UPDATE avatars SET is_active = coalesce(?, is_active), sort_order = coalesce(?, sort_order)
                        WHERE id = ?""").params(u.active(), u.sortOrder(), avatarId).update();
        if (changed == 0) throw new ApiException(ContentErrorCode.AVATAR_NOT_FOUND);
        log.info("콘텐츠 관리: 아바타 {} active={} sort={}", avatarId, u.active(), u.sortOrder());
        return jdbc.sql("SELECT id, model_id, is_active, sort_order FROM avatars WHERE id = ?")
                .param(avatarId)
                .query((rs, n) -> new AvatarSaved(rs.getLong(1), rs.getLong(2), rs.getBoolean(3), rs.getInt(4)))
                .single();
    }

    /** 런타임 경로: 버킷 안 상대 경로. CUBISM 모델은 런타임 없음. player · reactions는 engine이 있을 때만 */
    private static void checkRuntime(String format, String engine, String player, String reactions) {
        for (String p : new String[] {engine, player, reactions}) {
            if (blank(p) == null) continue;
            check(!"CUBISM".equals(format), "CUBISM 모델은 엔진 경로를 넣지 않음");
            check(FILE.matcher(p.strip()).matches(), "엔진 경로는 버킷 안 상대 경로: " + p);
        }
        check(
                (blank(player) == null && blank(reactions) == null) || blank(engine) != null,
                "playerPath · reactionsPath는 enginePath가 있을 때만");
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.strip();
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
