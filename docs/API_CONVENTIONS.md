# Implemented HTTP API conventions

Updated: 2026-09-14. Scope: Gateway and the eight implemented domain APIs.
Audit still has a closed business surface; Discovery/Config use their native
infrastructure protocols. This document does not claim completion of Phase 7.

## Request and error contract

The browser calls `/api/v1` through Gateway. Gateway and each resource service
validate their own session and operation authority; live organisation membership,
ownership, consent and grant decisions remain service-owned. See
[permissions](BACKEND_PERMISSIONS.md) and [sessions](SESSION_SECURITY.md).

`X-Request-ID` is an opaque correlation value, never an access credential or an
idempotency key. Accepted values contain 8–128 ASCII letters, digits, dots,
underscores or hyphens; invalid/missing values are replaced with UUIDs. Do not
send names, record contents, passwords or tokens in this header. Servlet filters
make it available during dispatch and clear the MDC afterward. Gateway propagates
the value explicitly; its reactive error logger does not rely on thread-local MDC.

HTTP failures use `application/problem+json`, `Cache-Control: no-store`, a matching
request ID and `type`, `title`, `status`, `detail`, `instance` fields. An instance
is a path reference, never the query string. Paths can contain opaque resource
identifiers; they should not contain personal data. Domain validation can add safe
field messages. Never serialize exception messages, stack traces, rejected values,
raw headers/cookies or request bodies in errors.

| Failure | Status |
| --- | --- |
| Missing/invalid HTTP parameter, malformed JSON or input method validation | 400 |
| Invalid, expired or revoked session | 401 |
| Forbidden operation or CSRF failure | 403 |
| Missing/concealed resource | 404 |
| Wrong HTTP method (with server-generated Allow header) | 405 |
| Unsupported response format | 406 |
| Domain version/state/idempotency conflict | 409 |
| Framework upload-size rejection | 413 |
| Unsupported request content type | 415 |
| Domain semantic validation, such as incomplete finalisation | 422 |
| Unexpected failure | 500 |
| Required authority unavailable; Gateway discovery/connection failure | 503 |
| Gateway transport timeout | 504 |

The narrow `HttpProtocolProblemDetailsHandler` handles framework errors before
the domain catch-all. It does not grant access, alter domain state or replace
domain exception handling. Protocol errors use `urn:sahha:problem:http-<status>`;
domain-specific problem types remain intact. Clients should use HTTP status and
safe domain fields, not depend on exception text. Gateway's handler processes
edge-generated failures only; normal downstream domain responses pass through.
Committed responses cannot be rewritten. Lower-level container rejection before
application dispatch remains a deployment/security acceptance concern.

## OpenAPI

Each implemented domain exposes local `/v3/api-docs` and Swagger UI for developer
inspection, not as a public Gateway route. External documents omit internal
authority endpoints and add the correlation header and metadata-only Problem
Detail schema. Existing success/domain response definitions are retained.

Cookie, CSRF and applicable file credentials are conjunctive: one security object
requires all of them. An array of separate OpenAPI security objects would mean
alternatives and misrepresent backend enforcement. The customizer reads method
and class declarations explicitly before building that contract. Anonymous Auth
operations do not acquire a cookie requirement from documentation customization.
Documentation never becomes an authorisation source.

All eight APIs have an `<SERVICE>_OPENAPI_ENABLED` switch, including Notification.
Disable public documentation in deployment settings. Audit query documentation
belongs with its future authorised query API, not with its closed foundation.

## Logging foundation and limits

Gateway/eight APIs import `api-policy.properties` as well as their health policy.
The policy disables detailed default errors, request/codec body logging, SQL
text/bind/extract logging and raw SQL error detail; servlet log levels include
the request ID. Unknown-path logging is disabled because it can contain submitted
data. Unexpected domain failures and new Gateway failures log bounded metadata
and exception class only. Do not enable DEBUG/TRACE request/SQL logging with
patient information; these defaults are not a universal log-scrubbing mechanism.

Structured event logs, tracing, dashboards, consumer failure/dead-letter redaction,
retention and full sensitive-logging acceptance remain Phase 7 work. No metrics or
diagnostics endpoint was opened by this change. No new runtime dependency or
shared domain library was introduced.

## Verification

Focused checks use synthetic controller requests, generated OpenAPI contracts,
owned isolated test databases and a real random-port Gateway with no discovered
upstream. No running full stack, Kafka or object store is needed for these checks.
Tool tests enforce policy import/defaults and consistency between service-owned
implementations. All 177 new API tests pass. The full 2026-09-14 Maven verification
passed all 14 reactor projects and packaged all twelve applications: 888 tests
passed, one existing opt-in storage test skipped, zero failures/errors. Sixteen
tooling checks also pass. The isolated non-persistent Redis helper was stopped
afterward. Exact evidence and remaining pre-cloud gates are recorded in the
[living plan](INTERNSHIP_PLAN.md); full live clinical/event acceptance is not
inferred from these protocol and regression checks.

Framework references: [Spring MVC error handling](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
and [Spring Boot logging](https://docs.spring.io/spring-boot/reference/features/logging.html).
Configuration names were also checked against installed Spring Boot 4.1.0 metadata.
