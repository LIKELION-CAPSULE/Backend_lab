package com.capsule.content.adapter;

import com.capsule.content.api.ContentTypes.ExpressionRef;
import com.capsule.content.api.ContentTypes.MotionRef;
import com.capsule.content.api.ContentTypes.Reaction;
import com.capsule.content.api.ReactionQuery;
import com.capsule.content.domain.ReactionMap;
import com.capsule.content.repository.CharacterRepository;
import com.capsule.content.repository.ReactionMapRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReactionQueryAdapter implements ReactionQuery {

    private final CharacterRepository characters;
    private final ReactionMapRepository reactions;

    @Override
    public Reaction pick(long characterId, String trigger, Integer intensity) {
        return characters
                .findById(characterId)
                .map(c -> pickOne(rows(c.getModel().getId(), trigger, intensity)))
                .orElse(Reaction.NONE);
    }

    /** 그 강도의 행이 없으면 강도 없는(NULL) 행으로 대체 */
    private List<ReactionMap> rows(long modelId, String trigger, Integer intensity) {
        if (intensity != null) {
            var exact = reactions.findByModelIdAndTriggerTypeAndIntensity(modelId, trigger, intensity.shortValue());
            if (!exact.isEmpty()) {
                return exact;
            }
        }
        return reactions.findByModelIdAndTriggerTypeAndIntensityIsNull(modelId, trigger);
    }

    private static Reaction pickOne(List<ReactionMap> rows) {
        if (rows.isEmpty()) {
            return Reaction.NONE;
        }
        ReactionMap r = rows.get(ThreadLocalRandom.current().nextInt(rows.size()));
        return new Reaction(
                r.getMotion() == null
                        ? null
                        : new MotionRef(
                                r.getMotion().getGroupName(), r.getMotion().getFilePath()),
                r.getExpression() == null
                        ? null
                        : new ExpressionRef(
                                r.getExpression().getName(), r.getExpression().getFilePath()));
    }
}
