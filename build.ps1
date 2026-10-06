# Build / test CodeBox.
#
# Usage:
#   .\build.ps1                                # Java tests + frontend unit tests + SFC check + legacy smoke
#   .\build.ps1 clean package -WithFrontend    # build the Vue frontend, then package the jar (delivery)
#   .\build.ps1 package                        # package only (frontend already built)
#
# Why this script exists: Maven writes to %USERPROFILE%\.m2\repository, which is
# outside the project directory. In restricted environments that write is denied and
# surfaces as a confusing "AccessDeniedException", easily mistaken for a network
# problem. This points Maven at a workspace-local repository (.m2-local/, gitignored,
# safe to delete - the script recreates it).
#
# NOTE: this file is deliberately ASCII-only. Windows PowerShell 5.1 reads .ps1 files
# as ANSI unless they carry a UTF-8 BOM, and git (with core.autocrlf) strips the BOM on
# checkout - which silently broke an earlier, Chinese-commented version of this script.
# Staying ASCII removes the whole class of problem.

param(
    [switch]$WithFrontend,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Goals = @('test')
)

$ErrorActionPreference = 'Stop'
$localRepo = Join-Path $PSScriptRoot '.m2-local'
$sourceRepo = Join-Path $env:USERPROFILE '.m2\repository'
$frontendDir = Join-Path $PSScriptRoot 'frontend'

function Clear-NpmNoise {
    # npm 11 rejects an allow-scripts setting when it appears in a project-scoped
    # install (some environments export it); the cache is redirected into the
    # workspace so nothing is written to %LOCALAPPDATA%.
    Remove-Item Env:\npm_config_allow_scripts -ErrorAction SilentlyContinue
    Remove-Item Env:\npm_config_prefix -ErrorAction SilentlyContinue
    $env:npm_config_cache = Join-Path $frontendDir '.npm-cache'
}

# ---------------------------------------------------------------- frontend (optional)

if ($WithFrontend) {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        throw "-WithFrontend requires Node.js, but the node command was not found."
    }
    Write-Host "==> Building Vue frontend (frontend/)" -ForegroundColor Cyan
    Push-Location $frontendDir
    try {
        Clear-NpmNoise
        if (-not (Test-Path (Join-Path $frontendDir 'node_modules'))) {
            Write-Host "    Installing dependencies (first run only)..." -ForegroundColor Cyan
            # --ignore-scripts is safe: the esbuild binary vite needs ships via
            # optionalDependencies, not via a postinstall download.
            npm install --no-audit --no-fund --ignore-scripts
            if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
        }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "frontend build failed" }
    } finally {
        Pop-Location
    }
}

# ---------------------------------------------------------------- maven

if (-not (Test-Path $localRepo)) {
    if (-not (Test-Path $sourceRepo)) {
        throw "No Maven repository at $sourceRepo - run a normal 'mvn' build once first."
    }
    Write-Host "Copying Maven repository into .m2-local (one time, ~280MB)..." -ForegroundColor Cyan
    robocopy $sourceRepo $localRepo /E /NFL /NDL /NJH /NJS /NP /R:0 /W:0 | Out-Null
    Write-Host "Done." -ForegroundColor Cyan
}

Write-Host "mvn -o $($Goals -join ' ')  (repo: .m2-local)" -ForegroundColor Cyan
& mvn -o -B "-Dmaven.repo.local=$localRepo" @Goals
$mvnExit = $LASTEXITCODE

# ---------------------------------------------------------------- checks

function Invoke-NodeCheck([string]$Label, [string]$Script) {
    if (-not (Test-Path $Script)) { return 0 }
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        Write-Host "node not found - skipping: $Label" -ForegroundColor Yellow
        return 0
    }
    Write-Host ""
    Write-Host "==> $Label" -ForegroundColor Cyan
    # Call directly and stream with Out-Host: piping or capturing would swallow node's
    # output and make the exit code unreliable.
    & node $Script | Out-Host
    $code = $LASTEXITCODE
    if ($code -ne 0) { Write-Host "    FAILED (exit $code)" -ForegroundColor Red }
    return $code
}

$checks = @(
    @{ Label = 'Frontend unit tests (frontend/test/utils.test.js)'; Script = (Join-Path $frontendDir 'test\utils.test.js') },
    @{ Label = 'Vue SFC static check';                              Script = (Join-Path $frontendDir 'test\sfc-check.js') },
    @{ Label = 'Legacy UI smoke check';                             Script = (Join-Path $PSScriptRoot '.uitest\harness.js') }
)

foreach ($c in $checks) {
    $code = Invoke-NodeCheck $c.Label $c.Script
    if ($code -ne 0) { $mvnExit = 1 }
}

exit $mvnExit
