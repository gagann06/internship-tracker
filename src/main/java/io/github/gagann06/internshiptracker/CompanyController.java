package io.github.gagann06.internshiptracker;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;




@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping()
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyRequest request ) {
        Company company = companyService.create(request.name(), request.industry());
        return ResponseEntity.created(URI.create("/api/companies/" + company.getId())).body(CompanyResponse.from(company));
    }

    @GetMapping()
    public List<CompanyResponse> list() {
        List<Company> companies = companyService.listAll();
        return companies.stream().map(company -> CompanyResponse.from(company)).toList();
    }
    
}
