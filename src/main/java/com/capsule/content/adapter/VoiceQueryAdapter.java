package com.capsule.content.adapter;

import com.capsule.content.api.ContentTypes.VoiceInfo;
import com.capsule.content.api.VoiceQuery;
import com.capsule.content.repository.CharacterRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VoiceQueryAdapter implements VoiceQuery {

    private final CharacterRepository characters;

    @Override
    public Optional<VoiceInfo> voice(long mateId) {
        return characters
                .findById(mateId)
                .map(c -> new VoiceInfo(c.getTtsVoice(), c.getTtsModel(), c.getTtsInstructions()));
    }
}
