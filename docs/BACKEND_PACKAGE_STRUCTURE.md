# Backend package structure

Last updated: 2026-07-28

## Purpose

Sahha uses a pragmatic layered package structure inside each Spring Boot
microservice. The structure keeps HTTP, business rules, persistence, security,
and asynchronous integration separate without introducing abstractions before
the internship workflow needs them.

Empty packages contain `.gitkeep` placeholders until implementation begins.
Placeholders do not add runtime behaviour.

## Domain-service template

Auth, Organisation, Patient, Scheduling, Clinical, Communication,
Notification, File, and Audit services use this structure. A service may omit
a package only when its owned workflow genuinely does not need that
responsibility.

```text
src/main/java/com/sahha/<service>/
├── config/       Service-owned Spring configuration
├── controller/   HTTP endpoints and request orchestration
├── dto/
│   ├── request/  Validated inbound API contracts
│   └── response/ Role-safe outbound API contracts
├── entity/       Service-owned persistence entities
├── repository/   Repositories for this service's database only
├── service/      Use cases, transactions, and business rules
├── mapper/       Explicit entity/domain/DTO mapping
├── exception/    Service exceptions and Problem Details handling
├── security/     Authentication context and resource authorisation
├── client/       Typed synchronous clients for other services
├── event/        Minimal Kafka event contracts and handlers
└── outbox/       Transactional-outbox persistence and publication
```

Database migrations live in:

```text
src/main/resources/db/migration/
```

### Boundary rules

- Controllers contain no persistence or business rules.
- Request and response DTOs are separate; JPA entities are never returned by
  controllers.
- Receptionist and clinical projections use different response DTOs.
- Services own transaction boundaries and call only their own repositories.
- Repositories never query another microservice's database.
- Security checks happen before protected data is mapped to a response.
- Events contain only the minimum data required by their consumers.
- Database changes and published domain events use the transactional outbox.
- Synchronous clients are typed and must not become hidden shared-database
  access.
- Avoid generic `util`, `common`, or `helper` packages that conceal ownership.

### Service-package grouping

When a service accumulates several use-case classes, group them by the entity
or responsibility they coordinate using lowercase package names. Auth
currently uses:

```text
service/
├── useraccountservice/       Account registration, verification, recovery,
│                             authentication, and public workflow orchestration
├── verificationtokenservice/ Verification/reset-token issuance and consumption
├── usersessionservice/       Device-session lifecycle, Redis cache-aside projection,
│                             logout, and global revocation
├── refreshtokenservice/      Refresh issuance, rotation, replay, and family revocation
├── accesstokenservice/       Minimal signed RS256 access-token issuance
├── userplatformroleservice/  Active global platform-role projection
├── browsersessionservice/    Scoped browser cookies and CSRF rotation
└── emailservice/             Provider-independent authentication email delivery
```

Session behavior is grouped under `usersessionservice`, while refresh-token
behavior is grouped under `refreshtokenservice`. Exceptions, repositories,
HTTP DTOs, and configuration remain in their existing responsibility packages
rather than being copied into service subpackages. PostgreSQL remains the
session authority; the Redis classes in `usersessionservice` are only a
versioned, bounded acceleration and revocation layer.

## API Gateway template

The gateway is an edge service, not a domain service. It has no entities or
repositories.

```text
src/main/java/com/sahha/gateway/
├── config/       Gateway, CORS, headers, and discovery configuration
├── filter/       Request ID, logging, rate-limit, and session filters
├── routing/      Route definitions and route-specific policies
├── security/     Edge authentication and coarse route protection
└── exception/    Safe edge-error translation
```

Resource-level authorisation remains inside the target domain service.

## Discovery Server template

The discovery server is infrastructure-only:

```text
src/main/java/com/sahha/discovery/
└── config/       Eureka server and operational configuration
```

No Sahha business logic belongs in Discovery Server.

## Config Server template

The config server is infrastructure-only:

```text
src/main/java/com/sahha/config/
└── config/       Native/Git repository and operational configuration
```

Local externalised configuration belongs under
`src/main/resources/config-repository/`. No domain rules or secrets belong in
the source-controlled configuration repository.

## Test structure

Tests mirror the production responsibility they verify:

```text
src/test/java/com/sahha/<service>/
├── controller/
├── service/
├── repository/
├── security/
├── mapper/
├── integration/
└── fixture/
```

Edge and discovery services use only the relevant subset.

- Unit tests remain next to the responsibility they test.
- `integration` contains cross-layer tests using real infrastructure. Local
  native services are acceptable for the Windows workflow; CI should use
  isolated containers when persistence or messaging is introduced.
- `fixture` contains synthetic builders and test data only.
- Test fixtures must not contain real patient information.
