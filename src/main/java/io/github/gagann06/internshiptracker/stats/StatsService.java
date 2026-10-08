package io.github.gagann06.internshiptracker.stats;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;

@Service 
public class StatsService {
    private final StatsRepository statsRepository;

    public StatsService(StatsRepository statsRepository) {
        this.statsRepository = statsRepository;
    }

    @Transactional(readOnly = true)
    public List<CompanyCount> applicationsPerCompany(Long userId) {
        return statsRepository.countApplicationsPerCompany(userId);
    }

    
    @Transactional(readOnly = true)
    public List<FunnelStep> funnel(Long userId) {
        Map<FunnelStage, Set<Long>> reached = new EnumMap<>(FunnelStage.class);

        for (FunnelStage stage : FunnelStage.values()) {
            reached.put(stage, new HashSet<>());
        }

        for (StatusReached row: statsRepository.findStatusesReached(userId)) {
            FunnelStage stage = FunnelStage.of(row.status());
            if (stage != null) {
                reached.get(stage).add(row.applicationId());
            }
        }

        Set<Long> applied = new HashSet<>();
        for (Set<Long> ids : reached.values()) {
            applied.addAll(ids);
        }
        reached.put(FunnelStage.APPLIED, applied);

        List<FunnelStep> steps = new ArrayList<>();
        for (FunnelStage stage : FunnelStage.values()) {
            int count = reached.get(stage).size();
            int percent = applied.isEmpty() ? 0 : Math.round(100f * count / applied.size());
            steps.add(new FunnelStep(stage, count, percent));
        }

        return steps;
    }
}
