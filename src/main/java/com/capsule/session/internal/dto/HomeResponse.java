package com.capsule.session.internal.dto;

import com.capsule.user.api.UserTypes.ActiveSessionSummary;
import java.util.List;

public record HomeResponse(ActiveSessionSummary activeSession, List<HomeRoom> rooms) {}
