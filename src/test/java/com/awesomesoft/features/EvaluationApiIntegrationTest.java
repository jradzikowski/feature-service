package com.awesomesoft.features;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "features.evaluation.bootstrap-token=test-bootstrap-token",
        "features.evaluation.bootstrap-application=audit",
        "features.admin.initial-password=admin-test-password"
})
@AutoConfigureMockMvc
class EvaluationApiIntegrationTest {

    private static final String EVALUATE = "/features-api/v1/evaluate";
    private static final String BOOTSTRAP_AUTH = "Bearer test-bootstrap-token";

    @Autowired
    private MockMvc mockMvc;

    private final UUID workgroupA = UUID.randomUUID();
    private final UUID workgroupB = UUID.randomUUID();
    private String suffix;

    @BeforeEach
    void seedApplication() throws Exception {
        // Unique flag keys per test run; the 'audit' application is shared across tests in this class.
        suffix = Integer.toHexString((int) (System.nanoTime() & 0xffffff));
        mockMvc.perform(post("/features-api/v1/admin/applications")
                .with(user("seeder").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"audit\",\"name\":\"Audit\"}"));
        // 201 on first run, 409 afterwards — both fine.
    }

    @Test
    void rejectsMissingAndUnknownTokens() throws Exception {
        mockMvc.perform(post(EVALUATE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(workgroupA)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(EVALUATE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(workgroupA)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolvesDefaultOverrideAndLocked() throws Exception {
        String key = "test.flag-" + suffix;
        createFlag(key, "false");
        setOverride(key, workgroupA, "true");

        // Workgroup with an override gets it; another workgroup gets the default.
        evaluate(workgroupA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags['" + key + "'].value").value(true))
                .andExpect(jsonPath("$.flags['" + key + "'].reason").value("WORKGROUP_OVERRIDE"))
                .andExpect(jsonPath("$.flags['" + key + "'].type").value("BOOLEAN"));
        evaluate(workgroupB)
                .andExpect(jsonPath("$.flags['" + key + "'].value").value(false))
                .andExpect(jsonPath("$.flags['" + key + "'].reason").value("DEFAULT"));

        // Locked: the override is ignored, everyone gets the default.
        patchFlag(key, "{\"locked\":true}");
        evaluate(workgroupA)
                .andExpect(jsonPath("$.flags['" + key + "'].value").value(false))
                .andExpect(jsonPath("$.flags['" + key + "'].reason").value("LOCKED"));

        // Unlocked again: the override applies once more.
        patchFlag(key, "{\"locked\":false}");
        evaluate(workgroupA)
                .andExpect(jsonPath("$.flags['" + key + "'].reason").value("WORKGROUP_OVERRIDE"));
    }

    @Test
    void archivedFlagsDisappearFromEvaluation() throws Exception {
        String key = "test.archived-" + suffix;
        createFlag(key, "true");
        evaluate(workgroupA).andExpect(jsonPath("$.flags['" + key + "']").exists());

        patchFlag(key, "{\"archived\":true}");
        evaluate(workgroupA).andExpect(jsonPath("$.flags['" + key + "']").doesNotExist());
    }

    @Test
    void etagYields304UntilConfigurationChanges() throws Exception {
        String key = "test.etag-" + suffix;
        createFlag(key, "false");

        MvcResult first = evaluate(workgroupA).andExpect(status().isOk()).andReturn();
        String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

        mockMvc.perform(post(EVALUATE)
                        .header(HttpHeaders.AUTHORIZATION, BOOTSTRAP_AUTH)
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(workgroupA)))
                .andExpect(status().isNotModified());

        patchFlag(key, "{\"defaultValue\":true}");

        mockMvc.perform(post(EVALUATE)
                        .header(HttpHeaders.AUTHORIZATION, BOOTSTRAP_AUTH)
                        .header(HttpHeaders.IF_NONE_MATCH, etag)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(workgroupA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags['" + key + "'].value").value(true));
    }

    @Test
    void generatedTokenWorksAndIsScopedToItsApplication() throws Exception {
        String key = "test.token-" + suffix;
        createFlag(key, "true");

        // Second application with its own token must not see audit's flags.
        String otherSlug = "other-" + suffix;
        mockMvc.perform(post("/features-api/v1/admin/applications")
                        .with(user("seeder").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slug\":\"" + otherSlug + "\",\"name\":\"Other\"}"))
                .andExpect(status().isCreated());
        MvcResult tokenResult = mockMvc.perform(post("/features-api/v1/admin/applications/" + otherSlug + "/tokens")
                        .with(user("seeder").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"other-backend\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(tokenResult.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post(EVALUATE)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evaluateBody(workgroupA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationSlug").value(otherSlug))
                .andExpect(jsonPath("$.flags['" + key + "']").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions evaluate(UUID workgroupId) throws Exception {
        return mockMvc.perform(post(EVALUATE)
                .header(HttpHeaders.AUTHORIZATION, BOOTSTRAP_AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(evaluateBody(workgroupId)));
    }

    private String evaluateBody(UUID workgroupId) {
        return "{\"context\":{\"workgroupId\":\"" + workgroupId + "\"}}";
    }

    private void createFlag(String key, String defaultValue) throws Exception {
        mockMvc.perform(post("/features-api/v1/admin/applications/audit/flags")
                        .with(user("seeder").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flagKey\":\"" + key + "\",\"name\":\"Test\",\"valueType\":\"BOOLEAN\","
                                + "\"defaultValue\":" + defaultValue + ",\"flagKind\":\"RELEASE\"}"))
                .andExpect(status().isCreated());
    }

    private void patchFlag(String key, String body) throws Exception {
        mockMvc.perform(patch("/features-api/v1/admin/applications/audit/flags/" + key)
                        .with(user("seeder").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private void setOverride(String key, UUID workgroupId, String value) throws Exception {
        mockMvc.perform(put("/features-api/v1/admin/applications/audit/flags/" + key + "/overrides/" + workgroupId)
                        .with(user("seeder").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":" + value + "}"))
                .andExpect(status().isOk());
    }
}
