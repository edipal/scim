package de.palsoftware.scim.server.mgmt.controller;

import de.palsoftware.scim.server.mgmt.model.MgmtUser;
import de.palsoftware.scim.server.mgmt.repository.MgmtUserRepository;
import de.palsoftware.scim.server.mgmt.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
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
    private final String scimApiBaseUrl;

    public UiController(MgmtUserRepository mgmtUserRepository,
                        @Value("${app.scim-api.base.url:http://localhost:8080}") String scimApiBaseUrl) {
        this.mgmtUserRepository = mgmtUserRepository;
        this.scimApiBaseUrl = scimApiBaseUrl;
    }

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        model.addAttribute("currentUser", resolveDisplayName(authentication));
        return "index";
    }

    @GetMapping("/ui/workspaces/{workspaceId}")
    public String workspaceDetail(@PathVariable String workspaceId, Model model, Authentication authentication) {
        model.addAttribute("currentUser", resolveDisplayName(authentication));
        model.addAttribute("scimApiBaseUrl", scimApiBaseUrl);
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
