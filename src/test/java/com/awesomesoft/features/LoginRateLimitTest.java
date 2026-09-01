package com.awesomesoft.features;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "features.admin.initial-password=admin-test-password",
        "features.security.login-rate-limit.max-attempts=3",
        "features.security.login-rate-limit.window-seconds=60"
})
@AutoConfigureMockMvc
class LoginRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsThrottledPerIpAfterMaxAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/features-api/v1/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/features-api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin-test-password\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
