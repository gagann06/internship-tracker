package io.github.gagann06.internshiptracker.stats;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gagann06.internshiptracker.auth.CurrentUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController 
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public StatsResponse stats(@AuthenticationPrincipal Jwt jwt) {
        return statsService.statsFor(CurrentUser.id(jwt));
    }
    
}
