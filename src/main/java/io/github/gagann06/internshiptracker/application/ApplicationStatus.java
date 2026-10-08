package io.github.gagann06.internshiptracker.application;

import java.util.EnumSet;
import java.util.Set;


public enum ApplicationStatus {
    TO_APPLY,
    APPLIED,
    ONLINE_ASSESSMENT,
    ONLINE_ASSESSMENT_COMPLETED,
    HIREVUE,
    HIREVUE_COMPLETED,
    TELEPHONE_INTERVIEW,
    TELEPHONE_INTERVIEW_COMPLETED,
    VIDEO_INTERVIEW,
    VIDEO_INTERVIEW_COMPLETED,
    ASSESSMENT_CENTRE,
    ASSESSMENT_CENTRE_COMPLETED,
    OFFER,
    REJECTED,
    WITHDRAWN,
    EXPIRED;

    public static final Set<ApplicationStatus> FINISHED = EnumSet.of(REJECTED, WITHDRAWN, EXPIRED);

}