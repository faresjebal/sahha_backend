# Local SeaweedFS for Sahha

Sahha uses SeaweedFS's private S3-compatible gateway for medical-file bytes in
local development. PostgreSQL remains authoritative for medical-file metadata,
authorization, scan state, audit history, and short-lived grants.

The browser never connects to SeaweedFS. The supported path is:

```text
Browser -> API Gateway -> File Service -> SeaweedFS S3 gateway
```

## Installed local version

The verified Windows installation is SeaweedFS `4.41` at:

```text
C:\Users\LENOVO\seaweedfs\4.41\weed.exe
```

Use `4.30` or newer. Versions before `4.30` contain a patched S3 path-traversal
vulnerability and are not acceptable for Sahha.

## Start SeaweedFS

From PowerShell:

```powershell
$seaweedExe = 'C:\Users\LENOVO\seaweedfs\4.41\weed.exe'
$seaweedData = 'C:\Users\LENOVO\seaweedfs\data'
$s3Config = 'C:\Users\LENOVO\Desktop\sahha\config\seaweedfs\s3.local.json'
New-Item -ItemType Directory -Path $seaweedData -Force | Out-Null
& $seaweedExe server -dir=$seaweedData -ip=127.0.0.1 -ip.bind=127.0.0.1 -master.telemetry=false -master.volumeSizeLimitMB=1024 -volume.port=18080 -volume.max=20 -filer -filer.port=18888 -s3 -s3.ip.bind=127.0.0.1 -s3.port=8333 -s3.config=$s3Config
```

Keep that terminal open. The local endpoints are:

- S3 API used by File Service: `http://127.0.0.1:8333`
- Master status: `http://127.0.0.1:9333`
- Filer status: `http://127.0.0.1:18888`
- Internal volume endpoint: `http://127.0.0.1:18080`

The filer deliberately uses `18888`; Sahha's Spring Cloud Config Server owns
port `8888`. Starting SeaweedFS on its default filer port would prevent the
Config Server from starting.

The source-controlled credentials are synthetic local-development credentials
and must never be reused outside a developer machine. Production credentials
must come from the deployment secret manager.

## Verify the process

```powershell
Test-NetConnection 127.0.0.1 -Port 8333
Invoke-RestMethod http://127.0.0.1:9333/cluster/status
```

File Service defaults already match the local S3 endpoint, credentials,
region, and private `sahha-medical-files` bucket. The service lazily creates
that bucket on its first storage operation.

Run the explicit live adapter check from the repository root:

```powershell
$env:SEAWEEDFS_LIVE_TEST = 'true'
.\mvnw.cmd -pl file-service -Dtest=SeaweedFsLiveStorageIntegrationTests test
Remove-Item Env:SEAWEEDFS_LIVE_TEST
```

## Application upload path

An authenticated doctor first calls `POST /api/v1/files/uploads` through the
Gateway with the CSRF header/cookie pair and the consultation ID, safe original
filename, allowlisted content type, declared byte size, and optional SHA-256.
File Service revalidates the exact doctor, organisation, and consultation in
Clinical Service before returning a one-time opaque upload ticket.

The browser then sends the bytes to the returned Gateway path with `PUT`, the
same CSRF proof, and the ticket in `X-Upload-Token`. The request must have the
declared `Content-Type` and exact `Content-Length`. File Service consumes the
ticket once, streams to the private bucket, computes SHA-256, and leaves the
metadata in `STORED`/`PENDING`. A pending object is not downloadable.

Only the `local` profile enables the explicit development endpoint
`POST /api/v1/files/{fileId}/synthetic-scan`. It accepts either `CLEAN` or
`REJECTED`, requires the owning doctor's cookie/active organisation and the
CSRF proof, and creates append-only audit/outbox evidence. Clean retains the
private bytes; rejected makes the row unavailable and removes the bytes. The
endpoint is disabled by default and is not a production malware scanner.

SeaweedFS endpoint details, credentials, bucket names, and object keys are
never returned to the browser. File bytes do not pass through Kafka.

## Stop SeaweedFS

In the SeaweedFS terminal, press `Ctrl+C`. Do not delete the data directory as
part of an ordinary stop; it contains the local object bytes.
