# Frontend Auth integration guide

Last updated: 2026-07-30

## What works

The React application now supports:

- patient account registration;
- registration validation with React Hook Form and Zod;
- the emailed `/verify-email?token=...` confirmation link;
- login through API Gateway;
- automatic CSRF bootstrap and stale-token recovery;
- HttpOnly access/refresh/device cookies;
- non-rotating startup restoration through the existing access cookie;
- browser-wide, session-first refresh coordination near access expiry;
- current-device logout;
- safe backend and connection errors.

The frontend never receives or stores an access/refresh token value.

## Start the complete local flow

PostgreSQL and Memurai must be running. Then start, in order:

1. `DiscoveryServerApplication` on `8761`.
2. `AuthServiceApplication (local)` on `8081`.
3. `ApiGatewayApplication` on `8079`.
4. The React development server:

```powershell
Set-Location C:\Users\LENOVO\Desktop\sahha\frontend
npm.cmd run dev
```

Open `http://localhost:5173/register`.

The ignored `frontend/.env.local` already selects:

```properties
VITE_USE_MOCKS=true
VITE_USE_AUTH_MOCKS=false
VITE_API_BASE_URL=http://localhost:8079/api/v1
```

This means authentication is real while unfinished patient, appointment,
clinical, and organisation APIs still use demo data.

## Register and verify

1. Open `http://localhost:5173/register`.
2. Enter synthetic first/last names and an email you can receive.
3. Use a password between 12 and 128 characters.
4. If supplied, use an E.164 phone such as `+21620123456`.
5. Select **Create patient account**.
6. Expect the “Verify your email” confirmation.
7. Open the Brevo email and select its verification link.
8. The browser opens `/verify-email`, removes the bearer token from the
   address bar, confirms it once, and shows “Your account is ready”.

In browser DevTools, the expected requests are:

| Request | Expected |
| --- | --- |
| `GET /api/v1/auth/csrf` | `200`, readable `XSRF-TOKEN` cookie |
| `POST /api/v1/auth/registrations` | `202` |
| `POST /api/v1/auth/email-verifications/confirm` | `204` |

## Sign in

1. Open `http://localhost:5173/login`.
2. Enter the verified email/password.
3. Select **Enter workspace**.
4. Expect `POST /api/v1/auth/login` to return `200`.
5. A normal registered account opens `/patient/overview`.

There is intentionally no role selector in real mode. The portal comes from
server metadata:

- signed `PLATFORM_ADMIN` global role → platform workspace;
- no global platform role → patient workspace.

Doctor, receptionist, staff, and organisation-administrator routing will be
added after Organisation Service validates memberships and organisation roles.

## Cookies and CSRF

The browser manages:

- `SAHHA_ACCESS_TOKEN` — HttpOnly;
- `SAHHA_REFRESH_TOKEN` — HttpOnly and Auth-path scoped;
- `SAHHA_DEVICE_ID` — HttpOnly;
- `XSRF-TOKEN` — readable only so the client can echo it in
  `X-XSRF-TOKEN`.

Every request uses `credentials: "include"`. The client obtains CSRF
automatically and retries an Auth mutation once if another tab rotated the
cookie.

On page reload, `GET /api/v1/auth/session` restores safe session metadata
without creating an access token, refresh token, session update, or database
write. When renewal is actually needed, a Web Lock named
`sahha-auth-refresh-v1` serialises refresh across tabs. After acquiring it, the
tab checks `/session` again; if another tab already renewed the shared cookies,
it skips `/refresh`.

## Current profile limitation

Auth returns user/session IDs, expiry values, and global platform roles, but it
does not yet return the user's email/name profile. The frontend therefore
keeps a minimal display-only identity projection in browser session storage
after login. It contains no credential, disappears with the browser session,
and grants no authority.

Organisation Service will later provide scoped membership, active
organisation, organisation role, and professional profile data.

## Verification commands

```powershell
Set-Location C:\Users\LENOVO\Desktop\sahha\frontend
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run validate:ui
```

Verified result:

- TypeScript: passed.
- Vitest: 27 files, 81 tests passed.
- Production build: passed.
- Browser layout suite: 11 checks passed, including registration at 375 px,
  consistent Sahha branding, keyboard entry, drawer behaviour, and no
  document-level horizontal overflow.

With the four local applications running, this reusable non-mutating check
submits only an unknown login and does not create a user:

```powershell
npm.cmd run smoke:auth
```

To verify the real doctor and receptionist browser workspaces from the
repository root, use the secure wrapper after the full local stack and frontend
are running:

```powershell
Set-Location C:\Users\LENOVO\Desktop\sahha
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\performance\Test-SahhaFrontendRoles.ps1
```

The wrapper prompts separately for both synthetic account passwords, passes
them to the child process only for the duration of the check, and removes the
environment values afterward. The check signs in through the real UI, selects
an organisation if required, verifies six doctor/receptionist route and
viewport combinations, checks the doctor notification panel, writes sanitized
screenshots under the temporary directory, and logs both sessions out. It does
not create or modify patients, availability, appointments, or notifications.

Verified on 2026-08-24: all six doctor/receptionist scenarios passed at 1440,
1024, and 375 px. Every body/document width matched its viewport, every title
used the Sahha suffix, Sahha branding was visible, the doctor notification panel
fit the phone viewport, and the reported API-error count was zero.

## Troubleshooting

- “Could not reach API Gateway”: confirm ports `8761`, `8081`, and `8079`, then
  restart Vite after changing an environment value.
- Browser `403`: inspect that the request contains both `XSRF-TOKEN` and
  `X-XSRF-TOKEN`; reload once to bootstrap a fresh pair.
- Browser CORS error: start the frontend at `http://localhost:5173`, matching
  Gateway's default allowed origin.
- Login `401` after registration: confirm the email link first.
- Role opens the patient portal: expected until Organisation Service supplies
  scoped roles; do not restore the old client-selected production role.

## Dependency audit note

PostCSS is forced to patched `8.5.25`, and React Router uses latest stable
`7.18.2`. npm still reports an advisory for React Server Components action
handling. This application is a client-only Vite `BrowserRouter` SPA and does
not enable React Server Components, server actions, route actions, or
`ScrollRestoration`.
