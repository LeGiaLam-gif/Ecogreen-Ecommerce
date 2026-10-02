$users = @('postgres', 'odoo', 'duy', 'DUY', 'Administrator')
$passes = @('123456', 'postgres', 'odoo', 'admin', 'root', '1234', '123', 'duy', '')
foreach ($u in $users) {
  foreach ($p in $passes) {
    [System.Environment]::SetEnvironmentVariable('PGPASSWORD', $p)
    $out = & 'C:\Program Files\PostgreSQL\12\bin\psql.exe' -U $u -h 127.0.0.1 -p 5432 -w -c 'SELECT 1;' 2>&1
    if ($LASTEXITCODE -eq 0) {
      Write-Output "FOUND: User=$u Pass=$p"
      exit 0
    }
  }
}
Write-Output "NOT FOUND"
