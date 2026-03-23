package de.palsoftware.scim.server.api.controller;

import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.common.model.ScimUserEmail;
import de.palsoftware.scim.server.common.model.Workspace;
import de.palsoftware.scim.server.common.repository.ScimUserRepository;
import de.palsoftware.scim.server.common.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(properties = "ACTUATOR_API_KEY=test-key")
class ScimUserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScimUserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    void testListUsers_doesNotThrowLazyInitializationException() throws Exception {
        // Create workspace
        Workspace ws = new Workspace();
        ws.setName("test-ws-" + UUID.randomUUID());
        ws.setCreatedByUsername("test-user");
        ws = workspaceRepository.save(ws);

        // Create user with lazy collection
        ScimUser user = new ScimUser();
        user.setWorkspace(ws);
        user.setUserName("lazy.test");
        user.setExternalId("ext1");

        ScimUserEmail email = new ScimUserEmail();
        email.setValue("test@example.com");
        email.setType("work");
        user.getEmails().add(email);

        userRepository.saveAndFlush(user);

        // Perform GET request that triggers mapper outside transaction. 
        // addFilters=false bypasses Spring Security so we hit the controller directly.
        mockMvc.perform(get("/ws/" + ws.getId() + "/scim/v2/Users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Resources[0].emails[0].value").value("test@example.com"));
    }
}
