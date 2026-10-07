$projectRoot = $PSScriptRoot
$frontendDir = Join-Path $projectRoot "frontend"
$jarPath = Join-Path $projectRoot "target\codebox.jar"
$localConfig = Join-Path $projectRoot "config\application-local.yml"

Write-Host ""
Write-Host "========================================"
Write-Host "          CodeBox Launcher"
Write-Host "========================================"
Write-Host ""

# 检查 MySQL
Write-Host "[1/3] Checking MySQL..." -ForegroundColor Cyan

$mysql = Get-Service | Where-Object {
    $_.Name -like "*mysql*" -and $_.Status -eq "Running"
}

if (-not $mysql) {
    Write-Host "[ERROR] MySQL is not running." -ForegroundColor Red
    Write-Host "Please start MySQL first."
    pause
    exit 1
}

Write-Host "[OK] MySQL is running." -ForegroundColor Green

# 检查后端文件
if (-not (Test-Path $jarPath)) {
    Write-Host "[ERROR] Backend JAR not found:" -ForegroundColor Red
    Write-Host $jarPath
    Write-Host ""
    Write-Host "Please run Maven package first." -ForegroundColor Yellow
    pause
    exit 1
}

# 检查本地配置
if (-not (Test-Path $localConfig)) {
    Write-Host "[ERROR] Local configuration not found:" -ForegroundColor Red
    Write-Host $localConfig
    pause
    exit 1
}

# 检查前端
if (-not (Test-Path $frontendDir)) {
    Write-Host "[ERROR] Frontend directory not found:" -ForegroundColor Red
    Write-Host $frontendDir
    pause
    exit 1
}

# 启动后端
Write-Host "[2/3] Starting Spring Boot backend..." -ForegroundColor Cyan

Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$projectRoot'; java -jar '$jarPath' --spring.config.additional-location=optional:./config/application-local.yml"
)

Start-Sleep -Seconds 4

# 启动前端
Write-Host "[3/3] Starting Vue frontend..." -ForegroundColor Cyan

Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$frontendDir'; npm run dev"
)

Start-Sleep -Seconds 5

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "          CodeBox started"
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Frontend : http://localhost:5173" -ForegroundColor Cyan
Write-Host "Backend  : http://localhost:8080" -ForegroundColor Cyan
Write-Host "MySQL    : localhost:3306" -ForegroundColor Cyan
Write-Host ""
Write-Host "Opening CodeBox..." -ForegroundColor Yellow
Write-Host ""

Start-Process "http://localhost:5173"