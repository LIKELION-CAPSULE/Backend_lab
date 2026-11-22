package com.capsule.content.adapter;

import com.capsule.content.api.ContentTypes.MateInfo;
import com.capsule.content.api.MateQuery;
import com.capsule.content.repository.CharacterRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MateQueryAdapter implements MateQuery {

    private final CharacterRepository characters;
    private final ContentMapper mapper;

    @Override
    public Optional<MateInfo> find(long mateId) {
        return characters.findById(mateId).map(mapper::mate);
    }

    @Override
    public List<MateInfo> activeMates() {
        return characters.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(mapper::mate)
                .toList();
    }
}
