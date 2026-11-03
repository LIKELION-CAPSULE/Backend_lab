package com.capsule.user.internal.controller;

import com.capsule.common.security.LoginUser;
import com.capsule.user.internal.domain.UserMemory;
import com.capsule.user.internal.dto.CreateMemoryRequest;
import com.capsule.user.internal.dto.MeResponse;
import com.capsule.user.internal.dto.MemoryList;
import com.capsule.user.internal.dto.MemoryResponse;
import com.capsule.user.internal.dto.UpdateMeRequest;
import com.capsule.user.internal.service.MemoryService;
import com.capsule.user.internal.service.MemoryService.Status;
import com.capsule.user.internal.service.ProfileService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** api.md 8, 10, 13, 14 */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final ProfileService profiles;
    private final MemoryService memories;

    MeController(ProfileService profiles, MemoryService memories) {
        this.profiles = profiles;
        this.memories = memories;
    }

    @GetMapping
    MeResponse me(@LoginUser long userId) {
        return profiles.me(userId);
    }

    @PatchMapping
    MeResponse update(@LoginUser long userId, @RequestBody UpdateMeRequest req) {
        return profiles.update(userId, req);
    }

    @PostMapping("/memories")
    @ResponseStatus(HttpStatus.CREATED)
    MemoryResponse createMemory(@LoginUser long userId, @RequestBody CreateMemoryRequest req) {
        return memories.create(userId, req);
    }

    @GetMapping("/memories")
    MemoryList memories(
            @LoginUser long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "UPCOMING") Status status,
            @RequestParam(required = false) UserMemory.Type type) {
        return memories.list(userId, from, to, date, status, type);
    }
}
