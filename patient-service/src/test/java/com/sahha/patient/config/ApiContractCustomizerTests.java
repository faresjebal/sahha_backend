package com.sahha.patient.config;

import java.util.ArrayList;
import java.util.List;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiContractCustomizerTests {
    @Test
    void recoversMethodCredentialsWithoutLosingClassCookieDeclaration() throws Exception {
        var customizer = new ApiContractCustomizer();
        Operation operation = new Operation().addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
        customizer.customize(operation, new org.springframework.web.method.HandlerMethod(
                new CredentialFixture(), CredentialFixture.class.getDeclaredMethod("command")));
        customizer.customise(new OpenAPI().path("/api/v1/example", new PathItem().post(operation)));
        assertEquals(1, operation.getSecurity().size());
        assertEquals(java.util.Set.of("cookieAuth", "csrfHeader", "uploadTicket"),
                operation.getSecurity().getFirst().keySet());
    }

    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "cookieAuth")
    static class CredentialFixture {
        @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "csrfHeader")
        @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "uploadTicket")
        public void command() {}
    }

    @Test
    void documentsProblemCorrelationAndConjunctiveCookieCsrfFileCredentials() {
        Operation command = new Operation().security(new ArrayList<>(List.of(
                new SecurityRequirement().addList("cookieAuth"),
                new SecurityRequirement().addList("csrfHeader"),
                new SecurityRequirement().addList("uploadTicket"))));
        ApiResponse success = new ApiResponse().description("Domain success");
        command.responses(new ApiResponses().addApiResponse("201", success));
        OpenAPI api = new OpenAPI().path("/api/v1/example", new PathItem().post(command))
                .path("/api/v1/internal/example", new PathItem().get(new Operation()));
        new ApiContractCustomizer().customise(api);
        assertFalse(api.getPaths().containsKey("/api/v1/internal/example"));
        assertEquals(1, command.getSecurity().size());
        assertEquals(java.util.Set.of("cookieAuth", "csrfHeader", "uploadTicket"),
                command.getSecurity().getFirst().keySet());
        assertSame(success, command.getResponses().get("201"));
        assertTrue(success.getHeaders().containsKey("X-Request-ID"));
        assertEquals("#/components/schemas/SahhaProblemDetail",
                command.getResponses().get("400").getContent()
                        .get("application/problem+json").getSchema().get$ref());
        assertNotNull(api.getComponents().getSchemas().get("SahhaProblemDetail"));
        assertTrue(command.getResponses().keySet().containsAll(List.of("400", "401", "403", "500")));
        assertEquals(1, command.getParameters().size());
        assertEquals("X-Request-ID", command.getParameters().getFirst().getName());
        assertFalse(command.getParameters().getFirst().getRequired());
        new ApiContractCustomizer().customise(api);
        assertEquals(1, command.getParameters().size());
        assertEquals(1, command.getSecurity().size());
    }

    @Test
    void preservesAnonymousOperationsExistingDomainResponsesAndUnknownSecurityAlternatives() {
        Operation publicRead = new Operation();
        Operation publicCommand = new Operation().addSecurityItem(new SecurityRequirement().addList("csrfHeader"));
        Operation alternative = new Operation().security(new ArrayList<>(List.of(
                new SecurityRequirement().addList("futureAuth"), new SecurityRequirement().addList("cookieAuth"))));
        ApiResponse domain = new ApiResponse().description("Domain validation");
        publicRead.responses(new ApiResponses().addApiResponse("400", domain));
        publicRead.addParametersItem(new Parameter().in("header").name("x-request-id").schema(new StringSchema()));
        OpenAPI api = new OpenAPI().path("/api/v1/public", new PathItem().get(publicRead).post(publicCommand))
                .path("/api/v1/future", new PathItem().get(alternative));
        new ApiContractCustomizer().customise(api);
        assertNull(publicRead.getSecurity());
        assertFalse(publicRead.getResponses().containsKey("401"));
        assertSame(domain, publicRead.getResponses().get("400"));
        assertEquals(1, publicRead.getParameters().size());
        assertEquals(java.util.Set.of("csrfHeader"), publicCommand.getSecurity().getFirst().keySet());
        assertFalse(publicCommand.getResponses().containsKey("401"));
        assertTrue(publicCommand.getResponses().containsKey("403"));
        assertEquals(2, alternative.getSecurity().size());
    }
}
