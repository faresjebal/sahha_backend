package com.sahha.clinical.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/** Documentation only; no authority, role mapping or business policy is derived here. */
@Component
public class ApiContractCustomizer implements OpenApiCustomizer, org.springdoc.core.customizers.OperationCustomizer {
    private static final Set<String> CONJUNCTIVE_SCHEMES =
            Set.of("cookieAuth", "csrfHeader", "uploadTicket", "downloadGrant");

    @Override
    public Operation customize(Operation operation, org.springframework.web.method.HandlerMethod handler) {
        // Read both annotation levels explicitly: generated inheritance may otherwise
        // retain only the class cookie declaration and lose method CSRF/file headers.
        var declarations = new ArrayList<io.swagger.v3.oas.annotations.security.SecurityRequirement>();
        declarations.addAll(org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedRepeatableAnnotations(handler.getBeanType(),
                        io.swagger.v3.oas.annotations.security.SecurityRequirement.class));
        declarations.addAll(org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedRepeatableAnnotations(handler.getMethod(),
                        io.swagger.v3.oas.annotations.security.SecurityRequirement.class));
        if (!declarations.isEmpty()) {
            var security = new ArrayList<SecurityRequirement>();
            if (operation.getSecurity() != null) security.addAll(operation.getSecurity());
            declarations.stream().filter(d -> CONJUNCTIVE_SCHEMES.contains(d.name())).forEach(d ->
                    security.add(new SecurityRequirement().addList(d.name(), List.of(d.scopes()))));
            operation.setSecurity(security);
        }
        return operation;
    }

    @Override
    public void customise(OpenAPI api) {
        if (api.getComponents() == null) api.setComponents(new Components());
        api.getComponents().addSchemas("SahhaProblemDetail", new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema().minimum(java.math.BigDecimal.valueOf(400))
                        .maximum(java.math.BigDecimal.valueOf(599)))
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("requestId", correlationSchema())
                .addProperty("errors", new MapSchema().additionalProperties(new StringSchema()))
                .required(List.of("type", "title", "status", "detail", "instance", "requestId")));
        if (api.getPaths() == null) return;
        // Internal authority endpoints have no browser/Gateway route and no public contract.
        api.getPaths().keySet().removeIf(path -> path.startsWith("/api/v1/internal/"));
        api.getPaths().forEach((path, item) -> {
            if (path.startsWith("/api/v1/")) item.readOperations().forEach(this::operation);
        });
    }

    private void operation(Operation operation) {
        if (operation.getParameters() == null) operation.setParameters(new ArrayList<>());
        if (operation.getParameters().stream().noneMatch(p ->
                "header".equals(p.getIn()) && "X-Request-ID".equalsIgnoreCase(p.getName()))) {
            operation.addParametersItem(new Parameter().in("header").name("X-Request-ID")
                    .required(false).schema(correlationSchema())
                    .description("Opaque correlation only; never send personal data or secrets. Invalid values are replaced."));
        }
        List<SecurityRequirement> security = operation.getSecurity();
        if (security != null && !security.isEmpty() && security.stream().allMatch(r ->
                !r.isEmpty() && CONJUNCTIVE_SCHEMES.containsAll(r.keySet()))) {
            // OpenAPI arrays mean OR. V1 requires the cookie AND CSRF/file credentials.
            SecurityRequirement required = new SecurityRequirement();
            security.forEach(required::putAll);
            operation.setSecurity(List.of(required));
        }
        if (operation.getResponses() == null) operation.setResponses(new ApiResponses());
        addProblem(operation, "400", "Invalid request");
        addProblem(operation, "500", "Unexpected request failure; use the request ID for support");
        if (security != null && security.stream().anyMatch(r -> r.containsKey("cookieAuth"))) {
            addProblem(operation, "401", "Invalid, expired or revoked session");
            addProblem(operation, "403", "Operation not authorised");
        }
        else if (security != null && security.stream().anyMatch(r -> r.containsKey("csrfHeader"))) {
            addProblem(operation, "403", "CSRF validation failed");
        }
        operation.getResponses().values().stream().filter(r -> r.get$ref() == null).forEach(r ->
                r.addHeaderObject("X-Request-ID", new Header().schema(correlationSchema())
                        .description("Request correlation identifier")));
    }

    private static void addProblem(Operation operation, String status, String description) {
        if (!operation.getResponses().containsKey(status)) {
            operation.getResponses().addApiResponse(status, new ApiResponse().description(description)
                    .content(new Content().addMediaType("application/problem+json",
                            new io.swagger.v3.oas.models.media.MediaType().schema(
                                    new Schema<>().$ref("#/components/schemas/SahhaProblemDetail")))));
        }
    }

    private static StringSchema correlationSchema() {
        StringSchema schema = new StringSchema();
        schema.setMinLength(8);
        schema.setMaxLength(128);
        schema.setPattern("^[A-Za-z0-9._-]{8,128}$");
        return schema;
    }
}
