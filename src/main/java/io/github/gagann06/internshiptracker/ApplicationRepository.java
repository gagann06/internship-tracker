package io.github.gagann06.internshiptracker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    @Query("select a from Application a join fetch a.company")
    List<Application> findAllWithCompany();
}
