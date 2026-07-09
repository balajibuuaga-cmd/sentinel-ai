package com.sentinelai.model.playbook;

import java.util.List;

public record EngineeringPlaybook(
        String id,
        String title,
        String category,
        String summary,
        List<String> checks,
        List<String> sentinelActions
) {
}
