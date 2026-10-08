package io.github.gagann06.internshiptracker.stats;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

import io.github.gagann06.internshiptracker.application.Application;

public interface StatsRepository extends Repository<Application, Long> {
    // no save/delete/findAll, makes sense for read only
    @Query("""
            select new io.github.gagann06.internshiptracker.stats.CompanyCount(c.name, count(a))
            from Application a join a.company c
            where a.ownerId = :ownerId
            group by c.id, c.name
            order by count(a) desc, c.name
            """)
    List<CompanyCount> countApplicationsPerCompany(@Param("ownerId") Long ownerId);

    @Query("""
            select distinct new io.github.gagann06.internshiptracker.stats.StatusReached(sc.application.id, sc.toStatus)
            from StatusChange sc
            where sc.application.ownerId = :ownerId
            """)
    List<StatusReached> findStatusesReached(@Param("ownerId") Long ownerId);
}
