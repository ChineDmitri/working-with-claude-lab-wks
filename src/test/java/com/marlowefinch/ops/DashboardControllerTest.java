package com.marlowefinch.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The JSON API through MockMvc, plus one real HTTP call to observe the error path. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRestTemplate http;

    @Test
    void healthReportsUpAndTheFixedToday() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.today").value("2026-09-21"));
    }

    @Test
    void kpisDefaultToTheLast30DaysEndingToday() throws Exception {
        mvc.perform(get("/api/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-21"))
                .andExpect(jsonPath("$.onTimeRate").value(0.937))
                .andExpect(jsonPath("$.openTickets").value(114))
                .andExpect(jsonPath("$.revenue").value(360095.5))
                .andExpect(jsonPath("$.orders").value(624));
    }

    @Test
    void kpisAcceptAnExplicitRange() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-07-01"))
                .andExpect(jsonPath("$.to").value("2026-07-31"))
                .andExpect(jsonPath("$.orders").value(679))
                .andExpect(jsonPath("$.revenue").value(480209.5));
    }

    @Test
    void onTimeReturnsOneRowPerCarrier() throws Exception {
        mvc.perform(get("/api/deliveries/on-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[1].carrier").value("Kessler Logistics"))
                .andExpect(jsonPath("$[1].delivered").value(265))
                .andExpect(jsonPath("$[1].onTime").value(238))
                .andExpect(jsonPath("$[1].rate").value(0.8981));
    }

    @Test
    void lateReturnsOrderCarrierDatesAndDaysLateAndRespectsTheLimit() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-14").param("to", "2026-09-21").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].orderRef").isString())
                .andExpect(jsonPath("$[0].carrier").isString())
                .andExpect(jsonPath("$[0].promisedDate").isString())
                .andExpect(jsonPath("$[0].deliveredDate").isString())
                .andExpect(jsonPath("$[0].daysLate").isNumber());
    }

    @Test
    void lateWithFromAfterToIsRejected() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-21").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", contains("from must be on or before to")));
    }

    @Test
    void ticketsByCategoryReturnsOpenAndTotalPerCategory() throws Exception {
        mvc.perform(get("/api/tickets/by-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].category").value("Delivery delay"))
                .andExpect(jsonPath("$[0].open").value(41))
                .andExpect(jsonPath("$[0].total").value(90));
    }

    @Test
    void vendorsIncludeDaysUntilContractEndAndTheNoticeWindowFlag() throws Exception {
        mvc.perform(get("/api/vendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].name").value("Volta Parts GmbH"))
                .andExpect(jsonPath("$[0].contractEnd").value("2026-10-15"))
                .andExpect(jsonPath("$[0].noticeDays").value(30))
                .andExpect(jsonPath("$[0].daysUntilContractEnd").value(24))
                .andExpect(jsonPath("$[0].inNoticeWindow").value(true))
                .andExpect(jsonPath("$[7].inNoticeWindow").value(false));
    }

    /**
     * Used to document a 500 for a malformed date; since TODO-232 the client gets a 400 with an
     * errors list. A real HTTP call is used so the response is rendered exactly as a client sees it.
     */
    @Test
    void malformedFromProducesA400WithAnErrorsList() {
        ResponseEntity<String> response = http.getForEntity("/api/kpis?from=next-tuesday", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(response.getBody()).isEqualTo("{\"errors\":[\"from must be an ISO date (YYYY-MM-DD)\"]}");
    }

    // --- TODO-232: query parameter validation ---

    private static final String[] DATE_ENDPOINTS = {
            "/api/kpis", "/api/deliveries/on-time", "/api/deliveries/late", "/api/tickets/by-category"};

    @Test
    void malformedDatesAreRejectedOnEveryEndpoint() throws Exception {
        for (String endpoint : DATE_ENDPOINTS) {
            mvc.perform(get(endpoint).param("from", "next-tuesday"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", contains("from must be an ISO date (YYYY-MM-DD)")));
            mvc.perform(get(endpoint).param("to", "21/09/2026"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", contains("to must be an ISO date (YYYY-MM-DD)")));
            mvc.perform(get(endpoint).param("from", "2026-02-30"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", contains("from must be an ISO date (YYYY-MM-DD)")));
        }
    }

    @Test
    void fromAfterToIsRejectedOnEveryEndpoint() throws Exception {
        for (String endpoint : DATE_ENDPOINTS) {
            mvc.perform(get(endpoint).param("from", "2026-09-21").param("to", "2026-09-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", contains("from must be on or before to")));
        }
    }

    @Test
    void fromAfterTheDefaultToIsRejected() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-09-22"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", contains("from must be on or before to")));
    }

    @Test
    void aRangeOf367DaysIsRejectedOnEveryEndpoint() throws Exception {
        for (String endpoint : DATE_ENDPOINTS) {
            mvc.perform(get(endpoint).param("from", "2025-01-01").param("to", "2026-01-03"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", contains("date range must span at most 366 days")));
        }
    }

    @Test
    void aRangeOf366DaysIsAcceptedOnEveryEndpoint() throws Exception {
        for (String endpoint : DATE_ENDPOINTS) {
            mvc.perform(get(endpoint).param("from", "2025-01-01").param("to", "2026-01-02"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aSingleDayRangeIsAccepted() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-09-21").param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-21"))
                .andExpect(jsonPath("$.to").value("2026-09-21"));
    }

    @Test
    void limitOutsideOneTo500OrNotAnIntegerIsRejected() throws Exception {
        for (String limit : new String[] {"0", "501", "-1", "99999999", "99999999999", "ten", "2.5"}) {
            mvc.perform(get("/api/deliveries/late").param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors", contains("limit must be an integer between 1 and 500")));
        }
    }

    @Test
    void limitBoundsOneAnd500AreAccepted() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/deliveries/late").param("limit", "500"))
                .andExpect(status().isOk());
    }

    @Test
    void lateDefaultsToALimitOf20() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-01-01").param("to", "2026-09-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)));
    }

    @Test
    void severalProblemsAreAllReported() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "next-tuesday").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors", containsInAnyOrder(
                        "from must be an ISO date (YYYY-MM-DD)",
                        "limit must be an integer between 1 and 500")));
        mvc.perform(get("/api/deliveries/late").param("from", "yesterday").param("to", "today").param("limit", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder(
                        "from must be an ISO date (YYYY-MM-DD)",
                        "to must be an ISO date (YYYY-MM-DD)",
                        "limit must be an integer between 1 and 500")));
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-21").param("to", "2026-09-01").param("limit", "501"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", containsInAnyOrder(
                        "from must be on or before to",
                        "limit must be an integer between 1 and 500")));
    }

    @Test
    void validRequestsStillSucceedOnEveryEndpoint() throws Exception {
        for (String endpoint : DATE_ENDPOINTS) {
            mvc.perform(get(endpoint).param("from", "2026-07-01").param("to", "2026-07-31"))
                    .andExpect(status().isOk());
        }
    }
}
