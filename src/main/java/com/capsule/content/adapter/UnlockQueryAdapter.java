package com.capsule.content.adapter;

import com.capsule.content.api.ContentTypes.UnlockInfo;
import com.capsule.content.api.UnlockQuery;
import com.capsule.content.repository.UnlockContentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UnlockQueryAdapter implements UnlockQuery {

    private final UnlockContentRepository contents;
    private final ContentMapper mapper;

    @Override
    public List<UnlockInfo> between(long characterId, int afterStage, int upToStage) {
        if (upToStage <= afterStage) return List.of();
        return contents.between(characterId, afterStage, upToStage).stream()
                .map(c -> new UnlockInfo(
                        c.getId(),
                        c.getType(),
                        c.getTitle(),
                        c.getUnlockStage(),
                        c.getExpressionName(),
                        mapper.asset(c.getAssetUrl())))
                .toList();
    }
}
