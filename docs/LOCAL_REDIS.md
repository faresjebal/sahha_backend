# Local Redis for Sahha Auth

Last updated: 2026-07-28

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

## Alternative: Docker Desktop

Docker Desktop is already installed on this development machine. Start Docker
Desktop and wait until its engine reports that it is running.

From PowerShell, verify Docker:

```powershell
docker version
```

Check whether the Sahha Redis container already exists:

```powershell
docker ps --all --filter "name=sahha-redis"
```

If it does not exist, create it:

```powershell
docker run --detach --name sahha-redis --restart unless-stopped --publish 127.0.0.1:6379:6379 redis:8.2-alpine redis-server --appendonly no --protected-mode yes
```

The host mapping is restricted to `127.0.0.1`; it does not publish Redis on
external network interfaces. No volume is attached because this data must be
safe to lose.

If the container already exists but is stopped, start it:

```powershell
docker start sahha-redis
```

Verify Redis itself:

```powershell
docker exec sahha-redis redis-cli ping
```

The expected result is:

```text
PONG
```

Verify the Windows port:

```powershell
Test-NetConnection localhost -Port 6379
```

`TcpTestSucceeded` should be `True`.

Useful lifecycle commands:

```powershell
docker stop sahha-redis
docker start sahha-redis
docker logs sahha-redis
```

## Alternative: Redis inside WSL2 Ubuntu

This machine has WSL2 enabled but currently has no Linux distribution. Open an
Administrator PowerShell window and install Ubuntu:

```powershell
wsl --install -d Ubuntu
```

Windows may request a restart. Open Ubuntu once and complete its username and
password setup.

Inside Ubuntu, install Redis from the official Redis APT repository:

```bash
sudo apt-get install lsb-release curl gpg
curl -fsSL https://packages.redis.io/gpg | sudo gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
sudo chmod 644 /usr/share/keyrings/redis-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/redis-archive-keyring.gpg] https://packages.redis.io/deb $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/redis.list
sudo apt-get update
sudo apt-get install redis
redis-server --daemonize yes --bind 127.0.0.1 --protected-mode yes
redis-cli ping
```

The expected result is `PONG`. Windows normally forwards WSL2 localhost ports,
so `Test-NetConnection localhost -Port 6379` should then succeed from
PowerShell.

Use either Docker or WSL, not both on port `6379`.

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

Inspect the Sahha session keys when using Docker:

```powershell
docker exec sahha-redis redis-cli --scan --pattern "sahha:auth:session:v1:*"
```

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
