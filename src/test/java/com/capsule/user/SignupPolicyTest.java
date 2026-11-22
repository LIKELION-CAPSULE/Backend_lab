package com.capsule.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.capsule.common.error.ApiException;
import com.capsule.user.domain.UserErrorCode;
import com.capsule.user.repository.UserRepository;
import com.capsule.user.service.SignupPolicy;
import org.junit.jupiter.api.Test;

/** 베타 정원 · 초대 코드 (api.md 4 · 6). 정원은 통합 테스트에서 낮추면 컨텍스트가 하나 더 생겨서 여기서 본다 */
class SignupPolicyTest {

    private final UserRepository users = mock(UserRepository.class);

    @Test
    void betaFullWhenActiveUsersReachMax() {
        SignupPolicy policy = new SignupPolicy(users, 100);
        given(users.countByWithdrawnAtIsNull()).willReturn(99L);
        policy.requireSeat();

        given(users.countByWithdrawnAtIsNull()).willReturn(100L);
        assertThatThrownBy(policy::requireSeat)
                .isInstanceOfSatisfying(
                        ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(UserErrorCode.BETA_FULL));
    }

    @Test
    void referralCodeRetriesWhenTaken() {
        SignupPolicy policy = new SignupPolicy(users, 100);
        given(users.existsByReferralCode(anyString())).willReturn(true, true, false);
        assertThat(policy.newReferralCode()).matches("CAP-[2-9A-HJ-NP-Z]{4}");
    }
}
