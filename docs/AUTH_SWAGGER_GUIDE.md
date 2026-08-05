# Auth Service Swagger test guide

Last updated: 2026-08-01

## What is implemented

Swagger documents sixteen Auth operations:

| Method | Path | Success |
| --- | --- | ---: |
| `GET` | `/api/v1/auth/csrf` | `200` |
| `POST` | `/api/v1/auth/registrations` | `202` |
| `POST` | `/api/v1/auth/email-verifications/resend` | `202` |
| `POST` | `/api/v1/auth/email-verifications/confirm` | `204` |
| `POST` | `/api/v1/auth/password-resets/request` | `202` |
| `POST` | `/api/v1/auth/password-resets/confirm` | `204` |
| `POST` | `/api/v1/auth/login` | `200` |
| `POST` | `/api/v1/auth/active-organisation` | `200` |
| `POST` | `/api/v1/auth/refresh` | `200` |
| `POST` | `/api/v1/auth/logout` | `204` |
| `POST` | `/api/v1/auth/logout-all` | `204` |
| `GET` | `/api/v1/auth/session` | `200` |
| `GET` | `/api/v1/auth/sessions` | `200` |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | `204` |
| `POST` | `/api/v1/auth/password-change` | `204` |
| `PUT` | `/api/v1/auth/platform/accounts/{userId}/status` | `204` |

The public key document at `/.well-known/jwks.json` is intentionally hidden
from Swagger. It publishes only the RSA public key that Gateway and later
resource services use to verify access tokens.

## 1. Start local dependencies

PostgreSQL and the Memurai Windows service must be running:

```powershell
Get-Service postgresql*
Get-Service Memurai
& "C:\Program Files\Memurai\memurai-cli.exe" PING
```

Memurai should be `Running` and return `PONG`.

Start Discovery and Auth from their shared IntelliJ run configurations, or use
two PowerShell terminals:

```powershell
.\mvnw.cmd -pl discovery-server spring-boot:run
```

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

Open:

- Health: `http://localhost:8081/actuator/health`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
- Public keys: `http://localhost:8081/.well-known/jwks.json`

Health should be `UP`. Swagger remains served directly by Auth as an isolated
service diagnostic. The React application and full browser workflow use
Gateway on `http://localhost:8079`; see `docs/GATEWAY_AUTH_GUIDE.md`.

## 2. Set the Swagger CSRF header

Every unsafe request (`POST`, `PUT`, or `DELETE`) requires CSRF protection,
including registration and login.

1. Execute `GET /api/v1/auth/csrf`.
2. Copy the `token` value from its JSON response.
3. Select **Authorize** at the top of Swagger.
4. Paste the value into `csrfHeader (apiKey)`.
5. Select **Authorize**, then close the dialog.

Swagger now sends `X-XSRF-TOKEN`, while the browser sends the matching
`XSRF-TOKEN` cookie automatically. Do not enter an access token into
`cookieAuth`; the login endpoint writes that HttpOnly cookie and the browser
manages it.

The JSON `token` and the `XSRF-TOKEN` cookie value are intentionally identical.
If they differ, Auth is running an older build and must be restarted before
testing.

Auth rotates the CSRF token after login, refresh, and logout. After each of
those calls, execute `GET /api/v1/auth/csrf` again and replace the
`csrfHeader` value in **Authorize**.

## 3. Register and verify a synthetic account

Execute `POST /api/v1/auth/registrations` with a unique Gmail plus alias:

```json
{
  "email": "your-address+sahha-swagger-01@gmail.com",
  "password": "Synthetic passphrase 2026!",
  "firstName": "Amal",
  "lastName": "Mansour",
  "phoneNumber": "+21620123456"
}
```

Use synthetic names and a password you do not use elsewhere. Expect:

```text
HTTP 202
{"status":"accepted"}
```

Brevo sends the verification message when local mail is enabled. The link
currently targets the future frontend route. Copy only the value after
`token=` and execute `POST /api/v1/auth/email-verifications/confirm`:

```json
{
  "token": "paste-the-token-from-the-newest-verification-link"
}
```

Expect `204`. A token is single-use. Resend returns the same generic `202` for
known and unknown accounts; after the two-minute cooldown, the newest message
replaces the older active verification token.

## 4. Test login and cookie issuance

Execute `POST /api/v1/auth/login`:

```json
{
  "email": "your-address+sahha-swagger-01@gmail.com",
  "password": "Synthetic passphrase 2026!",
  "deviceName": "Swagger on development laptop"
}
```

Expect `200` with safe session metadata. The response body does not contain an
access token or refresh token. In browser developer tools, under
**Application → Cookies → http://localhost:8081**, the browser should hold:

- `SAHHA_ACCESS_TOKEN`: HttpOnly, path `/`.
- `SAHHA_REFRESH_TOKEN`: HttpOnly, path `/api/v1/auth`.
- `SAHHA_DEVICE_ID`: HttpOnly device identifier.
- `XSRF-TOKEN`: readable CSRF token.

`Secure` is disabled only by the local HTTP profile. Production defaults to
secure cookies.

Call `GET /api/v1/auth/csrf` and update Swagger's `csrfHeader` before the next
POST.

## 5. Test active-organisation selection

This operation also requires Discovery and Organisation Service to be running,
and the signed-in account must already have an active organisation membership.
Use [the Organisation Swagger guide](./ORGANISATION_SWAGGER_GUIDE.md) to create
the organisation and assign its administrator first.

1. Start Organisation Service with the `local` profile.
2. Execute `GET /api/v1/organisations/memberships` in Organisation Swagger and
   copy one returned `organisationId`.
3. Refresh Auth's CSRF value and execute
   `POST /api/v1/auth/active-organisation`:

   ```json
   {
     "organisationId": "replace-with-an-active-membership-organisation-id"
   }
   ```

Expect `200` with the selected `activeOrganisationId` and only the active roles
for that membership. Auth replaces only the access cookie and CSRF token; it
does not create a session, rotate the refresh token, or merge roles from other
organisations. A missing, suspended, unrelated, or role-less membership is
rejected safely.

## 6. Test session management

Execute `GET /api/v1/auth/session` first. Expect `200` with the current user
ID, session ID, access-token expiry, and global platform roles. This endpoint
validates the existing access cookie but does not issue authentication
cookies, rotate the refresh token, update the session, or add refresh-token
history. The React application uses it for ordinary page-reload restoration.

Log in from a second browser or private browsing profile with a different
`deviceName` if you want two active sessions to inspect.

Execute `GET /api/v1/auth/sessions`. Expect `200` and only safe metadata such
as session ID, device name, creation/last-seen time, expiry, and whether it is
the current session. Raw tokens, token hashes, and IP addresses are never
returned.

To revoke one device:

1. Execute `GET /api/v1/auth/csrf` and update `csrfHeader`.
2. Execute `DELETE /api/v1/auth/sessions/{sessionId}` using a session ID owned
   by the current account.

Expect `204`. Revoking another account's session returns a generic `404`.
Revoking the current session also clears the browser authentication cookies.

## 7. Test authenticated password change

Execute `POST /api/v1/auth/password-change`:

```json
{
  "currentPassword": "Synthetic passphrase 2026!",
  "newPassword": "Changed synthetic passphrase 2026!"
}
```

Expect `204`. Auth verifies the current password, changes the password,
advances the credential version, revokes every active session, and clears the
current browser's authentication cookies. All earlier access and refresh
credentials must then be rejected.

## 8. Test refresh and logout

Execute `POST /api/v1/auth/refresh` with no body. Expect `200`. Auth atomically
consumes the refresh credential and replaces both authentication cookies.

After updating the CSRF header again:

- Execute `POST /api/v1/auth/logout` to revoke only the current device; expect
  `204`.
- Log in again, then execute `POST /api/v1/auth/logout-all` to revoke all
  active devices for that account; expect `204`.

After logout, the access and refresh cookies are cleared. A request that
requires `cookieAuth` returns generic `401` without exposing why the session is
invalid.

## 9. Test password reset

Execute `POST /api/v1/auth/password-resets/request`:

```json
{
  "email": "your-address+sahha-swagger-01@gmail.com"
}
```

Expect generic `202`. Copy the newest reset email's `token` query parameter and
execute `POST /api/v1/auth/password-resets/confirm`:

```json
{
  "token": "paste-the-token-from-the-newest-reset-link",
  "newPassword": "Replacement passphrase 2026!"
}
```

Expect `204`. It consumes the reset token, increments the credential version,
and revokes existing sessions. A later protected request with an old access
cookie must be rejected.

## 10. Test platform account administration

This endpoint requires a verified active account with a persisted
`PLATFORM_ADMIN` assignment. A signed role claim alone is not sufficient, and
Auth intentionally exposes no public endpoint for self-assigning that role.

Using a platform administrator session, execute
`PUT /api/v1/auth/platform/accounts/{userId}/status` with a synthetic target
account UUID:

```json
{
  "action": "SUSPEND"
}
```

Supported actions are `SUSPEND`, `REACTIVATE`, and `DISABLE`. Expect `204` for
a valid transition. Suspension and disablement revoke all target sessions;
reactivation applies only to a verified suspended account. A platform
administrator cannot target their own account through this endpoint.

## 11. Useful negative checks

- Omit `X-XSRF-TOKEN` from an unsafe request: expect safe `403`.
- Use an unknown email or wrong password at login: both return the same `401`.
- Reuse a rotated refresh cookie: expect `401`; that device family becomes
  compromised and requires a new login.
- Reuse an email/reset token: expect a generic token problem.
- Revoke a session owned by another account: expect generic `404`.
- Call the platform account endpoint as a non-admin: expect `403`.
- Exceed an endpoint's configured request limit: expect `429` with
  `Retry-After`.
- Submit invalid input: expect `400 application/problem+json` with safe field
  errors.

Responses carry `X-Request-Id`. Passwords, raw tokens, cookie values, and
account-existence details must not appear in response bodies or logs.

## 12. Automated verification

```powershell
.\mvnw.cmd -pl auth-service test
.\mvnw.cmd test
```

Verified result on 2026-08-01:

- Auth: 115 tests, 0 failures, 0 errors, 0 skipped.
- Organisation: 22 tests, 0 failures, 0 errors, 0 skipped.
- Gateway: 19 tests, 0 failures, 0 errors, 0 skipped.
- Frontend: 27 tests plus TypeScript typecheck and production build, all passed.

The Redis integration test uses native Memurai at `localhost:6379` and removes
only `sahha:auth:session:v1:*` keys before and after each test. Its outage test
uses an unused port and does not stop Memurai.

## Production switches

- Set `AUTH_OPENAPI_ENABLED=false` to disable Swagger and OpenAPI.
- Keep `AUTH_COOKIE_SECURE=true`.
- Keep `AUTH_JWT_EPHEMERAL_KEY_ENABLED=false`.
- Keep expired refresh-token lineage long enough for replay detection. The
  defaults purge it in batches seven days after the owning session's absolute
  expiry; tune `AUTH_REFRESH_TOKEN_RETENTION`,
  `AUTH_SESSION_CLEANUP_FAMILY_BATCH_SIZE`, and
  `AUTH_SESSION_CLEANUP_FIXED_DELAY` rather than deleting active history.
- Set `AUTH_OUTBOX_PUBLISHER_ENABLED=true` only when Kafka is available, then
  provide `KAFKA_BOOTSTRAP_SERVERS` and optionally
  `AUTH_OUTBOX_SECURITY_TOPIC`. The local default keeps publication disabled
  while still persisting every security event and its pending outbox row.
- Supply a persistent PKCS#8 RSA private key through
  `AUTH_JWT_PRIVATE_KEY_BASE64` and its X.509 public key through
  `AUTH_JWT_PUBLIC_KEY_BASE64`.

Ephemeral signing keys are enabled only by the local and test profiles; a local
restart therefore invalidates earlier access cookies.
