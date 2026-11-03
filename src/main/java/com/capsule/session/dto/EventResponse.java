package com.capsule.session.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** created: 새로 저장했으면 201, 중복·무시면 200 */
public record EventResponse(Long eventId, @JsonIgnore boolean created) {}
