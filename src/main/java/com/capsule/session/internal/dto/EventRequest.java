package com.capsule.session.internal.dto;

import java.time.Instant;

public record EventRequest(String type, Instant occurredAt) {}
