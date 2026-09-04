[CmdletBinding()]
param(
    [ValidateSet(
        'PlatformAdministrator',
        'OrganisationAdministrator',
        'Doctor',
        'Receptionist',
        'Patient'
    )]
    [string]$Role = 'Doctor',

    [ValidateRange(5, 500)]
    [int]$WarmSamples = 30,

    [string]$BaseUri = 'http://localhost:8079/api/v1',

    [string]$Email,

    [string]$OrganisationId,

    [string]$OutputPath,

    [switch]$FirstRequestIsCold,

    [switch]$ProbeOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-SahhaPercentile {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Values,

        [Parameter(Mandatory = $true)]
        [ValidateRange(0.0, 1.0)]
        [double]$Percentile
    )

    $ordered = @($Values | Sort-Object)
    $index = [Math]::Max(
        0,
        [Math]::Ceiling($Percentile * $ordered.Count) - 1
    )
    return [double]$ordered[$index]
}

function Get-SahhaCsrf {
    param(
        [Parameter(Mandatory = $true)]
        $WebSession
    )

    return Invoke-RestMethod `
        -Uri "$BaseUri/auth/csrf" `
        -WebSession $WebSession `
        -TimeoutSec 15
}

function Invoke-SahhaPost {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        $Body,

        [Parameter(Mandatory = $true)]
        $WebSession
    )

    $csrf = Get-SahhaCsrf -WebSession $WebSession
    $headers = @{
        $csrf.headerName = $csrf.token
    }
    $parameters = @{
        Method = 'Post'
        Uri = "$BaseUri$Path"
        WebSession = $WebSession
        Headers = $headers
        TimeoutSec = 15
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }

    return Invoke-WebRequest @parameters
}

function Invoke-SahhaGetSample {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        $WebSession
    )

    $timer = [Diagnostics.Stopwatch]::StartNew()
    $status = 0
    $requestId = $null
    try {
        $response = Invoke-WebRequest `
            -Uri "$BaseUri$Path" `
            -WebSession $WebSession `
            -TimeoutSec 15 `
            -UseBasicParsing
        $status = [int]$response.StatusCode
        $requestId = $response.Headers['X-Request-Id']
    }
    catch {
        if ($null -ne $_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            $requestId = $_.Exception.Response.Headers['X-Request-Id']
        }
    }
    finally {
        $timer.Stop()
    }

    return [pscustomobject]@{
        Status = $status
        Milliseconds = [double]$timer.Elapsed.TotalMilliseconds
        RequestId = $requestId
    }
}

function Measure-SahhaEndpoint {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [int]$TargetP95Milliseconds,

        [Parameter(Mandatory = $true)]
        $WebSession
    )

    Write-Progress -Activity 'Measuring Sahha Gateway' -Status $Name
    $first = Invoke-SahhaGetSample -Path $Path -WebSession $WebSession
    Invoke-SahhaGetSample -Path $Path -WebSession $WebSession | Out-Null

    $samples = @(
        1..$WarmSamples | ForEach-Object {
            Invoke-SahhaGetSample -Path $Path -WebSession $WebSession
        }
    )
    $durations = [double[]]@($samples | ForEach-Object { $_.Milliseconds })
    $errorCount = @($samples | Where-Object { $_.Status -ge 400 -or $_.Status -eq 0 }).Count
    $p50 = [Math]::Round((Get-SahhaPercentile -Values $durations -Percentile 0.50), 2)
    $p95 = [Math]::Round((Get-SahhaPercentile -Values $durations -Percentile 0.95), 2)
    $p99 = [Math]::Round((Get-SahhaPercentile -Values $durations -Percentile 0.99), 2)
    $maximum = [Math]::Round(($durations | Measure-Object -Maximum).Maximum, 2)

    return [pscustomobject]@{
        Scenario = $Name
        Path = $Path
        Count = $WarmSamples
        FirstRequestMs = [Math]::Round($first.Milliseconds, 2)
        FirstRequestStatus = $first.Status
        FirstRequestId = $first.RequestId
        FirstRequestIsCold = [bool]$FirstRequestIsCold
        P50ms = $p50
        P95ms = $p95
        P99ms = $p99
        Maxms = $maximum
        Errors = $errorCount
        TargetP95ms = $TargetP95Milliseconds
        Result = if ($errorCount -eq 0 -and $p95 -le $TargetP95Milliseconds) {
            'PASS'
        }
        else {
            'INVESTIGATE'
        }
    }
}

function Get-SahhaScenarios {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SelectedRole
    )

    $now = [DateTimeOffset]::UtcNow
    $from = [Uri]::EscapeDataString($now.AddDays(-30).ToString('o'))
    $to = [Uri]::EscapeDataString($now.ToString('o'))
    $common = @(
        [pscustomobject]@{
            Name = 'Auth session read'
            Path = '/auth/session'
            TargetP95Milliseconds = 250
        },
        [pscustomobject]@{
            Name = 'Organisation memberships'
            Path = '/organisations/memberships'
            TargetP95Milliseconds = 500
        }
    )

    $roleScenarios = switch ($SelectedRole) {
        'PlatformAdministrator' {
            @(
                [pscustomobject]@{
                    Name = 'Platform organisation list'
                    Path = '/platform/organisations?page=0&size=100'
                    TargetP95Milliseconds = 500
                }
            )
        }
        'OrganisationAdministrator' {
            @(
                [pscustomobject]@{
                    Name = 'Department list'
                    Path = '/departments?page=0&size=100'
                    TargetP95Milliseconds = 500
                },
                [pscustomobject]@{
                    Name = 'Staff directory'
                    Path = '/staff?page=0&size=100'
                    TargetP95Milliseconds = 500
                }
            )
        }
        'Doctor' {
            @(
                [pscustomobject]@{
                    Name = 'Doctor availability'
                    Path = '/availability/me'
                    TargetP95Milliseconds = 500
                },
                [pscustomobject]@{
                    Name = 'Appointment history'
                    Path = "/appointments?from=$from&to=$to"
                    TargetP95Milliseconds = 500
                },
                [pscustomobject]@{
                    Name = 'Notification inbox'
                    Path = '/notifications?page=0&size=20'
                    TargetP95Milliseconds = 250
                }
            )
        }
        'Receptionist' {
            @(
                [pscustomobject]@{
                    Name = 'Patient directory'
                    Path = '/patients?page=0&size=50&query='
                    TargetP95Milliseconds = 500
                },
                [pscustomobject]@{
                    Name = 'Available doctors'
                    Path = '/availability/doctors'
                    TargetP95Milliseconds = 500
                },
                [pscustomobject]@{
                    Name = 'Appointment history'
                    Path = "/appointments?from=$from&to=$to"
                    TargetP95Milliseconds = 500
                }
            )
        }
        'Patient' {
            @(
                [pscustomobject]@{
                    Name = 'Patient registration links'
                    Path = '/patients/me/registrations'
                    TargetP95Milliseconds = 500
                }
            )
        }
    }

    return @($common + $roleScenarios)
}

$BaseUri = $BaseUri.TrimEnd('/')
$probeSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$probeTimer = [Diagnostics.Stopwatch]::StartNew()
$probe = Get-SahhaCsrf -WebSession $probeSession
$probeTimer.Stop()
if ([string]::IsNullOrWhiteSpace($probe.headerName) -or
    [string]::IsNullOrWhiteSpace($probe.token)) {
    throw 'Gateway CSRF probe returned an invalid response.'
}
Write-Host "Gateway probe succeeded in $([Math]::Round($probeTimer.Elapsed.TotalMilliseconds, 2)) ms."

if ($ProbeOnly) {
    Write-Host 'Probe-only validation completed; no login session was created.'
    return
}

if ([string]::IsNullOrWhiteSpace($Email)) {
    $Email = Read-Host "Synthetic $Role email"
}
if ([string]::IsNullOrWhiteSpace($Email)) {
    throw 'A synthetic account email is required.'
}

$securePassword = Read-Host 'Synthetic account password' -AsSecureString
$plainPassword = [Net.NetworkCredential]::new('', $securePassword).Password
$webSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loggedIn = $false

try {
    $loginResponse = Invoke-SahhaPost `
        -Path '/auth/login' `
        -Body @{
            email = $Email.Trim().ToLowerInvariant()
            password = $plainPassword
            deviceName = 'Sahha local Gateway performance sampler'
        } `
        -WebSession $webSession
    $loginSession = $loginResponse.Content | ConvertFrom-Json
    $loggedIn = $true
    $plainPassword = $null
    $securePassword = $null

    $organisationScopedRoles = @(
        'OrganisationAdministrator',
        'Doctor',
        'Receptionist'
    )
    if ($organisationScopedRoles -contains $Role) {
        $selectedOrganisationId = $loginSession.activeOrganisationId
        if (-not [string]::IsNullOrWhiteSpace($OrganisationId)) {
            $selectedOrganisationId = $OrganisationId
        }
        if ([string]::IsNullOrWhiteSpace([string]$selectedOrganisationId)) {
            $contextResponse = Invoke-WebRequest `
                -Uri "$BaseUri/organisations/memberships" `
                -WebSession $webSession `
                -TimeoutSec 15 `
                -UseBasicParsing
            $contexts = @($contextResponse.Content | ConvertFrom-Json)
            if ($contexts.Count -eq 0) {
                throw 'The account has no active organisation membership.'
            }
            $selectedOrganisationId = $contexts[0].organisationId
        }
        if ($loginSession.activeOrganisationId -ne $selectedOrganisationId) {
            $selectionResponse = Invoke-SahhaPost `
                -Path '/auth/active-organisation' `
                -Body @{ organisationId = $selectedOrganisationId } `
                -WebSession $webSession
            $loginSession = $selectionResponse.Content | ConvertFrom-Json
        }
    }

    $requiredRole = switch ($Role) {
        'PlatformAdministrator' { 'PLATFORM_ADMIN' }
        'OrganisationAdministrator' { 'ORGANIZATION_ADMIN' }
        'Doctor' { 'DOCTOR' }
        'Receptionist' { 'RECEPTIONIST' }
        default { $null }
    }
    if ($Role -eq 'PlatformAdministrator' -and
        $loginSession.platformRoles -notcontains $requiredRole) {
        throw 'The authenticated account is not a Platform Administrator.'
    }
    if ($null -ne $requiredRole -and
        $Role -ne 'PlatformAdministrator' -and
        $loginSession.organisationRoles -notcontains $requiredRole) {
        throw "The selected organisation does not grant the expected $Role role."
    }

    $scenarios = Get-SahhaScenarios -SelectedRole $Role
    $results = @(
        foreach ($scenario in $scenarios) {
            Measure-SahhaEndpoint `
                -Name $scenario.Name `
                -Path $scenario.Path `
                -TargetP95Milliseconds $scenario.TargetP95Milliseconds `
                -WebSession $webSession
        }
    )
    Write-Progress -Activity 'Measuring Sahha Gateway' -Completed

    $report = [ordered]@{
        measuredAt = [DateTimeOffset]::Now.ToString('o')
        machine = $env:COMPUTERNAME
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        baseUri = $BaseUri
        role = $Role
        userId = $loginSession.userId
        activeOrganisationId = $loginSession.activeOrganisationId
        firstRequestIsCold = [bool]$FirstRequestIsCold
        warmSamplesPerScenario = $WarmSamples
        results = $results
    }

    if ([string]::IsNullOrWhiteSpace($OutputPath)) {
        $safeRole = $Role.ToLowerInvariant()
        $OutputPath = Join-Path $env:TEMP "sahha-gateway-baseline-$safeRole.json"
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding UTF8

    $results |
        Select-Object Scenario, Count, FirstRequestMs, P50ms, P95ms, P99ms, Maxms, Errors, TargetP95ms, Result |
        Format-Table -AutoSize
    Write-Host "Sanitized report: $OutputPath"
}
finally {
    $plainPassword = $null
    $securePassword = $null
    if ($loggedIn) {
        try {
            Invoke-SahhaPost -Path '/auth/logout' -Body $null -WebSession $webSession | Out-Null
        }
        catch {
            Write-Warning 'The measurement completed, but the temporary session could not be logged out.'
        }
    }
}
