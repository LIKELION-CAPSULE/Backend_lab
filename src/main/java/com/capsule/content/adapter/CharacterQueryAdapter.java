package com.capsule.content.adapter;

import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.repository.CharacterRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CharacterQueryAdapter implements CharacterQuery {

    private final CharacterRepository characters;
    private final ContentMapper mapper;

    @Override
    public Optional<CharacterInfo> find(long characterId) {
        return characters.findById(characterId).map(mapper::character);
    }

    @Override
    public List<CharacterInfo> active() {
        return characters.findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(mapper::character)
                .toList();
    }
}
