package com.capsule.content.adapter;

import com.capsule.content.api.CharacterQuery;
import com.capsule.content.api.ContentTypes.CharacterInfo;
import com.capsule.content.repository.CharacterRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class CharacterQueryAdapter implements CharacterQuery {

    private final CharacterRepository characters;
    private final ContentMapper mapper;

    CharacterQueryAdapter(CharacterRepository characters, ContentMapper mapper) {
        this.characters = characters;
        this.mapper = mapper;
    }

    @Override
    public Optional<CharacterInfo> find(long characterId) {
        return characters.findById(characterId).map(mapper::character);
    }
}
