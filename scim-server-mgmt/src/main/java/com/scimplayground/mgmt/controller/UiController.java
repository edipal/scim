package com.scimplayground.mgmt.controller;

import com.scimplayground.mgmt.model.MgmtUser;
import com.scimplayground.mgmt.repository.MgmtUserRepository;
import com.scimplayground.mgmt.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Server-side rendered management UI using Thymeleaf.
 */
@Controller
public class UiController {

    private final MgmtUserRepository mgmtUserRepository;

    public UiController(MgmtUserRepository mgmtUserRepository) {
        this.mgmtUserRepository = mgmtUserRepository;
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        model.addAttribute("currentUser", resolveDisplayName(authentication));
        return "index";
    }

    @GetMapping("/ui/workspaces/{workspaceId}")
    public String workspaceDetail(@PathVariable String workspaceId, Model model, Authentication authentication) {
        model.addAttribute("currentUser", resolveDisplayName(authentication));
        return "workspace";
    }

    private String resolveDisplayName(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser oidcUser) {
            String sub = oidcUser.getSubject();
            if (sub != null) {
                return mgmtUserRepository.findById(sub)
                        .map(MgmtUser::getEmail)
                        .filter(e -> e != null && !e.isBlank())
                        .orElseGet(() -> AuthenticatedUser.displayName(authentication));
            }
        }
        return AuthenticatedUser.displayName(authentication);
    }
}
