param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Force .local | Out-Null
$secretPath = Join-Path $PWD '.local/jwt-secret'
if (-not (Test-Path $secretPath)) {
  $secretBytes = New-Object byte[] 48
  [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($secretBytes)
  [IO.File]::WriteAllText($secretPath, [Convert]::ToBase64String($secretBytes))
}
$env:JWT_SECRET = [IO.File]::ReadAllText($secretPath)
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "$env:USERPROFILE/.jdks/ms-21.0.9" }
if (-not $SkipBuild) { & "$PSScriptRoot/maven.ps1" -B package; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
& "$env:JAVA_HOME/bin/java.exe" -jar target/ankidsa-backend-1.0.0.jar
