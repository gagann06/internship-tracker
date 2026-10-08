package io.github.gagann06.internshiptracker.application;

import io.github.gagann06.internshiptracker.auth.CurrentUser;

import jakarta.validation.Valid;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;




@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ApplicationRequest request) {
        Application application = applicationService.create(CurrentUser.id(jwt), request);
        return ResponseEntity.created(URI.create("/api/applications/" + application.getId())).body(ApplicationResponse.from(application));
    }

    @GetMapping
    public List<ApplicationResponse> listApplications(@AuthenticationPrincipal Jwt jwt) {
        List<Application> applications = applicationService.listAll(CurrentUser.id(jwt));
        return applications.stream().map(application -> ApplicationResponse.from(application)).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse getApplication(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Application application = applicationService.getApplication(CurrentUser.id(jwt), id);

        return ApplicationResponse.from(application);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        applicationService.delete(CurrentUser.id(jwt), id);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ApplicationRequest request) {
        Application application = applicationService.update(CurrentUser.id(jwt), id, request);
        return ApplicationResponse.from(application);
    }

    @PostMapping("/{id}/status")
    public ApplicationResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        Application application = applicationService.changeStatus(CurrentUser.id(jwt), id, request.status(), request.note());
        return ApplicationResponse.from(application);
    }

    @GetMapping("/{id}/status-changes")
    public List<StatusChangeResponse> getHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        List<StatusChange> history = applicationService.getHistory(CurrentUser.id(jwt), id);
        return history.stream().map(s -> StatusChangeResponse.from(s)).toList();
    }
}
