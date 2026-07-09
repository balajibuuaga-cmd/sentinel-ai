package com.sentinelai.model.intelligence;

import java.util.List;

public record EngineeringDna(
        int overall,
        String summary,
        List<DnaScore> scores
) {
}
