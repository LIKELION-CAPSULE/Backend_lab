package com.capsule.content.adapter;

import com.capsule.content.api.BackgroundQuery;
import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.repository.CharacterBackgroundRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BackgroundQueryAdapter implements BackgroundQuery {

    private final CharacterBackgroundRepository backgrounds;
    private final ContentMapper mapper;

    @Override
    public List<BackgroundInfo> backgrounds(long mateId) {
        return backgrounds.findByCharacterIdAndIsActiveTrueOrderBySortOrderAscIdAsc(mateId).stream()
                .map(b -> new BackgroundInfo(
                        b.getCode(), b.getName(), mapper.asset(b.getImageUrl()), b.getGuide(), b.isDefault()))
                .toList();
    }
}
