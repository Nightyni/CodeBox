# Build/test CodeBox using a workspace-local Maven repository.
#
# Why this exists: Maven writes to %USERPROFILE%\.m2\repository, which is outside
# the project directory. In restricted/sandboxed environments that write is denied,
# which shows up as a confusing "AccessDeniedException: ...\.m2\repository\..." even
# though the network is fine. Pointing Maven at a repo inside the workspace avoids it.
#
# Usage:
#   .\build.ps1              # run tests
#   .\build.ps1 package      # build the runnable jar
#   .\build.ps1 clean test   # any Maven goals
#
# The local repo is a copy of your real one and is gitignored. Delete .m2-local/
# any time; this script re-creates it on demand.

param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Goals = @('test')
)

$ErrorActionPreference = 'Stop'
$localRepo = Join-Path $PSScriptRoot '.m2-local'
$sourceRepo = Join-Path $env:USERPROFILE '.m2\repository'

if (-not (Test-Path $localRepo)) {
    if (-not (Test-Path $sourceRepo)) {
        throw "No Maven repository found at $sourceRepo - run a normal 'mvn' build once first."
    }
    Write-Host "Copying Maven repository into .m2-local (one time, ~280MB)..." -ForegroundColor Cyan
    robocopy $sourceRepo $localRepo /E /NFL /NDL /NJH /NJS /NP /R:0 /W:0 | Out-Null
    Write-Host "Done." -ForegroundColor Cyan
}

Write-Host "mvn -o $($Goals -join ' ')  (repo: .m2-local)" -ForegroundColor Cyan
& mvn -o -B "-Dmaven.repo.local=$localRepo" @Goals
exit $LASTEXITCODE
