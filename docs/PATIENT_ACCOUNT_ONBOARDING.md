# New patient accounts and clinic registrations

A verified Sahha identity and a clinic's patient registration are separate.
Verification enables sign-in. A clinic's receptionist creates its patient
registration; the patient then links it using the organisation, medical record
number, date of birth and the same verified email.

A new identity can legitimately have no registration. The backend currently
represents this on GET /api/v1/patients/me/registrations using HTTP 404 and
urn:sahha:problem:patient-account-link-not-found. The frontend adapter maps that
one response to an empty list, allowing the overview and doctor-search pages to
present a starting point rather than an error.

The exception applies only to listing. Explicit link requests, scheduling
contexts and protected reads keep their existing ownership checks. Unrelated
HTTP failures remain visible. No clinic membership, patient identity,
appointment or access grant is manufactured for a new account.

Validation: 27 local tests cover the adapter, workspace, appointments and
notification entry points; TypeScript checking and production compilation pass.
The original frontend sources were compared with the deployment branch before
applying this narrow change.

Deploy using the existing Azure pipeline on feature/organisation-service with
deployToDemo=true. Its hosted tests and release checks remain required. After
activation, reload the signed-in patient overview: a fresh identity shows
Your account is ready and a Connect a clinic record action. Without a clinic
record, no doctor or appointment request is made. Linked patients continue to
see only their own existing registrations and appointments.

If the message appears after Verify and connect, check the actual clinic-issued
details and the verified email. Do not guess identifiers, change someone else's
registration or bypass the backend match.
