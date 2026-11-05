package com.capsule.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capsule.TestcontainersConfiguration;
import com.capsule.content.api.AvatarQuery;
import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.AvatarInfo;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.ModelInfo;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ContentTypes.RoomInfo;
import com.capsule.content.api.ReactionQuery;
import com.capsule.content.api.RoomQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 시드(db/seed/R__demo_content.sql) 기준 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ContentTest {

    static final ExpressionRef POUT = new ExpressionRef("삐짐", "exp/expression1.exp3.json");
    static final ExpressionRef SMILE = new ExpressionRef("웃음", "exp/expression6.exp3.json");
    static final ExpressionRef SHY = new ExpressionRef("수줍음", "exp/expression7.exp3.json");

    @Autowired
    MockMvc mvc;

    @Autowired
    AvatarQuery avatarQuery;

    @Autowired
    RoomQuery roomQuery;

    @Autowired
    CharacterQuery characterQuery;

    @Autowired
    ReactionQuery reactionQuery;

    @Test
    void avatars() throws Exception {
        mvc.perform(get("/api/avatars").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatars[0].id").value(1))
                .andExpect(jsonPath("$.avatars[0].name").value("기본 아바타"))
                // DB에는 상대 경로, 응답은 CDN 주소(테스트는 기본값 cdn.capsule.invalid)를 붙인 값
                .andExpect(
                        jsonPath("$.avatars[0].thumbnailUrl").value("https://cdn.capsule.invalid/haru/thumbnail.png"))
                .andExpect(jsonPath("$.avatars[0].active").doesNotExist())
                .andExpect(jsonPath("$.avatars[0].model.modelJsonUrl")
                        .value("https://cdn.capsule.invalid/haru/v1/Haru.model3.json"))
                .andExpect(jsonPath("$.avatars[0].model.scale").value(1.0))
                .andExpect(jsonPath("$.avatars[0].model.offsetX").value(0))
                .andExpect(jsonPath("$.avatars[0].model.offsetY").value(0));
    }

    @Test
    void avatarsNeedLogin() throws Exception {
        mvc.perform(get("/api/avatars"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void defaultAvatar() {
        assertThat(avatarQuery.defaultAvatar()).map(AvatarInfo::id).contains(1L);
    }

    @Test
    void rooms() {
        assertThat(roomQuery.activeRooms()).extracting(RoomInfo::id).containsExactly(1L);

        RoomInfo room = roomQuery.find(1).orElseThrow();
        assertThat(room.name()).isEqualTo("밤의 호그와트 도서관");
        assertThat(room.characters()).extracting(CharacterInfo::name).containsExactly("말포이");
        assertThat(room.thumbnailUrl())
                .isEqualTo("https://cdn.capsule.invalid/images/malfoy-ts/hogwarts-night-poster-v1.webp");
        assertThat(room.bannerUrl())
                .isEqualTo("https://cdn.capsule.invalid/images/malfoy-ts/hogwarts-night-detail-banner-v1.webp");
        assertThat(room.characters().get(0).profileImageUrl())
                .isEqualTo("https://cdn.capsule.invalid/images/malfoy-ts/malfoy-ts-study-v1.webp");

        assertThat(roomQuery.defaultBackground(1))
                .contains(new BackgroundInfo(
                        1,
                        1,
                        "밤의 도서관",
                        "https://cdn.capsule.invalid/images/malfoy-ts/hogwarts-night-background-v1.webp"));
        assertThat(roomQuery.find(999)).isEmpty();
    }

    @Test
    void character() {
        CharacterInfo malfoy = characterQuery.find(1).orElseThrow();
        assertThat(malfoy.roomId()).isEqualTo(1);
        assertThat(malfoy.personality()).containsExactly("츤데레", "슬리데린 5학년", "라이벌");
        assertThat(malfoy.model())
                .isEqualTo(new ModelInfo("https://cdn.capsule.invalid/chaerin/v1/chaerin.model3.json", 1.0, 0, 12));
    }

    @Test
    void reactionExactMatch() {
        // 말포이 모델은 모션 파일이 없어 표정만 (api.md 19)
        assertThat(reactionQuery.pick(1, "GREETING", null)).isEqualTo(new Reaction(null, SMILE));
        assertThat(reactionQuery.pick(1, "AWAY", 2)).isEqualTo(new Reaction(null, POUT));
        assertThat(reactionQuery.pick(1, "AWAY", 3)).isEqualTo(new Reaction(null, POUT));
    }

    @Test
    void reactionByEmotionName() {
        // 19 · 20: LLM이 고른 감정 이름으로 같은 이름의 표정 (#9)
        assertThat(reactionQuery.pick(1, "수줍음", null)).isEqualTo(new Reaction(null, SHY));
        assertThat(reactionQuery.pick(1, "웃음", null)).isEqualTo(new Reaction(null, SMILE));
        assertThat(reactionQuery.pick(1, "찡그림", null)).isEqualTo(Reaction.NONE); // 에셋 없는 표정
    }

    @Test
    void reactionFallsBackToNullIntensity() {
        // (GREETING, 2) 행은 없고 (GREETING, NULL)만 있음
        assertThat(reactionQuery.pick(1, "GREETING", 2)).isEqualTo(new Reaction(null, SMILE));
    }

    @Test
    void reactionNone() {
        assertThat(reactionQuery.pick(1, "NO_SUCH_TRIGGER", null)).isEqualTo(Reaction.NONE);
        assertThat(reactionQuery.pick(999, "GREETING", null)).isEqualTo(Reaction.NONE);
    }
}
