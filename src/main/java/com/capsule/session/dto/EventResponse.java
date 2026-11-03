package com.capsule.session.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/** created: 새로 저장했으면 201, 중복·무시면 200. 휴식 중이라 무시된 이벤트는 eventId null, characters 빈 배열 */
public record EventResponse(
        Long eventId,
        List<EventCharacter> characters,
        @JsonIgnore boolean created) {

    public static EventResponse ignored() {
        return new EventResponse(null, List.of(), false);
    }
}
