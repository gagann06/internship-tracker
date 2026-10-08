package io.github.gagann06.internshiptracker.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    @Query("select a from Application a join fetch a.company where a.ownerId = :ownerId")
    List<Application> findAllWithCompany(@Param("ownerId") Long ownerId);

    @Query("select a from Application a join fetch a.company where a.id = :id and a.ownerId = :ownerId")
    Optional<Application> findByIdWithCompany(@Param("id") Long id, @Param("ownerId") Long ownerId);


    boolean existsByCompanyId(Long companyId);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    @Query("""
            select a from Application a join fetch a.company
            where a.deadline between :from and :to
            and a.status not in :excluded
            order by a.deadline
            """)
    List<Application> findDueBetween(@Param("from") LocalDate from,
                                    @Param("to") LocalDate to,
                                    @Param("excluded") Collection<ApplicationStatus> excluded);
}
