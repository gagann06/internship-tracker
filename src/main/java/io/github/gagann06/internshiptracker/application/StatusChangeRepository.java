package io.github.gagann06.internshiptracker.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {
    List<StatusChange> findByApplicationIdOrderByChangedAtAscIdAsc(Long applicationId);
}