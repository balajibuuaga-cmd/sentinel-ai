package com.sentinelai.controller;

import com.sentinelai.model.PullRequestDecisionRequest;
import com.sentinelai.model.PullRequestReview;
import com.sentinelai.model.PullRequestReviewRequest;
import com.sentinelai.service.PullRequestReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pr-reviews")
public class PullRequestReviewController {

    private final PullRequestReviewService pullRequestReviewService;

    public PullRequestReviewController(PullRequestReviewService pullRequestReviewService) {
        this.pullRequestReviewService = pullRequestReviewService;
    }

    @GetMapping
    public List<PullRequestReview> reviews() {
        return pullRequestReviewService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PullRequestReview> review(@PathVariable long id) {
        return pullRequestReviewService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/simulate")
    public PullRequestReview simulate(@Valid @RequestBody PullRequestReviewRequest request) {
        return pullRequestReviewService.simulate(request);
    }

    @PostMapping("/{id}/decision")
    public PullRequestReview decide(
            @PathVariable long id,
            @Valid @RequestBody PullRequestDecisionRequest request
    ) {
        return pullRequestReviewService.decide(id, request);
    }
}
