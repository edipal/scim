package com.scimplayground.validator.mgmt.controller;

import com.scimplayground.validator.mgmt.dto.ValidationRunForm;
import com.scimplayground.validator.mgmt.dto.ValidationRunView;
import com.scimplayground.validator.mgmt.model.MgmtUser;
import com.scimplayground.validator.mgmt.security.AuthenticatedUser;
import com.scimplayground.validator.mgmt.repo.MgmtUserRepository;
import com.scimplayground.validator.mgmt.service.ValidationRunService;
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
        model.addAttribute("currentUser", resolveDisplayName(authentication));
        return "index";
    }

    @PostMapping("/runs")
    public String execute(@Valid @ModelAttribute("runForm") ValidationRunForm runForm,
                          BindingResult bindingResult,
                          Model model,
                          Authentication authentication) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("runs", validationRunService.listRuns(actorUserId(authentication), actorUsername(authentication), isAdmin(authentication)));
            model.addAttribute("currentUser", resolveDisplayName(authentication));
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
        model.addAttribute("currentUser", resolveDisplayName(authentication));
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
