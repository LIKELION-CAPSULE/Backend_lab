package com.capsule.credit.api;

import com.capsule.credit.api.CreditTypes.SpendResult;
import java.util.UUID;

/** 크레딧 쓰기 (api.md 33 '크레딧 규칙'). 사용자마다 한 줄로 처리한다 */
public interface CreditCommands {

    /**
     * 차감 (보이스 20 · 고급 30 · 둘 다 50, 금액은 부르는 쪽이 정함). 출석분부터 쓴다. 같은 requestId는 한 번만.
     * 모자라면 차감하지 않고 spent=false (그 기능만 멈춤). 답장이 성공한 뒤에 부른다
     *
     * @param title 내역 문구 (예: "보이스 대화 · 채린")
     */
    SpendResult spend(long userId, int amount, String title, UUID requestId);

    /**
     * 초대분 지급 (사라지지 않음, 8단계 친구 초대 보상). 같은 초대 · 같은 쪽은 한 번만.
     * 33 invitedFriends는 초대한 사람 몫(inviter=true)만 센다
     *
     * @param referralId 초대 id (referrals.id)
     * @param inviter 초대한 사람 몫이면 true, 코드를 입력한(받은) 사람 몫이면 false
     * @return 이번에 지급했으면 true, 이미 받았으면 false
     */
    boolean grantInvite(long userId, int amount, long referralId, boolean inviter, String title);
}
