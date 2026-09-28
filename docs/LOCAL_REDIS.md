# Local Redis for Sahha Auth

Last updated: 2026-09-11

Auth expects Redis at `localhost:6379` by default. Redis is a disposable cache;
PostgreSQL remains the source of truth.

## Recommended Windows setup: Memurai

This development machine uses the native Memurai Windows service so Docker
Desktop and WSL do not need to run.

Verify the service and Redis protocol:

```powershell
Get-Service Memurai
& "C:\Program Files\Memurai\memurai-cli.exe" PING
```

The service should be `Running` and the command should return `PONG`.

Inspect the settings selected for Sahha's disposable session cache:

```powershell
& "C:\Program Files\Memurai\memurai-cli.exe" CONFIG GET maxmemory
& "C:\Program Files\Memurai\memurai-cli.exe" CONFIG GET maxmemory-policy
& "C:\Program Files\Memurai\memurai-cli.exe" CONFIG GET save
& "C:\Program Files\Memurai\memurai-cli.exe" CONFIG GET appendonly
```

The current local configuration is 256 MB, `allkeys-lru`, no snapshot schedule
and append-only persistence disabled. PostgreSQL remains authoritative.

Inspect only Sahha Auth session keys:

```powershell
& "C:\Program Files\Memurai\memurai-cli.exe" --scan --pattern "sahha:auth:session:v1:*"
```

Redis Insight is an optional graphical interface. Connect it to
`localhost:6379` without a username or password for this local-only setup.

Docker and WSL setup are not part of the current V1 workflow. Use the native
server above; no container commands are required.

## Sahha configuration

No additional file is required for the recommended local setup. Auth defaults
to:

```text
host: localhost
port: 6379
database: 0
active cache TTL: at most 30 seconds
```

For custom settings, copy `.env.redis.example` to `.env.redis.local`. The local
file is ignored by Git and imported by the Auth `local` profile.

Start Auth:

```powershell
.\mvnw.cmd -pl auth-service spring-boot:run "-Dspring-boot.run.profiles=local"
```

The relevant environment variables are:

| Variable | Default |
| --- | --- |
| `AUTH_SESSION_CACHE_ENABLED` | `true` |
| `AUTH_REDIS_HOST` | `localhost` |
| `AUTH_REDIS_PORT` | `6379` |
| `AUTH_REDIS_DATABASE` | `0` |
| `AUTH_REDIS_USERNAME` | empty |
| `AUTH_REDIS_PASSWORD` | empty |
| `AUTH_REDIS_CONNECT_TIMEOUT` | `500ms` |
| `AUTH_REDIS_COMMAND_TIMEOUT` | `1s` |
| `AUTH_SESSION_CACHE_MAXIMUM_ACTIVE_TTL` | `PT30S` |

Redis stores only keys shaped like:

```text
sahha:auth:session:v1:{sessionId}
```

Use the native `memurai-cli.exe --scan` command above to inspect only Sahha's
session-key prefix. Never run `FLUSHALL` against a shared Redis server.

If Redis stops, Auth falls back to PostgreSQL. The standard Auth health endpoint
does not become unhealthy solely because this optional cache is unavailable.

## Automated Redis verification

The Redis integration test uses the native server configured by
`AUTH_TEST_REDIS_HOST` and `AUTH_TEST_REDIS_PORT` (defaults:
`localhost:6379`). It deletes only keys under the Sahha Auth test prefix before
and after every test:

```powershell
.\mvnw.cmd -pl auth-service "-Dtest=RedisSessionCacheIntegrationTests,RedisSessionCacheOutageIntegrationTests" test
```

These tests verify real Redis JSON, TTL, atomic version checks, tombstones,
cache-aside loading and logout invalidation. The outage test points Auth at an
unused local port, so it verifies PostgreSQL fallback without stopping Memurai.
