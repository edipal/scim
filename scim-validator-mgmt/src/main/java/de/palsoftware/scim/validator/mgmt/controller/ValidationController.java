package de.palsoftware.scim.validator.mgmt.controller;

import de.palsoftware.scim.validator.mgmt.dto.ValidationRunForm;
import de.palsoftware.scim.validator.mgmt.dto.ValidationRunView;
import de.palsoftware.scim.validator.mgmt.security.AuthenticatedUser;
import de.palsoftware.scim.validator.mgmt.service.MgmtUserService;
import de.palsoftware.scim.validator.mgmt.service.ValidationRunService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Controller
public class ValidationController {

    private static final String ATTR_CURRENT_USER = "currentUser";
    private static final String ATTR_CURRENT_USER_ROLE = "currentUserRole";
    private static final String ROLE_ADMIN = "Admin";
    private static final String ROLE_USER = "User";

    private final ValidationRunService validationRunService;
    private final MgmtUserService mgmtUserService;

    public ValidationController(ValidationRunService validationRunService,
            MgmtUserService mgmtUserService) {
        this.validationRunService = validationRunService;
        this.mgmtUserService = mgmtUserService;
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        if (!model.containsAttribute("runForm")) {
            model.addAttribute("runForm", new ValidationRunForm("", "", ""));
        }
        model.addAttribute("runs", validationRunService.listRuns(actorEmail(authentication), isAdmin(authentication)));
        model.addAttribute(ATTR_CURRENT_USER, resolveDisplayName(authentication));
        model.addAttribute(ATTR_CURRENT_USER_ROLE, currentUserRole(authentication));
        return "index";
    }

    @PostMapping("/runs")
    public String execute(@Valid @ModelAttribute("runForm") ValidationRunForm runForm,
            BindingResult bindingResult,
            Model model,
            Authentication authentication) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("runs",
                validationRunService.listRuns(actorEmail(authentication), isAdmin(authentication)));
            model.addAttribute(ATTR_CURRENT_USER, resolveDisplayName(authentication));
            model.addAttribute(ATTR_CURRENT_USER_ROLE, currentUserRole(authentication));
            return "index";
        }

        ValidationRunView run = ValidationRunView.from(validationRunService.executeRun(
                runForm,
            actorEmail(authentication)));
        return "redirect:/runs/" + run.id();
    }

    @GetMapping("/runs/{runId}")
    public String detail(@PathVariable UUID runId, Model model, Authentication authentication) {
        try {
            model.addAttribute("run",
                    validationRunService.getRun(runId, actorEmail(authentication), isAdmin(authentication)));
            model.addAttribute("tests",
                    validationRunService.getTestResults(runId, actorEmail(authentication), isAdmin(authentication)));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                model.addAttribute("runNotFound", true);
            } else {
                throw e;
            }
        }
        model.addAttribute(ATTR_CURRENT_USER, resolveDisplayName(authentication));
        model.addAttribute(ATTR_CURRENT_USER_ROLE, currentUserRole(authentication));
        return "run-detail";
    }

    @PostMapping("/runs/{runId}/delete")
    public String deleteRun(@PathVariable UUID runId, Authentication authentication) {
        validationRunService.deleteRun(runId, actorEmail(authentication), isAdmin(authentication));
        return "redirect:/";
    }

    private String actorEmail(Authentication authentication) {
        return AuthenticatedUser.email(authentication);
    }

    private boolean isAdmin(Authentication authentication) {
        return AuthenticatedUser.isAdmin(authentication);
    }

    private String currentUserRole(Authentication authentication) {
        return isAdmin(authentication) ? ROLE_ADMIN : ROLE_USER;
    }

    private String resolveDisplayName(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String fallback = AuthenticatedUser.displayName(authentication);
        return mgmtUserService.resolveDisplayName(AuthenticatedUser.email(authentication), fallback);
    }
}
