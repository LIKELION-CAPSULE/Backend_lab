package com.capsule.user.service;

import com.capsule.common.error.ApiException;
import com.capsule.common.error.CommonErrorCode;
import com.capsule.common.ratelimit.RateLimiter;
import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.repository.AlertRequestRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 출시 알림 신청(api.md 45, 로그인 없이) · 준비 중 메이트 공개 알림 신청(46) */
@Service
@RequiredArgsConstructor
public class AlertRequestService {

    /** 로그인 없이 부르는 API라 같은 IP 1분 5번 (명세 45) */
    private static final int WAITLIST_LIMIT = 5;

    private final AlertRequestRepository requests;
    private final MateQuery mates;
    private final RateLimiter rateLimiter;

    /** 이미 신청한 이메일이어도 성공 (가입 여부처럼 신청 여부를 드러내지 않음) */
    @Transactional
    public void joinWaitlist(String rawEmail, String clientIp) {
        if (!rateLimiter.tryAcquire("rl:waitlist:" + clientIp, WAITLIST_LIMIT, Duration.ofMinutes(1))) {
            throw new ApiException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        String email = AuthService.normalize(rawEmail);
        if (!AuthService.isEmail(email)) {
            throw new ApiException(UserErrorCode.INVALID_EMAIL);
        }
        requests.joinWaitlist(email);
    }

    @Transactional
    public void requestMateAlert(long userId, long mateId) {
        MateInfo mate = mates.find(mateId)
                .filter(MateInfo::active)
                .orElseThrow(() -> new ApiException(UserErrorCode.MATE_NOT_FOUND));
        if (!mate.comingSoon()) {
            throw new ApiException(UserErrorCode.MATE_ALREADY_OPEN);
        }
        requests.requestMateAlert(userId, mateId);
    }
}
