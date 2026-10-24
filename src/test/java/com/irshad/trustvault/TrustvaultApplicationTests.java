
package com.irshad.trustvault;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TrustvaultApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void anonymousUserCannotAccessDocumentList() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }
@Test
void healthEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk());
}
@Test
@org.springframework.security.test.context.support.WithMockUser(roles = "USER")
void regularUserCannotAccessAdminAuditLogs() throws Exception {
    mockMvc.perform(get("/api/admin/audit-logs"))
            .andExpect(status().isForbidden());
}
}
