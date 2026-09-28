# Authoritative session validation

Last updated: 2026-09-12. Implementation verification is recorded in the living plan.

Gateway and each implemented resource service first validate the RS256 signature,
issuer, audience, time window and their own access-claim contract. They then ask
Auth to validate that exact access cookie at
`GET /api/v1/internal/auth/session-check`. The internal path is not routed by
Gateway. It has no subject/session query parameters and returns no account,
organisation, patient or token data.

Auth's decoder reads its own PostgreSQL session/account and active platform-role
assignments for every decision. It checks session status, idle/absolute expiry,
current credential version, active verified account, and the stored organisation
context/roles. Redis remains a technical session projection; a stale positive
entry cannot authorize a request. Resource services never query Auth's database.
Their existing live organisation membership and resource checks remain required.

The shared `session-security` Maven library contains the technical client,
decoder/transport decorators and stateless claim-to-authority conversion, not
shared permission policy, entities or domain persistence. Permission names and
role bundles remain service-owned; see [the catalogue](BACKEND_PERMISSIONS.md). It
is not another deployable service. Build a service together with reactor
dependencies, for example `mvn -pl clinical-service -am test` (with the native test
prerequisites), or use the root native build/test commands.

## Configuration and failure behavior

- `AUTH_SESSION_CHECK_URI` defaults to
  `http://localhost:8081/api/v1/internal/auth/session-check` in all eight clients.
  Set it to Auth's trusted private address when deploying or changing ports. It
  is an internal service URI, **not** the browser Gateway URL. Do not route it
  through Gateway (that would create a validation loop).
- Only the access cookie is forwarded. No refresh cookie, device cookie, user
  parameters, browser headers or patient content is copied. Redirects are refused.
- Every check uses a new random challenge; Auth echoes it only after successful
  authentication. Responses must be HTTP 204 with exactly that challenge. Both
  sides use `Cache-Control: no-store`; there is no positive decision cache.
- Connection/request timeouts are three seconds. Missing/malformed/replayed
  responses, revocation, timeouts and authority outages deny access. These use
  the existing safe HTTP **401** contract, including on an authority outage;
  availability is deliberately traded for confidentiality. The frontend may
  attempt refresh/re-authentication. Transport/token details are not returned.
- Gateway uses asynchronous HTTP rather than blocking its reactive event loop.
  Servlet services use bounded synchronous calls. Auth must be sized for the
  extra checks; performance/load testing and private TLS/network deployment
  hardening remain later delivery gates. Health probes do not contact Auth.

## Already-open WebSockets

Communication and Notification revalidate the original connection JWT before
each incoming frame and each outgoing broker send. A failed check closes the
connection with policy-violation status before delivering the protected frame.
This also enforces token expiry and organisation-context changes. Existing
handshake membership checks, STOMP CSRF and destination permissions are retained.

An idle socket can remain physically open until its next frame; this is not a
promise of instantaneous idle-socket disconnection. A request/frame already
authorized before revocation commits may finish. Existing file authorization,
ticket expiration and private storage boundaries still apply. This work does not grant
clinical access, complete referral sharing, or replace resource-level audits.
