package de.palsoftware.scim.validator.mgmt.dto;

import de.palsoftware.scim.validator.mgmt.model.ValidationRun;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ValidationRunView(
        UUID id,
        String name,
        String targetUrl,
        OffsetDateTime executedAt,
        String status,
        String createdByUsername,
        int totalTests,
        int passedTests,
        int failedTests) {
    public static ValidationRunView from(ValidationRun run) {
        String ownerDisplayName = run.getCreatedByUser() != null
                && run.getCreatedByUser().getEmail() != null
                && !run.getCreatedByUser().getEmail().isBlank()
                        ? run.getCreatedByUser().getEmail()
                        : run.getCreatedByUsername();
        return new ValidationRunView(
                run.getId(),
                run.getName(),
                run.getTargetUrl(),
                run.getExecutedAt(),
                run.getStatus(),
                ownerDisplayName,
                run.getTotalTests(),
                run.getPassedTests(),
                run.getFailedTests());
    }
}
