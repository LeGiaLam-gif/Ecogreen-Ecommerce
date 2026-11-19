param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$Container = "ecogreen-postgres",
    [string]$Database = "ecogreen",
    [string]$Username = "postgres"
)

$ErrorActionPreference = "Stop"
if (-not (Test-Path $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}

Write-Host "Restoring $BackupFile into $Container/$Database..."
Get-Content -Raw -Encoding UTF8 $BackupFile | docker exec -i $Container psql -v ON_ERROR_STOP=1 -U $Username -d $Database
if ($LASTEXITCODE -ne 0) {
    throw "psql restore failed with exit code $LASTEXITCODE"
}
Write-Host "Restore complete."
