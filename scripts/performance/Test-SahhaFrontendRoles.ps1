[CmdletBinding()]
param(
    [string]$FrontendUrl = 'http://localhost:5173',
    [string]$GatewayUrl = 'http://localhost:8079',
    [string]$DoctorEmail = 'ffaresjebali+sahha-doctor@gmail.com',
    [string]$ReceptionistEmail = 'ffaresjebali+sahha-receptionist@gmail.com'
)

$ErrorActionPreference = 'Stop'
$frontendRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\frontend')).Path
$doctorPassword = Read-Host 'Synthetic doctor account password' -AsSecureString
$receptionistPassword = Read-Host 'Synthetic receptionist account password' -AsSecureString
$doctorPlainText = [Net.NetworkCredential]::new('', $doctorPassword).Password
$receptionistPlainText = [Net.NetworkCredential]::new('', $receptionistPassword).Password

try {
    $env:SAHHA_FRONTEND_URL = $FrontendUrl
    $env:SAHHA_GATEWAY_URL = $GatewayUrl
    $env:SAHHA_DOCTOR_EMAIL = $DoctorEmail.Trim().ToLowerInvariant()
    $env:SAHHA_DOCTOR_PASSWORD = $doctorPlainText
    $env:SAHHA_RECEPTIONIST_EMAIL = $ReceptionistEmail.Trim().ToLowerInvariant()
    $env:SAHHA_RECEPTIONIST_PASSWORD = $receptionistPlainText

    Push-Location $frontendRoot
    try {
        & npm.cmd run smoke:roles
        if ($LASTEXITCODE -ne 0) {
            throw "The live frontend role smoke test failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    Remove-Item Env:SAHHA_FRONTEND_URL -ErrorAction SilentlyContinue
    Remove-Item Env:SAHHA_GATEWAY_URL -ErrorAction SilentlyContinue
    Remove-Item Env:SAHHA_DOCTOR_EMAIL -ErrorAction SilentlyContinue
    Remove-Item Env:SAHHA_DOCTOR_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SAHHA_RECEPTIONIST_EMAIL -ErrorAction SilentlyContinue
    Remove-Item Env:SAHHA_RECEPTIONIST_PASSWORD -ErrorAction SilentlyContinue
    $doctorPlainText = $null
    $receptionistPlainText = $null
    $doctorPassword = $null
    $receptionistPassword = $null
}
