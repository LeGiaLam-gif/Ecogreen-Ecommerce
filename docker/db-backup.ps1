param(
    [string]$OutputDir = "backups",
    [string]$Container = "ecogreen-postgres",
    [string]$Database = "ecogreen",
    [string]$Username = "postgres"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupFile = Join-Path $OutputDir "ecogreen-$timestamp.sql"

Write-Host "Creating backup $backupFile from $Container/$Database..."
docker exec $Container pg_dump -U $Username -d $Database --clean --if-exists > $backupFile
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed with exit code $LASTEXITCODE"
}
Write-Host "Backup complete: $backupFile"
