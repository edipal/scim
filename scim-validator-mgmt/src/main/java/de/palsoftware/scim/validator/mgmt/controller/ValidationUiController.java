package de.palsoftware.scim.validator.mgmt.controller;

import de.palsoftware.scim.validator.mgmt.dto.ValidationRunForm;
import de.palsoftware.scim.validator.mgmt.dto.ValidationRunView;
import de.palsoftware.scim.validator.mgmt.model.MgmtUser;
import de.palsoftware.scim.validator.mgmt.security.AuthenticatedUser;
import de.palsoftware.scim.validator.mgmt.repo.MgmtUserRepository;
import de.palsoftware.scim.validator.mgmt.service.ValidationRunService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@Controller
public class ValidationUiController {

    private static final String ATTR_CURRENT_USER = "currentUser";

    private final ValidationRunService validationRunService;
    private final MgmtUserRepository mgmtUserRepository;

    public ValidationUiController(ValidationRunService validationRunService,
                                  MgmtUserRepository mgmtUserRepository) {
        this.validationRunService = validationRunService;
        this.mgmtUserRepository = mgmtUserRepository;
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        if (!model.containsAttribute("runForm")) {
            model.addAttribute("runForm", new ValidationRunForm("", "", ""));
        }
        model.addAttribute("runs", validationRunService.listRuns(actorUserId(authentication), actorUsername(authentication), isAdmin(authentication)));
        model.addAttribute(ATTR_CURRENT_USER, resolveDisplayName(authentication));
        return "index";
    }

    @PostMapping("/runs")
    public String execute(@Valid @ModelAttribute("runForm") ValidationRunForm runForm,
                          BindingResult bindingResult,
                          Model model,
                          Authentication authentication) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("runs", validationRunService.listRuns(actorUserId(authentication), actorUsername(authentication), isAdmin(authentication)));
            model.addAttribute(ATTR_CURRENT_USER, resolveDisplayName(authentication));
            return "index";
        }

        ValidationRunView run = ValidationRunView.from(validationRunService.executeRun(
            runForm,
            actorUserId(authentication),
            resolveDisplayName(authentication)));
        return "redirect:/runs/" + run.id();
    }

    @GetMapping("/runs/{runId}")
    public String detail(@PathVariable UUID runId, Model model, Authentication authentication) {
        model.addAttribute("run", validationRunService.getRun(runId, actorUserId(authentication), actorUsername(authentication), isAdmin(authentication)));
        model.addAttribute("tests", validationRunService.getTestResults(runId, actorUserId(authentication), actorUsername(authentication), isAdmin(authentication)));
        model.addAttribute(ATTR_CURRENT_USER, resolveDisplayName(authentication));
        return "run-detail";
    }

    @PostMapping("/runs/{runId}/delete")
    public String deleteRun(@PathVariable UUID runId, Authentication authentication) {
        validationRunService.deleteRun(runId, actorUserId(authentication), actorUsername(authentication), isAdmin(authentication));
        return "redirect:/";
    }

    private String actorUserId(Authentication authentication) {
        return AuthenticatedUser.userId(authentication);
    }

    private String actorUsername(Authentication authentication) {
        return AuthenticatedUser.username(authentication);
    }

    private boolean isAdmin(Authentication authentication) {
        return AuthenticatedUser.isAdmin(authentication);
    }

    private String resolveDisplayName(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String sub = oidcUser.getSubject();
            if (sub != null) {
                return mgmtUserRepository.findById(sub)
                    .map(MgmtUser::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .orElseGet(() -> AuthenticatedUser.displayName(authentication));
            }
        }
        return AuthenticatedUser.displayName(authentication);
    }
}
