package com.capsule.user.dto;

/** 보낸 값만 바꾼다 (api.md 10). honorific · aboutMe는 빈 문자열이면 지움, birthDate는 비어 있을 때 한 번만 */
public record UpdateMeRequest(String nickname, Long avatarId, String birthDate, String honorific, String aboutMe) {}
