package com.awesomesoft.features;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "features.admin.initial-username=admin",
        "features.admin.initial-password=admin-test-password",
        "features.evaluation.bootstrap-token=plans-test-token",
        "features.evaluation.bootstrap-application=plans-app"
})
@AutoConfigureMockMvc
class PlansIntegrationTest {

    private static final String APPS     = "/features-api/v1/admin/applications";
    private static final String WGROUPS  = "/features-api/v1/admin/workgroups";
    private static final String EVALUATE = "/features-api/v1/evaluate";

    @Autowired
    private MockMvc mockMvc;

    private String slug;
    private String suffix;

    @BeforeEach
    void setup() throws Exception {
        suffix = Integer.toHexString((int) (System.nanoTime() & 0xffffff));
        slug = "plans-app";
        // 201 on first run, 409 afterwards — both acceptable
        mockMvc.perform(post(APPS)
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"slug\":\"" + slug + "\",\"name\":\"Plans App\"}"));
    }

    // ── Workgroups ────────────────────────────────────────────────────────────

    @Test
    void workgroupLifecycle() throws Exception {
        UUID id = UUID.randomUUID();

        // create
        asAdmin(post(WGROUPS), "{\"id\":\"" + id + "\",\"name\":\"Acme Corp\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme Corp"));

        // get
        mockMvc.perform(get(WGROUPS + "/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));

        // rename
        asAdmin(patch(WGROUPS + "/" + id), "{\"name\":\"Acme Inc\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Inc"));

        // appears in list
        mockMvc.perform(get(WGROUPS).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].name").value("Acme Inc"));

        // delete
        mockMvc.perform(delete(WGROUPS + "/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(WGROUPS + "/" + id).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateWorkgroupIdIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + id + "\",\"name\":\"First\"}")
                .andExpect(status().isCreated());
        asAdmin(post(WGROUPS), "{\"id\":\"" + id + "\",\"name\":\"Second\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void workgroupSearchByName() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + id1 + "\",\"name\":\"Globex " + suffix + "\"}").andExpect(status().isCreated());
        asAdmin(post(WGROUPS), "{\"id\":\"" + id2 + "\",\"name\":\"Initech " + suffix + "\"}").andExpect(status().isCreated());

        mockMvc.perform(get(WGROUPS).param("name", "Globex " + suffix).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id1.toString()));
    }

    // ── Plans CRUD ────────────────────────────────────────────────────────────

    @Test
    void planLifecycle() throws Exception {
        String planId = createPlan("Starter-" + suffix, "Entry level");

        // get detail
        mockMvc.perform(get(APPS + "/" + slug + "/plans/" + planId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Starter-" + suffix))
                .andExpect(jsonPath("$.description").value("Entry level"))
                .andExpect(jsonPath("$.flags").isArray())
                .andExpect(jsonPath("$.flags.length()").value(0));

        // update
        asAdmin(patch(APPS + "/" + slug + "/plans/" + planId),
                "{\"name\":\"Starter Plus-" + suffix + "\",\"description\":\"Updated\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Starter Plus-" + suffix))
                .andExpect(jsonPath("$.description").value("Updated"));

        // appears in list
        mockMvc.perform(get(APPS + "/" + slug + "/plans").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + planId + "')].name").value("Starter Plus-" + suffix));

        // delete
        mockMvc.perform(delete(APPS + "/" + slug + "/plans/" + planId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(APPS + "/" + slug + "/plans/" + planId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicatePlanNameIsRejected() throws Exception {
        createPlan("Pro-" + suffix, null);
        asAdmin(post(APPS + "/" + slug + "/plans"),
                "{\"name\":\"Pro-" + suffix + "\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void planNotFoundInAnotherApplication() throws Exception {
        String otherSlug = "other-" + suffix;
        asAdmin(post(APPS), "{\"slug\":\"" + otherSlug + "\",\"name\":\"Other\"}")
                .andExpect(status().isCreated());

        String planId = createPlan("OtherPlan-" + suffix, null);

        // plan belongs to slug, not to otherSlug
        mockMvc.perform(get(APPS + "/" + otherSlug + "/plans/" + planId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ── Plan flags ────────────────────────────────────────────────────────────

    @Test
    void planFlagSetAndRemove() throws Exception {
        String flagKey = "plan.feat-" + suffix;
        createFlag(flagKey, "false");
        String planId = createPlan("Business-" + suffix, null);

        // set flag in plan
        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey), "{\"value\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value(flagKey))
                .andExpect(jsonPath("$.value").value(true));

        // appears in plan detail
        mockMvc.perform(get(APPS + "/" + slug + "/plans/" + planId).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.flags.length()").value(1))
                .andExpect(jsonPath("$.flags[0].flagKey").value(flagKey))
                .andExpect(jsonPath("$.flags[0].value").value(true));

        // update the value
        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey), "{\"value\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(false));

        // remove flag from plan
        mockMvc.perform(delete(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(APPS + "/" + slug + "/plans/" + planId).with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.flags.length()").value(0));
    }

    @Test
    void planFlagValueTypeIsValidated() throws Exception {
        String flagKey = "plan.typed-" + suffix;
        createFlag(flagKey, "false");
        String planId = createPlan("TypedPlan-" + suffix, null);

        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey), "{\"value\":\"not-a-bool\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void removingNonExistentPlanFlagReturns404() throws Exception {
        String flagKey = "plan.missing-" + suffix;
        createFlag(flagKey, "true");
        String planId = createPlan("EmptyPlan-" + suffix, null);

        mockMvc.perform(delete(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ── Workgroup plan assignment ──────────────────────────────────────────────

    @Test
    void workgroupPlanAssignmentLifecycle() throws Exception {
        UUID wgId = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgId + "\",\"name\":\"TechCorp " + suffix + "\"}")
                .andExpect(status().isCreated());

        String planId = createPlan("Enterprise-" + suffix, null);

        // assign
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + planId + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workgroupId").value(wgId.toString()))
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.planName").value("Enterprise-" + suffix))
                .andExpect(jsonPath("$.workgroupName").value("TechCorp " + suffix));

        // get assignment
        mockMvc.perform(get(APPS + "/" + slug + "/workgroups/" + wgId + "/plan")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId));

        // reassign to another plan
        String plan2Id = createPlan("Enterprise Plus-" + suffix, null);
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + plan2Id + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(plan2Id));

        // unassign
        mockMvc.perform(delete(APPS + "/" + slug + "/workgroups/" + wgId + "/plan")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(APPS + "/" + slug + "/workgroups/" + wgId + "/plan")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void assigningPlanForUnknownWorkgroupReturns404() throws Exception {
        String planId = createPlan("Ghost-" + suffix, null);
        UUID unknownWg = UUID.randomUUID();

        asAdmin(put(APPS + "/" + slug + "/workgroups/" + unknownWg + "/plan"),
                "{\"planId\":\"" + planId + "\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void unassigningWhenNoAssignmentReturns404() throws Exception {
        UUID wgId = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgId + "\",\"name\":\"NoAssign " + suffix + "\"}")
                .andExpect(status().isCreated());

        mockMvc.perform(delete(APPS + "/" + slug + "/workgroups/" + wgId + "/plan")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    // ── Evaluation with plans ─────────────────────────────────────────────────

    @Test
    void workgroupWithPlanGetsPlanValue() throws Exception {
        String flagKey = "eval.plan-" + suffix;
        createFlag(flagKey, "false");
        String planId = createPlan("EvalPlan-" + suffix, null);
        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey), "{\"value\":true}")
                .andExpect(status().isOk());

        UUID wgWithPlan = UUID.randomUUID();
        UUID wgWithoutPlan = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgWithPlan + "\",\"name\":\"WithPlan " + suffix + "\"}").andExpect(status().isCreated());
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgWithPlan + "/plan"),
                "{\"planId\":\"" + planId + "\"}")
                .andExpect(status().isOk());

        // workgroup with plan gets plan value
        evaluate(wgWithPlan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags['" + flagKey + "'].value").value(true))
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("PLAN"));

        // workgroup without plan gets default
        evaluate(wgWithoutPlan)
                .andExpect(jsonPath("$.flags['" + flagKey + "'].value").value(false))
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("DEFAULT"));
    }

    @Test
    void workgroupOverrideWinsOverPlan() throws Exception {
        String flagKey = "eval.override-" + suffix;
        createFlag(flagKey, "false");
        String planId = createPlan("OverridePlan-" + suffix, null);
        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey), "{\"value\":true}")
                .andExpect(status().isOk());

        UUID wgId = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgId + "\",\"name\":\"Override " + suffix + "\"}").andExpect(status().isCreated());
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + planId + "\"}")
                .andExpect(status().isOk());

        // individual override set to different value than plan
        asAdmin(put(APPS + "/" + slug + "/flags/" + flagKey + "/overrides/" + wgId), "{\"value\":false}")
                .andExpect(status().isOk());

        // override wins
        evaluate(wgId)
                .andExpect(jsonPath("$.flags['" + flagKey + "'].value").value(false))
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("WORKGROUP_OVERRIDE"));
    }

    @Test
    void lockedFlagIgnoresPlanAndOverride() throws Exception {
        String flagKey = "eval.locked-" + suffix;
        createFlag(flagKey, "false");
        String planId = createPlan("LockedPlan-" + suffix, null);
        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey), "{\"value\":true}")
                .andExpect(status().isOk());

        UUID wgId = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgId + "\",\"name\":\"Locked " + suffix + "\"}").andExpect(status().isCreated());
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + planId + "\"}")
                .andExpect(status().isOk());

        asAdmin(patch(APPS + "/" + slug + "/flags/" + flagKey), "{\"locked\":true}")
                .andExpect(status().isOk());

        evaluate(wgId)
                .andExpect(jsonPath("$.flags['" + flagKey + "'].value").value(false))
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("LOCKED"));
    }

    @Test
    void flagNotInPlanFallsBackToDefault() throws Exception {
        String inPlanKey  = "eval.inplan-"  + suffix;
        String notInPlan  = "eval.noplan-"  + suffix;
        createFlag(inPlanKey, "false");
        createFlag(notInPlan, "false");

        String planId = createPlan("PartialPlan-" + suffix, null);
        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + inPlanKey), "{\"value\":true}")
                .andExpect(status().isOk());

        UUID wgId = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgId + "\",\"name\":\"Partial " + suffix + "\"}").andExpect(status().isCreated());
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + planId + "\"}")
                .andExpect(status().isOk());

        evaluate(wgId)
                .andExpect(jsonPath("$.flags['" + inPlanKey + "'].reason").value("PLAN"))
                .andExpect(jsonPath("$.flags['" + notInPlan  + "'].reason").value("DEFAULT"));
    }

    @Test
    void reassigningPlanChangesEvaluationImmediately() throws Exception {
        String flagKey = "eval.switch-" + suffix;
        createFlag(flagKey, "false");

        String planA = createPlan("PlanA-" + suffix, null);
        String planB = createPlan("PlanB-" + suffix, null);
        asAdmin(put(APPS + "/" + slug + "/plans/" + planA + "/flags/" + flagKey), "{\"value\":true}")
                .andExpect(status().isOk());
        asAdmin(put(APPS + "/" + slug + "/plans/" + planB + "/flags/" + flagKey), "{\"value\":false}")
                .andExpect(status().isOk());

        UUID wgId = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgId + "\",\"name\":\"Switcher " + suffix + "\"}").andExpect(status().isCreated());
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + planA + "\"}")
                .andExpect(status().isOk());

        evaluate(wgId)
                .andExpect(jsonPath("$.flags['" + flagKey + "'].value").value(true))
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("PLAN"));

        // switch to plan B
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + planB + "\"}")
                .andExpect(status().isOk());

        evaluate(wgId)
                .andExpect(jsonPath("$.flags['" + flagKey + "'].value").value(false))
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("PLAN"));
    }

    @Test
    void unassigningPlanFallsBackToDefault() throws Exception {
        String flagKey = "eval.unassign-" + suffix;
        createFlag(flagKey, "false");
        String planId = createPlan("UnassignPlan-" + suffix, null);
        asAdmin(put(APPS + "/" + slug + "/plans/" + planId + "/flags/" + flagKey), "{\"value\":true}")
                .andExpect(status().isOk());

        UUID wgId = UUID.randomUUID();
        asAdmin(post(WGROUPS), "{\"id\":\"" + wgId + "\",\"name\":\"Unassign " + suffix + "\"}").andExpect(status().isCreated());
        asAdmin(put(APPS + "/" + slug + "/workgroups/" + wgId + "/plan"),
                "{\"planId\":\"" + planId + "\"}")
                .andExpect(status().isOk());

        evaluate(wgId)
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("PLAN"));

        mockMvc.perform(delete(APPS + "/" + slug + "/workgroups/" + wgId + "/plan")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());

        evaluate(wgId)
                .andExpect(jsonPath("$.flags['" + flagKey + "'].value").value(false))
                .andExpect(jsonPath("$.flags['" + flagKey + "'].reason").value("DEFAULT"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ResultActions evaluate(UUID workgroupId) throws Exception {
        return mockMvc.perform(post(EVALUATE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer plans-test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"context\":{\"workgroupId\":\"" + workgroupId + "\"}}"));
    }

    private String createPlan(String name, String description) throws Exception {
        String body = description != null
                ? "{\"name\":\"" + name + "\",\"description\":\"" + description + "\"}"
                : "{\"name\":\"" + name + "\"}";
        String response = asAdmin(post(APPS + "/" + slug + "/plans"), body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void createFlag(String key, String defaultValue) throws Exception {
        asAdmin(post(APPS + "/" + slug + "/flags"),
                "{\"flagKey\":\"" + key + "\",\"name\":\"Test\",\"valueType\":\"BOOLEAN\","
                        + "\"defaultValue\":" + defaultValue + ",\"flagKind\":\"PERMISSION\"}")
                .andExpect(status().isCreated());
    }

    private ResultActions asAdmin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder, String body)
            throws Exception {
        return mockMvc.perform(builder
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
