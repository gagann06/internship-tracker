package io.github.gagann06.internshiptracker.stats;

import io.github.gagann06.internshiptracker.application.ApplicationStatus;

public enum FunnelStage {
    APPLIED, ONLINE_ASSESSMENT, HIREVUE, TELEPHONE_INTERVIEW, VIDEO_INTERVIEW, ASSESSMENT_CENTRE, OFFER;

    /** The stage a status belongs to, or null if it isn't a stage you can reach. */
    static FunnelStage of(ApplicationStatus status) {
        return switch (status) {
            case APPLIED -> APPLIED;
            case ONLINE_ASSESSMENT, ONLINE_ASSESSMENT_COMPLETED -> ONLINE_ASSESSMENT;
            case HIREVUE, HIREVUE_COMPLETED -> HIREVUE;
            case TELEPHONE_INTERVIEW, TELEPHONE_INTERVIEW_COMPLETED -> TELEPHONE_INTERVIEW;
            case VIDEO_INTERVIEW, VIDEO_INTERVIEW_COMPLETED -> VIDEO_INTERVIEW;
            case ASSESSMENT_CENTRE, ASSESSMENT_CENTRE_COMPLETED -> ASSESSMENT_CENTRE;
            case OFFER -> OFFER;
            case TO_APPLY, REJECTED, WITHDRAWN, EXPIRED -> null;
        };
    }
}
