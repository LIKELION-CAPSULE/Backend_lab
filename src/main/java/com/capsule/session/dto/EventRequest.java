package com.capsule.session.dto;

import java.time.Instant;

public record EventRequest(String type, Instant occurredAt) {}
