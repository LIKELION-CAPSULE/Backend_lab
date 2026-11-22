package com.capsule.credit.dto;

import com.capsule.credit.api.CreditTypes.Credits;
import java.time.Instant;

/** api.md 33: Credits + todayUsed · invitedFriends */
public record CreditSummaryResponse(
        int todayRemaining,
        int attendanceRemaining,
        Instant attendanceExpiresAt,
        int inviteBalance,
        boolean low,
        boolean exhausted,
        int todayUsed,
        int invitedFriends) {

    public static CreditSummaryResponse of(Credits c, int todayUsed, int invitedFriends) {
        return new CreditSummaryResponse(
                c.todayRemaining(),
                c.attendanceRemaining(),
                c.attendanceExpiresAt(),
                c.inviteBalance(),
                c.low(),
                c.exhausted(),
                todayUsed,
                invitedFriends);
    }
}
