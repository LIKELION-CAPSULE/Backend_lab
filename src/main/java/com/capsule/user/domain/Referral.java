package com.capsule.user.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * referrals 테이블 (api.md 35 · 36). 받은 사람(invitee)당 한 행. 추가는 ReferralRepository.insertIfAbsent로만 한다
 * (같은 사람이 동시에 두 번 입력해도 409 하나로 끝나게)
 */
@Entity
@Table(name = "referrals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long inviterId;
    private long inviteeId;
    private String deviceHash;
    private Instant enteredAt;
    private Instant qualifiedAt;
    private Long qualifiedConnectionId;
    private boolean inviterRewarded;
    private boolean inviteeRewarded;

    public boolean isQualified() {
        return qualifiedAt != null;
    }

    /** 입력한 뒤 처음 끝난 연결 (22). 받은 사람 몫은 항상, 초대한 사람 몫은 inviterRewarded일 때 */
    public void qualify(long connectionId, Instant at, boolean inviterRewarded) {
        this.qualifiedAt = at;
        this.qualifiedConnectionId = connectionId;
        this.inviteeRewarded = true;
        this.inviterRewarded = inviterRewarded;
    }
}
