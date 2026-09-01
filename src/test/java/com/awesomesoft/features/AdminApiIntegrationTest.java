package com.awesomesoft.features;

import org.springframework.mock.web.MockHttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "features.admin.initial-username=admin",
        "features.admin.initial-password=admin-test-password"
})
@AutoConfigureMockMvc
class AdminApiIntegrationTest {

    private static final String APPS = "/features-api/v1/admin/applications";

    @Autowired
    private MockMvc mockMvc;

    private String slug;

    @BeforeEach
    void createApplication() throws Exception {
        slug = "app-" + Integer.toHexString((int) (System.nanoTime() & 0xffffff));
        asAdmin(post(APPS), "{\"slug\":\"" + slug + "\",\"name\":\"Test app\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void realLoginCreatesSessionUsableForAdminCalls() throws Exception {
        MvcResult login = mockMvc.perform(post("/features-api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin-test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        // MockMvc does not emit the container-level session cookie; the HTTP session itself is
        // where Spring Security stored the context, so carry that between requests.
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/features-api/v1/admin/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));

        mockMvc.perform(get(APPS).session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/features-api/v1/admin/auth/logout").session(session))
                .andExpect(status().isNoContent());
    }

    @Test
    void badCredentialsAndAnonymousAreRejected() throws Exception {
        mockMvc.perform(post("/features-api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get(APPS)).andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCanReadButNotMutate() throws Exception {
        mockMvc.perform(get(APPS + "/" + slug + "/flags").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk());

        mockMvc.perform(post(APPS + "/" + slug + "/flags")
                        .with(user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(flagBody("v.flag", "false")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/features-api/v1/admin/users").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateSlugAndDuplicateFlagKeyConflict() throws Exception {
        asAdmin(post(APPS), "{\"slug\":\"" + slug + "\",\"name\":\"Dup\"}")
                .andExpect(status().isConflict());

        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("dup.flag", "false"))
                .andExpect(status().isCreated());
        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("dup.flag", "false"))
                .andExpect(status().isConflict());
    }

    @Test
    void valueTypeIsValidatedOnCreateUpdateAndOverride() throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"),
                "{\"flagKey\":\"typed.flag\",\"name\":\"T\",\"valueType\":\"BOOLEAN\","
                        + "\"defaultValue\":\"not-a-boolean\",\"flagKind\":\"OPS\"}")
                .andExpect(status().isBadRequest());

        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("typed.flag", "true"))
                .andExpect(status().isCreated());

        asAdmin(patch(APPS + "/" + slug + "/flags/typed.flag"), "{\"defaultValue\":123}")
                .andExpect(status().isBadRequest());

        asAdmin(put(APPS + "/" + slug + "/flags/typed.flag/overrides/" + UUID.randomUUID()),
                "{\"value\":\"nope\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void mutationsBumpConfigVersionAndAppendAuditLog() throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("audited.flag", "false"))
                .andExpect(status().isCreated());
        asAdmin(patch(APPS + "/" + slug + "/flags/audited.flag"), "{\"defaultValue\":true}")
                .andExpect(status().isOk());
        UUID workgroup = UUID.randomUUID();
        asAdmin(put(APPS + "/" + slug + "/flags/audited.flag/overrides/" + workgroup),
                "{\"value\":false,\"note\":\"Noratel\"}")
                .andExpect(status().isOk());

        mockMvc.perform(get(APPS).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$[?(@.slug=='" + slug + "')].configVersion").value(3));

        mockMvc.perform(get(APPS + "/" + slug + "/audit-log").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].operation").value("OVERRIDE_SET"))
                .andExpect(jsonPath("$.content[0].workgroupId").value(workgroup.toString()))
                .andExpect(jsonPath("$.content[0].actorUsername").value("admin"))
                .andExpect(jsonPath("$.content[1].operation").value("FLAG_UPDATED"))
                .andExpect(jsonPath("$.content[1].oldValue").value(false))
                .andExpect(jsonPath("$.content[1].newValue").value(true))
                .andExpect(jsonPath("$.content[2].operation").value("FLAG_CREATED"));
    }

    @Test
    void deleteRequiresArchivalFirst() throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("doomed.flag", "false"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(APPS + "/" + slug + "/flags/doomed.flag").with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict());

        asAdmin(patch(APPS + "/" + slug + "/flags/doomed.flag"), "{\"archived\":true}")
                .andExpect(status().isOk());
        mockMvc.perform(delete(APPS + "/" + slug + "/flags/doomed.flag").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(APPS + "/" + slug + "/flags/doomed.flag").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void overrideLifecycleAndWorkgroupView() throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("wg.flag", "false"))
                .andExpect(status().isCreated());
        UUID workgroup = UUID.randomUUID();
        asAdmin(put(APPS + "/" + slug + "/flags/wg.flag/overrides/" + workgroup), "{\"value\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(true));

        mockMvc.perform(get(APPS + "/" + slug + "/overrides").param("workgroupId", workgroup.toString())
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flagKey").value("wg.flag"))
                .andExpect(jsonPath("$[0].value").value(true));

        mockMvc.perform(delete(APPS + "/" + slug + "/flags/wg.flag/overrides/" + workgroup)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(APPS + "/" + slug + "/flags/wg.flag/overrides/" + workgroup)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void staleReportListsExpiredFlags() throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"),
                "{\"flagKey\":\"stale.flag\",\"name\":\"S\",\"valueType\":\"BOOLEAN\",\"defaultValue\":false,"
                        + "\"flagKind\":\"PERMISSION\",\"expiresAt\":\"" + LocalDate.now().minusDays(1) + "\"}")
                .andExpect(status().isCreated());
        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("fresh.flag", "false"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(APPS + "/" + slug + "/flags/stale").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flagKey").value("stale.flag"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void expiryCanBeSetAndCleared() throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("expiring.flag", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());

        LocalDate expiry = LocalDate.now().plusDays(30);
        asAdmin(patch(APPS + "/" + slug + "/flags/expiring.flag"), "{\"expiresAt\":\"" + expiry + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(expiry.toString()));

        // null means "leave unchanged", so the date survives an unrelated update
        asAdmin(patch(APPS + "/" + slug + "/flags/expiring.flag"), "{\"defaultValue\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").value(expiry.toString()));

        // clearExpiresAt makes the flag never expire again
        asAdmin(patch(APPS + "/" + slug + "/flags/expiring.flag"), "{\"clearExpiresAt\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
    }

    @Test
    void flagKeyFormatIsValidated() throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"), flagBody("Bad_Key!", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.flagKey").exists());
    }

    @Test
    void adminUserLifecycle() throws Exception {
        MvcResult created = asAdmin(post("/features-api/v1/admin/users"),
                "{\"username\":\"viewer-" + slug + "\",\"password\":\"secret-password\",\"role\":\"VIEWER\"}")
                .andExpect(status().isCreated())
                .andReturn();
        String id = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        asAdmin(patch("/features-api/v1/admin/users/" + id), "{\"enabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    private org.springframework.test.web.servlet.ResultActions asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String body)
            throws Exception {
        return mockMvc.perform(builder
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String flagBody(String key, String defaultValue) {
        return "{\"flagKey\":\"" + key + "\",\"name\":\"Test flag\",\"valueType\":\"BOOLEAN\","
                + "\"defaultValue\":" + defaultValue + ",\"flagKind\":\"RELEASE\"}";
    }
}
