package com.capsule.session.dto;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.util.List;

/** todayStudySeconds = 오늘(한국 날짜) 끝낸 세션의 공부 시간 합. 진행 중 세션은 빠짐 */
public record HomeResponse(int todayStudySeconds, ActiveSessionSummary activeSession, List<HomeRoom> rooms) {}
