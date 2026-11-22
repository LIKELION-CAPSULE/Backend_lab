package com.capsule.session.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** api.md 18. created: 새로 저장했으면 201, 재전송이면 200. reaction은 PRESENT면 null */
public record EventResponse(
        long eventId, ReactionView reaction, @JsonIgnore boolean created) {}
