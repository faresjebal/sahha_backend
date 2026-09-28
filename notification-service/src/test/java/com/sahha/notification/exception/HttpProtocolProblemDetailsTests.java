package com.sahha.notification.exception;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.sahha.notification.config.RequestIdFilter;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class HttpProtocolProblemDetailsTests {
    private static final String REQUEST_ID = "synthetic-request-123";
    private static final String PRIVATE = "synthetic-secret-must-not-echo";
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ProtocolFixture())
                .setControllerAdvice(new HttpProtocolProblemDetailsHandler(), new NotificationProblemDetailsHandler())
                .addFilters(new RequestIdFilter()).build();
    }

    static Stream<Arguments> protocolCases() {
        return Stream.of(
                Arguments.of(get("/protocol/required"), 400),
                Arguments.of(get("/protocol/required").param("count", PRIVATE), 400),
                Arguments.of(get("/protocol/id/" + PRIVATE), 400),
                Arguments.of(get("/protocol/header"), 400),
                Arguments.of(get("/protocol/cookie"), 400),
                Arguments.of(get("/protocol/validated").param("count", "-9"), 400),
                Arguments.of(post("/protocol/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + PRIVATE), 400),
                Arguments.of(post("/protocol/body").contentType(MediaType.TEXT_PLAIN).content(PRIVATE), 415),
                Arguments.of(post("/protocol/required"), 405),
                Arguments.of(get("/protocol/json").accept(MediaType.TEXT_PLAIN), 406),
                Arguments.of(get("/protocol/too-large"), 413),
                Arguments.of(get("/protocol/absent"), 404));
    }

    @ParameterizedTest
    @MethodSource("protocolCases")
    void protocolErrorsKeepTheirStatusAndSafeProblemContract(
            MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        mvc.perform(request.header("X-Request-ID", REQUEST_ID).queryParam("private", PRIVATE))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Request-ID", REQUEST_ID))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.type").value("urn:sahha:problem:http-" + expectedStatus))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.detail", not(containsString(PRIVATE))))
                .andExpect(jsonPath("$.instance", not(containsString("?"))))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void methodNotAllowedRetainsServerAllowHeader() throws Exception {
        mvc.perform(post("/protocol/required")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void beanValidationStillUsesTheDomainFieldErrorHandler() throws Exception {
        mvc.perform(post("/protocol/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}").header("X-Request-ID", REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(content().string(not(containsString("HttpProtocolProblemDetailsHandler"))));
    }

    @Test
    void unexpectedFailuresDoNotEchoExceptionMessage(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        mvc.perform(get("/protocol/failure").header("X-Request-ID", REQUEST_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:sahha:problem:internal-error"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(content().string(not(containsString(PRIVATE))));
        assertFalse(output.getAll().contains(PRIVATE));
        assertTrue(output.getAll().contains("requestId=" + REQUEST_ID));
    }

    @Test
    void requestCorrelationIsPresentDuringDispatchAndClearedAfterward() throws Exception {
        MDC.remove("requestId");
        mvc.perform(get("/protocol/correlation").header("X-Request-ID", REQUEST_ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(REQUEST_ID));
        assertNull(MDC.get("requestId"));
        mvc.perform(get("/protocol/failure").header("X-Request-ID", REQUEST_ID));
        assertNull(MDC.get("requestId"));
    }

    @Test
    void invalidCorrelationHeaderIsReplacedAndMatchesProblem() throws Exception {
        var response = mvc.perform(get("/protocol/required").header("X-Request-ID", "unsafe<id>"))
                .andExpect(status().isBadRequest()).andReturn().getResponse();
        String generated = response.getHeader("X-Request-ID");
        assertNotNull(generated);
        assertDoesNotThrow(() -> UUID.fromString(generated));
        assertTrue(response.getContentAsString().contains(generated));
        assertFalse(response.getContentAsString().contains("unsafe<id>"));
    }

    @RestController
    static class ProtocolFixture {
        @GetMapping("/protocol/required")
        Map<String, Integer> required(@RequestParam int count) { return Map.of("count", count); }
        @GetMapping("/protocol/id/{id}")
        Map<String, UUID> identifier(@PathVariable UUID id) { return Map.of("id", id); }
        @GetMapping("/protocol/header")
        String header(@RequestHeader("X-Fixture-Required") String value) { return value; }
        @GetMapping("/protocol/cookie")
        String cookie(@CookieValue("FIXTURE_REQUIRED") String value) { return value; }
        @GetMapping("/protocol/validated")
        int validated(@RequestParam @Min(1) int count) { return count; }
        @PostMapping(value = "/protocol/body", consumes = MediaType.APPLICATION_JSON_VALUE)
        Body body(@Valid @RequestBody Body body) { return body; }
        @GetMapping(value = "/protocol/json", produces = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> json() { return Map.of("result", "synthetic"); }
        @GetMapping("/protocol/too-large")
        void tooLarge() { throw new MaxUploadSizeExceededException(12); }
        @GetMapping("/protocol/failure")
        void failure() { throw new UnsupportedOperationException(PRIVATE); }
        @GetMapping("/protocol/correlation")
        Map<String, String> correlation(HttpServletRequest request) {
            assertEquals(request.getAttribute("sahha.requestId"), MDC.get("requestId"));
            return Map.of("requestId", MDC.get("requestId"));
        }
    }
    record Body(@NotBlank String name) {}
}
