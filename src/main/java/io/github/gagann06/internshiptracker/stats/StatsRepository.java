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

    @Query(value = """
            SELECT from_status AS "fromStatus",
                to_status as "toStatus",
                ROUND(AVG(EXTRACT(EPOCH FROM (changed_at - previously_changed_at)) / 86400), 1) AS "averageDays",
                COUNT(*) AS "transitions"
            FROM (
                SELECT sc.from_status, sc.to_status, sc.changed_at,
                LAG(sc.changed_at) OVER (PARTITION BY sc.application_id ORDER BY sc.changed_at, sc.id) AS
                previously_changed_at
                FROM status_changes sc
                JOIN applications a ON a.id = sc.application_id
                WHERE a.user_id = :ownerId
            ) AS timed
            WHERE previously_changed_at IS NOT NULL
            GROUP BY from_status, to_status
            ORDER BY from_status, to_status
            """, nativeQuery = true)
    List<TransitionTime> averageTimeBetweenStatuses(@Param("ownerId") Long ownerId);
}
