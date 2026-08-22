package com.dearjolly.server.global.seed;

import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StampSeedWriter {
    private final StampRepository stampRepository;

    @Transactional
    public int write(List<StampSeed> seeds) {
        int changed = 0;
        for (StampSeed seed : seeds) {
            if (write(seed)) {
                changed++;
            }
        }
        return changed;
    }

    private boolean write(StampSeed seed) {
        Optional<Stamps> found = stampRepository.findByName(seed.name());
        if (found.isEmpty()) {
            stampRepository.save(Stamps.create(seed.name(), seed.imageKey()));
            return true;
        }
        Stamps stamp = found.get();
        if (stamp.getImageKey().equals(seed.imageKey())) {
            return false;
        }
        stamp.updateImageKey(seed.imageKey());
        return true;
    }
}
