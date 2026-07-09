package com.sentinelai.controller;

import com.sentinelai.model.team.TeamInviteRequest;
import com.sentinelai.model.team.TeamMemberView;
import com.sentinelai.model.team.TeamRoleUpdateRequest;
import com.sentinelai.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/team")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/members")
    public List<TeamMemberView> listMembers() {
        return teamService.listMembers();
    }

    @PostMapping("/invite")
    public ResponseEntity<TeamMemberView> invite(@Valid @RequestBody TeamInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.invite(request));
    }

    @PutMapping("/members/{id}/role")
    public TeamMemberView updateRole(@PathVariable Long id, @Valid @RequestBody TeamRoleUpdateRequest request) {
        return teamService.updateRole(id, request);
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id) {
        teamService.removeMember(id);
        return ResponseEntity.noContent().build();
    }
}
