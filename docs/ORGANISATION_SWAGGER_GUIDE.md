# Organisation Service Swagger guide

Last updated: 2026-08-04

The current internship slice lets only a global `PLATFORM_ADMIN` create an
immediately active healthcare organisation, inspect organisations, and assign
an existing active verified Sahha account as that organisation's
`ORGANIZATION_ADMIN`. That administrator can manage departments and invite
doctors or receptionists only in the active organisation. An authenticated,
verified account can accept or reject only invitations addressed to its exact
Auth email. Organisation applications, document checks, and approve/reject
processing are deferred.

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

## 5. Assign an organisation administrator

Before assigning an administrator, register and verify a second synthetic
Sahha account. It does not need a global platform role.

1. Execute
   `POST /api/v1/platform/organisations/{organisationId}/administrators` with:

   ```json
   {
     "email": "the-exact-verified-account@example.com"
   }
   ```

2. Expect `201 Created`, membership status `ACTIVE`, and role
   `ORGANIZATION_ADMIN`.
3. Execute
   `GET /api/v1/platform/organisations/{organisationId}/administrators`.
4. Execute
   `GET /api/v1/platform/organisations/{organisationId}/administrators/{membershipId}`.

Auth Service resolves the exact email. Organisation Service stores the Auth
user ID, a display-only identity snapshot, the organisation-scoped membership,
and its role. It never reads or writes the Auth database directly. An unknown,
pending, suspended, disabled, or already assigned account is rejected.

## 6. Discover and select the user's organisation context

1. Log out the Platform Administrator in Auth Swagger and log in as the
   assigned Organisation Administrator.
2. Refresh the Auth CSRF value, then open Organisation Swagger and update its
   `csrfHeader` authorisation value.
3. Execute `GET /api/v1/organisations/memberships`.
4. Expect only the signed-in user's active memberships in active organisations
   with at least one active role.
5. Execute
   `GET /api/v1/organisations/{organisationId}/membership-context` with the
   returned organisation ID.
6. In Auth Swagger, execute `POST /api/v1/auth/active-organisation` with that
   ID. Expect a new access-cookie context containing `ORGANIZATION_ADMIN`; the
   refresh token is deliberately unchanged.

These endpoints derive the user ID from the verified access token. They never
accept a user ID from the query string, request body, or browser storage.

## 7. Manage departments in the active organisation

After selecting the organisation in Auth Swagger, execute
`GET /api/v1/auth/csrf` again and update Organisation Swagger's `csrfHeader`
value. The browser now carries an access cookie with matching `org_id` and
`org_roles` claims.

1. Execute `POST /api/v1/departments` with synthetic data:

   ```json
   {
     "name": "Cardiology",
     "code": "CARD",
     "description": "Synthetic department data only."
   }
   ```

2. Expect `201 Created`, status `ACTIVE`, and version `0`.
3. Execute `GET /api/v1/departments`, then
   `GET /api/v1/departments/{departmentId}`.
4. Execute `PUT /api/v1/departments/{departmentId}` with the latest fields and
   response version. A successful update increments the version.
5. Execute `PUT /api/v1/departments/{departmentId}/status` with:

   ```json
   {
     "status": "INACTIVE",
     "version": 1
   }
   ```

6. Use the newly returned version to activate the department again.

The request never contains an organisation ID. Organisation Service obtains it
from the verified active-session token and then rechecks the live membership,
role, and organisation state in its own database. Names and codes are unique
inside one organisation. A duplicate returns `409`; a stale version also
returns `409` and requires reloading the department before retrying.

## 8. Invite and onboard a doctor or receptionist

While logged in as the Organisation Administrator with the organisation still
selected, create an invitation in Organisation Swagger:

1. Execute `POST /api/v1/staff-invitations` with:

   ```json
   {
     "email": "synthetic.doctor@example.test",
     "role": "DOCTOR"
   }
   ```

2. Expect `201 Created`, the authoritative `organisationName`, status
   `PENDING`, version `0`, and an expiry seven days in the future.
3. Execute `GET /api/v1/staff-invitations` and
   `GET /api/v1/staff-invitations/{invitationId}`.
4. Use `POST /api/v1/staff-invitations/{invitationId}/renew` with the latest
   version to extend a pending/expired invitation, or use `/revoke` to prevent
   acceptance.

The invited person must have a registered, verified, active Auth account with
that exact email before accepting. The invitation itself does not expose Auth
account search, and this slice does not yet send an invitation email; the
future Notification Service will add delivery together with a
privacy-preserving recipient-resolution contract.

To accept:

1. Log out the administrator and log in to Auth as the invited account.
2. Fetch a fresh CSRF token and update Organisation Swagger's `csrfHeader`.
3. Execute `GET /api/v1/my/staff-invitations`. No active organisation is
   required for this caller-owned endpoint.
4. Execute
   `POST /api/v1/my/staff-invitations/{invitationId}/accept` with:

   ```json
   {
     "version": 0
   }
   ```

5. Expect `ACCEPTED` and a non-null `acceptedMembershipId`.
6. Execute `GET /api/v1/organisations/memberships`, then select the new
   organisation through `POST /api/v1/auth/active-organisation` in Auth
   Swagger. The renewed access context now contains only the assigned
   organisation role.

Use `/reject` instead of `/accept` to decline. Every mutation requires the
latest response version. An expired, revoked, resolved, duplicated, or
membership-conflicting operation returns `409`.

## 9. Manage accepted staff and department assignments

Log back in as the Organisation Administrator, reselect the organisation, and
copy the refreshed CSRF token into Organisation Swagger.

1. Execute `GET /api/v1/staff`. It lists only accepted doctor and receptionist
   memberships in the active organisation, including historical department
   assignments and a doctor profile when one exists.
2. Execute `POST /api/v1/staff/{membershipId}/department-assignments` with an
   active department from section 7:

   ```json
   {
     "departmentId": "replace-with-department-uuid",
     "positionTitle": "Attending physician",
     "primaryAssignment": true,
     "startDate": "2026-08-04",
     "plannedEndDate": null
   }
   ```

3. End the assignment with
   `PUT /api/v1/staff/{membershipId}/department-assignments/{assignmentId}/end`,
   using the assignment's latest version:

   ```json
   {
     "endDate": "2026-08-04",
     "version": 0
   }
   ```

4. Suspend or reactivate access with
   `PUT /api/v1/staff/{membershipId}/status` and the membership's latest
   version. `SUSPENDED` is reversible; `REMOVED` is permanent and also ends
   active assignments and deactivates the doctor/receptionist role.

The request never accepts an organisation ID. A department or staff UUID from
another organisation is returned as `404`. A second active assignment to the
same department or a second active primary assignment returns `409`.

## 10. Complete the doctor's professional profile

Log in as the accepted doctor and select the organisation. In Organisation
Swagger, execute `PUT /api/v1/my/doctor-profile`:

```json
{
  "specialty": "Cardiology",
  "professionalTitle": "Consultant cardiologist",
  "licenceNumber": "SYNTHETIC-MED-123",
  "registrationAuthority": "Synthetic Medical Council",
  "biography": "Synthetic internship profile only.",
  "version": null
}
```

Use `version: null` only to create the profile. Read it with
`GET /api/v1/my/doctor-profile`; use the returned numeric version on every
update. The Organisation Administrator can read it through
`GET /api/v1/staff/{membershipId}/doctor-profile`, but cannot edit it.
Receptionists cannot read or write this endpoint. Licence uniqueness is
enforced inside the organisation; documentary licence verification remains a
deferred external process and the internship uses synthetic values only.

## 11. Verify the security boundary

- Remove `X-XSRF-TOKEN` and retry the POST: expect `403`.
- Log in as a verified account without `PLATFORM_ADMIN`: list, read, and create
  all return `403`.
- Remove or expire the access cookie: protected operations return `401`.
- The same restrictions are applied at Gateway and again inside Organisation
  Service.
- A user without `PLATFORM_ADMIN` cannot assign or inspect organisation
  administrators.
- Resolve a random organisation UUID while logged in as the assigned
  administrator: expect `404`, without revealing whether that organisation
  exists.
- An inactive membership, inactive organisation, or membership with no active
  role is absent from the available-context list and cannot be selected.
- A global `PLATFORM_ADMIN` without an active organisation administrator role
  receives `403` from department operations.
- A doctor or receptionist cannot use department-management operations.
- A department UUID belonging to another organisation returns `404` rather
  than revealing the other tenant's resource.
- A Platform Administrator without a live `ORGANIZATION_ADMIN` membership in
  the active organisation cannot manage its invitations.
- An administrator using a different active organisation receives `404` for
  another organisation's invitation UUID.
- An authenticated account whose verified Auth email differs from the target
  receives `404` when it tries to read or accept the invitation.
- Suspending the membership in Organisation Service immediately makes
  department access return `403`, even if an older token still carries the
  organisation role.
- A doctor receives `403` from `/api/v1/staff`, and a receptionist receives
  `403` from `/api/v1/my/doctor-profile`.
- An administrator using a staff, department, or assignment UUID from another
  organisation receives `404`.
- Doctor-profile outbox events contain identifiers and lifecycle metadata, not
  the specialty, licence number, registration authority, or biography.

The organisation-creation, administrator-assignment, and department-mutation
transactions each store their aggregate change, an append-only audit event,
and a minimal outbox event. Organisation contact data, administrator
email/display-name snapshots, department descriptions, and invitation email
addresses are not copied into outbox payloads. Staff lifecycle, department
assignment, and doctor-profile mutations follow the same audit/outbox rule.
