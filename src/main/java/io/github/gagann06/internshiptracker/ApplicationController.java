package io.github.gagann06.internshiptracker;

import jakarta.validation.Valid;
import java.util.List;

import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody ApplicationRequest request ) {
        Application application = applicationService.create(request);
        return ResponseEntity.created(URI.create("/api/applications/" + application.getId())).body(ApplicationResponse.from(application));
    }

    @GetMapping
    public List<ApplicationResponse> listApplications() {
        List<Application> applications = applicationService.listAll();
        return applications.stream().map(application -> ApplicationResponse.from(application)).toList();
    }

    @GetMapping("/{id}")
    public ApplicationResponse getApplication(@PathVariable Long id) {
        Application application = applicationService.getApplication(id);

        return ApplicationResponse.from(application);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        applicationService.delete(id);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@Valid @RequestBody ApplicationRequest request, @PathVariable Long id) {
        Application application = applicationService.update(id, request);
        return ApplicationResponse.from(application);    
    }

    @PostMapping("/{id}/status") 
    public ApplicationResponse status(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        Application application = applicationService.changeStatus(id, request.status(), request.note());
        return ApplicationResponse.from(application);
    }
    
    @GetMapping("/{id}/status-changes")
    public List<StatusChangeResponse> getHistory(@PathVariable Long id) {
        List<StatusChange> history = applicationService.getHistory(id);
            return history.stream().map(s -> StatusChangeResponse.from(s)).toList();
    }
}
