package io.github.gagann06.internshiptracker.company;

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

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import io.github.gagann06.internshiptracker.auth.CurrentUser;

import java.net.URI;




@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping()
    public ResponseEntity<CompanyResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyRequest request) {
        Company company = companyService.create(CurrentUser.id(jwt), request.name(), request.industry());
        return ResponseEntity.created(URI.create("/api/companies/" + company.getId())).body(CompanyResponse.from(company));
    }

    @GetMapping()
    public List<CompanyResponse> listCompanies(@AuthenticationPrincipal Jwt jwt) {
        List<Company> companies = companyService.listAll(CurrentUser.id(jwt));
        return companies.stream().map(company -> CompanyResponse.from(company)).toList();
    }

    @GetMapping("/{id}")
    public CompanyResponse getCompany(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Company company = companyService.getCompany(CurrentUser.id(jwt), id);

        return CompanyResponse.from(company);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        companyService.delete(CurrentUser.id(jwt), id);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public CompanyResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyRequest request, @PathVariable Long id) {
        Company company = companyService.update(CurrentUser.id(jwt), id, request.name(), request.industry());
        return CompanyResponse.from(company);    
    }

    
    
}
