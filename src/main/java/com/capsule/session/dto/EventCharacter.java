package com.capsule.session.dto;

import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.relation.api.RelationTypes.AffinityChange;

/** 상태 변화 전송(18) 응답의 캐릭터 한 명분: 서버가 고른 모션·표정과 이 이벤트로 바뀐 호감도 */
public record EventCharacter(long characterId, MotionRef motion, ExpressionRef expression, AffinityChange affinity) {}
