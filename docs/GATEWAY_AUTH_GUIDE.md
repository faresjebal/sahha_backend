# Gateway and Auth local guide

Last updated: 2026-07-30

## Purpose

The browser-facing Auth entry point is now API Gateway:

- Gateway: `http://localhost:8079`
- Auth Service: `http://localhost:8081`
- Eureka: `http://localhost:8761`

Gateway routes `/api/v1/auth/**` and `/.well-known/jwks.json` to the
`auth-service` instance registered in Eureka. Auth Swagger remains available
directly on port `8081` for isolated endpoint diagnostics; the frontend must
call Gateway instead.

## Start locally

PostgreSQL and Memurai must already be running. Start the applications in this
order using their IntelliJ run configurations, or use three terminals:

```powershell
.\mvnw.cmd -pl discovery-server spring-boot:run
```

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

```powershell
.\mvnw.cmd -pl api-gateway spring-boot:run
```

Check:

```powershell
Invoke-RestMethod http://localhost:8761/actuator/health
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8079/actuator/health
Invoke-RestMethod http://localhost:8079/.well-known/jwks.json
```

Each health response should be `UP`. The Gateway JWKS response must contain
public RSA material only; it must never contain a private `d` parameter.

## Browser authentication flow

1. The browser calls `GET http://localhost:8079/api/v1/auth/csrf` with
   credentials enabled.
2. Auth returns the readable `XSRF-TOKEN` cookie and the same value in the JSON
   `token` field.
3. Each state-changing request includes the cookie plus its value in
   `X-XSRF-TOKEN`.
4. Login writes scoped HttpOnly access, refresh, and device cookies. JavaScript
   must not read or copy these values.
5. Gateway verifies the access cookie on protected routes using Auth's public
   JWKS. Auth still validates its authoritative session and credential version.
6. On page reload, the frontend calls protected `GET /api/v1/auth/session`.
   A valid access cookie restores safe UI metadata without rotating any
   credential.
7. If the access cookie is expired or within its renewal window, refresh is
   coordinated with a browser-wide exclusive lock. A waiting tab rechecks
   `/session` after acquiring the lock and skips rotation when another tab
   already renewed the shared cookies.
8. Refresh remains public at the Gateway edge because an expired access cookie
   must not prevent refresh-token rotation. Auth still verifies the refresh
   cookie and CSRF pair.
9. Logout, logout-all, session revocation, password change, and platform
   account status changes require a valid access cookie and CSRF pair.

Frontend requests must use `credentials: "include"` with Fetch, or
`withCredentials: true` with Axios.

## Public and protected routes

The current public edge routes are:

- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/registrations`
- `POST /api/v1/auth/email-verifications/resend`
- `POST /api/v1/auth/email-verifications/confirm`
- `POST /api/v1/auth/password-resets/request`
- `POST /api/v1/auth/password-resets/confirm`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /.well-known/jwks.json`
- `GET /actuator/health`

Other `/api/v1/**` requests require a valid access cookie. Unknown non-API
routes are denied. Auth remains responsible for CSRF and its session-backed
checks; every future domain service must also validate the token and enforce
resource-level authorisation.

## Gateway configuration

The important environment overrides are:

| Variable | Local default |
| --- | --- |
| `AUTH_ACCESS_COOKIE_NAME` | `SAHHA_ACCESS_TOKEN` |
| `AUTH_JWK_SET_URI` | `http://localhost:8081/.well-known/jwks.json` |
| `AUTH_JWT_ISSUER` | `http://localhost:8081` |
| `AUTH_JWT_AUDIENCE` | `sahha-api` |
| `FRONTEND_ALLOWED_ORIGINS` | `http://localhost:5173` |
| `GATEWAY_CONNECT_TIMEOUT` | `PT5S` |
| `GATEWAY_READ_TIMEOUT` | `PT30S` |

The Gateway and Auth issuer/audience values must match. Production must use
HTTPS and Auth's persistent RSA key pair.

## What Gateway enforces

Gateway:

- accepts browser authentication only from the configured access cookie;
- validates RS256, signature, expiry, issuer, audience, token type, UUID
  subject, UUID session ID, credential version, and safe global-role values;
- strips client-supplied `Authorization`, forwarding, and Sahha identity
  context headers before routing;
- replaces any client-supplied `X-Sahha-Client-IP` with the connection's edge
  address so Auth can apply trustworthy IP-aware throttling;
- creates or preserves one canonical `X-Request-ID`;
- returns safe Problem Details for edge `401` and `403` decisions;
- applies credentialed CORS only to configured frontend origins;
- forwards `Set-Cookie` responses without storing cookies in a shared
  downstream client.

Gateway does not query Redis or PostgreSQL and does not decide whether a user
may read a particular organisation, patient, consultation, or file.

## Automated verification

```powershell
.\mvnw.cmd -pl api-gateway test
.\mvnw.cmd test
```

Verified on 2026-07-30:

- Gateway: 16 tests, 0 failures, 0 errors, 0 skipped.
- Auth: 112 tests, 0 failures, 0 errors, 0 skipped.
- Complete reactor: 138 tests, 0 failures, 0 errors, 0 skipped.
- Native Memurai: `PONG`; zero `sahha:auth:session:v1:*` keys remained.
- Live Eureka/Auth/Gateway smoke: routing, JWKS, cookie isolation, CSRF,
  login reachability, protected-route rejection, and request-ID uniqueness
  passed.

## Current limitation and next task

Auth's account, session, credential, platform-account, throttling, and
security-event workflows are complete. Active-organisation selection and
domain-service token validation are intentionally not implemented yet because
Organisation Service must authoritatively validate memberships.

The next task is to model Organisation Service organisations, departments,
invitations, memberships, organisation roles, and the active-organisation
selection contract.
