# Organisation Service Swagger guide

The current internship slice lets only a global `PLATFORM_ADMIN` create an
immediately active healthcare organisation and list or read organisations.
Organisation applications, document checks, and approve/reject processing are
deferred.

Use synthetic organisation data only.

## 1. Start the required services

Start Auth and Organisation Service in separate PowerShell terminals:

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

```powershell
.\mvnw.cmd -pl organisation-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

Auth must be reachable on port `8081` because Organisation Service validates
access-cookie signatures through Auth's public JWKS.

## 2. Prepare a Platform Administrator account

Register and verify an account with Auth first. Public registration does not
grant `PLATFORM_ADMIN`; the first local administrator is a trusted development
bootstrap operation.

In pgAdmin, connect to `sahha_auth` as its service owner and run this with the
verified account's email:

```sql
INSERT INTO user_platform_role (
    id,
    user_id,
    role_id,
    assigned_by_user_id,
    active
)
SELECT
    gen_random_uuid(),
    account.id,
    role.id,
    account.id,
    TRUE
FROM user_account account
JOIN platform_role role
    ON role.code = 'PLATFORM_ADMIN'
WHERE account.normalized_email = lower(btrim('replace-with-your-email'))
ON CONFLICT (user_id, role_id)
DO UPDATE SET
    active = TRUE,
    deactivated_at = NULL,
    updated_at = CURRENT_TIMESTAMP;
```

Confirm that one row was inserted or updated. Log out and log in again after
the assignment because the global role is embedded in the newly issued access
token. A production bootstrap workflow must replace this local SQL step.

## 3. Establish cookies and copy the CSRF token

1. Open Auth Swagger at `http://localhost:8081/swagger-ui.html`.
2. Execute `GET /api/v1/auth/csrf`.
3. In Swagger's **Authorize** dialog, paste the response `token` into
   `csrfHeader`.
4. Execute `POST /api/v1/auth/login` with the verified Platform Administrator.
5. Execute `GET /api/v1/auth/csrf` again and copy the current response token.

Auth writes the access token to the HttpOnly `SAHHA_ACCESS_TOKEN` cookie and
the CSRF value to the readable `XSRF-TOKEN` cookie. Both services use
`localhost` and cookie path `/`, so the browser sends them to Organisation
Service automatically.

## 4. Create and inspect an organisation

1. Open Organisation Swagger at
   `http://localhost:8082/swagger-ui.html`.
2. Click **Authorize** and paste the latest Auth CSRF response token into
   `csrfHeader`. The browser supplies `cookieAuth` automatically.
3. Execute `POST /api/v1/platform/organisations` with the generated synthetic
   example.
4. Expect `201 Created`, a `Location` header, and response status `ACTIVE`.
5. Execute `GET /api/v1/platform/organisations` and expect the new item.
6. Execute `GET /api/v1/platform/organisations/{organisationId}` with the
   returned UUID.

Creating the same organisation name with different case or surrounding spaces
returns `409 Conflict`. Invalid fields return `400` Problem Details.

## 5. Verify the security boundary

- Remove `X-XSRF-TOKEN` and retry the POST: expect `403`.
- Log in as a verified account without `PLATFORM_ADMIN`: list, read, and create
  all return `403`.
- Remove or expire the access cookie: protected operations return `401`.
- The same restrictions are applied at Gateway and again inside Organisation
  Service.

The creation transaction stores the organisation, an append-only audit event,
and a minimal outbox event. Contact email, phone number, and address are not
copied into the outbox payload.
