package com.capsule.session.internal;

import com.capsule.relation.api.RelationTypes.StudySettlement;
import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** api.md 11·12·16~18·21~23 요청·응답 모양 */
final class SessionDtos {

    private SessionDtos() {}

    record StartRequest(@NotNull Long roomId, @NotNull Long characterId, Integer targetMinutes) {}

    record EventRequest(String type, Instant occurredAt) {}

    record EndRequest(Integer studySeconds, Integer presentSeconds) {}

    record RoomView(long id, String name, String backgroundImageUrl) {}

    /** 캐릭터·아바타 공용. 아바타의 profileImageUrl = 아바타 썸네일 */
    record Figure(
            long id,
            String name,
            String profileImageUrl,
            String modelJsonUrl,
            double scale,
            int offsetX,
            int offsetY) {}

    record Me(String nickname, String profileImageUrl) {}

    record StartResponse(
            long sessionId,
            Instant startedAt,
            Integer targetMinutes,
            RoomView room,
            Figure character,
            Figure avatar,
            Me me,
            Instant serverTime) {}

    record ResumeResponse(
            long sessionId,
            Integer targetMinutes,
            RoomView room,
            Figure character,
            Figure avatar,
            Me me,
            int studySeconds,
            int breakSeconds,
            Instant serverTime) {}

    /** created: 새로 저장했으면 201, 중복·무시면 200 */
    record EventResponse(Long eventId, @JsonIgnore boolean created) {}

    record CharacterRef(long id, String name) {}

    record ReportResponse(
            long sessionId,
            String roomName,
            Instant startedAt,
            Instant endedAt,
            String endType,
            int studySeconds,
            int presentSeconds,
            int awaySeconds,
            int returnCount,
            CharacterRef character,
            StudySettlement relation,
            String closingLine) {}

    record HomeResponse(ActiveSessionSummary activeSession, List<HomeRoom> rooms) {}

    record HomeRoom(long id, String name, String thumbnailUrl, List<CharacterBrief> characters) {}

    record CharacterBrief(long id, String name, String profileImageUrl) {}

    record RoomDetail(
            long id,
            String name,
            String description,
            String thumbnailUrl,
            List<RoomCharacter> characters,
            Long activeSessionId) {}

    record RoomCharacter(
            long id, String name, String profileImageUrl, List<String> personality, RelationBrief relation) {}

    record RelationBrief(int level, String levelName, int totalStudySeconds) {}
}
