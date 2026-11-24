package com.capsule.user.adapter;

import com.capsule.user.api.NotificationCommands;
import com.capsule.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** NotificationCommands 계약 구현 (다른 계약 어댑터와 같이 얇게) */
@Service
@RequiredArgsConstructor
public class NotificationCommandsAdapter implements NotificationCommands {

    private final NotificationService notifications;

    @Override
    public void stageUp(
            long userId, long connectionId, String mateName, int stage, String stageName, boolean unlocked) {
        notifications.stageUp(userId, connectionId, mateName, stage, stageName, unlocked);
    }
}
