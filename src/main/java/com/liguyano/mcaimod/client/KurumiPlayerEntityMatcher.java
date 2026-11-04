package com.liguyano.mcaimod.client;

import java.util.List;
import java.util.UUID;

/** Select an already-synced player entity, never infer identity from proximity. */
final class KurumiPlayerEntityMatcher {
    record Candidate(UUID uuid, String profileName, String displayedName) {}

    static Candidate select(String canonical, UUID targetUuid, List<Candidate> candidates) {
        if (targetUuid != null) {
            List<Candidate> matching = candidates.stream()
                    .filter(candidate -> targetUuid.equals(candidate.uuid())).toList();
            if (matching.size() == 1) return matching.get(0);
            if (matching.size() > 1) return null;
        }
        List<Candidate> byName = candidates.stream().filter(candidate ->
                canonical.equalsIgnoreCase(candidate.profileName())
                        || canonical.equalsIgnoreCase(candidate.displayedName())).toList();
        return byName.size() == 1 ? byName.get(0) : null;
    }

    private KurumiPlayerEntityMatcher() {}
}
