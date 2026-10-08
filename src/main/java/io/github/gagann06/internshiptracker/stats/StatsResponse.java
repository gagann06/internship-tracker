package io.github.gagann06.internshiptracker.stats;

import java.util.List;

public record StatsResponse(List<FunnelStep> funnel, List<TransitionTime> timeBetweenStatuses, List<CompanyCount> applicationsPerCompany) {}
