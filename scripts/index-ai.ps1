[CmdletBinding()]
param(
    [ValidateRange(1, 365)][int]$FlightDays = 30,
    [ValidateRange(1, 3650)][int]$BookingDays = 15,
    [ValidateRange(100, 500000)][int]$MaxDocuments = 50000,
    [ValidateRange(1, 64)][int]$BatchSize = 8,
    [ValidateRange(1, 120)][int]$TimeoutSeconds = 90,
    [ValidateRange(0, 60)][int]$BatchDelaySeconds = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$originalValues = @{}
$dotenv = @{}

try {
    # Parse configuration as text; never execute .env contents or print secrets.
    foreach ($file in @((Join-Path $repoRoot '.env'), (Join-Path $backendRoot '.env'))) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
        foreach ($raw in [IO.File]::ReadAllLines($file, [Text.Encoding]::UTF8)) {
            $line = $raw.TrimStart([char]0xFEFF).Trim()
            if ($line -notmatch '^(?:export\s+)?((?:DB_|SKYGO_|AUTH_|PAYOS_)[A-Z0-9_]+|GEMINI_API_KEY)\s*=\s*(.*)$') { continue }
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.StartsWith('"') -or $value.StartsWith("'")) {
                $closing = $value.IndexOf($value[0], 1)
                if ($closing -lt 1) { throw "Invalid quoted configuration value: $name" }
                $tail = $value.Substring($closing + 1).Trim()
                if ($tail -and -not $tail.StartsWith('#')) { throw "Invalid quoted configuration value: $name" }
                $value = $value.Substring(1, $closing - 1)
            } else {
                $value = ($value -replace '\s+#.*$', '').TrimEnd()
            }
            $dotenv[$name] = $value
        }
    }
    foreach ($name in $dotenv.Keys) {
        $old = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -ne $old) { continue }
        $originalValues[$name] = $old
        [Environment]::SetEnvironmentVariable($name, $dotenv[$name], 'Process')
    }
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('GEMINI_API_KEY', 'Process'))) {
        throw 'Add GEMINI_API_KEY to backend/.env or the process environment first.'
    }
    Write-Host 'Indexing existing SkyGo data into MariaDB ai_document. Only changed documents request embeddings.'
    Write-Host 'Use one index writer per database. Stop another backend with automatic AI indexing before this command.'
    Write-Host 'Business tables are read only; Flyway and booking cleanup are disabled for this command.'
    Write-Host "Scope: flights next $FlightDays days; bookings past $BookingDays days. Batch: $BatchSize; pause: $BatchDelaySeconds seconds; timeout: $TimeoutSeconds seconds."
    # CLI properties override the opt-in .env defaults only for this process.
    # A random local port avoids an existing backend on 8080; security beans still use normal servlet startup.
    $arguments = @(
        '--skygo.ai.enabled=true',
        '--skygo.ai.index.enabled=true', '--skygo.ai.index.run-once=true',
        "--skygo.ai.index.flight-days=$FlightDays", "--skygo.ai.index.booking-days=$BookingDays",
        "--skygo.ai.index.batch-size=$BatchSize", "--skygo.ai.timeout=${TimeoutSeconds}s",
        "--skygo.ai.index.batch-delay=${BatchDelaySeconds}s",
        "--skygo.ai.index.max-documents=$MaxDocuments", '--server.port=0', '--server.address=127.0.0.1',
        '--spring.flyway.enabled=false', '--skygo.seat-hold.cleanup-enabled=false'
    ) -join ' '
    Push-Location -LiteralPath $backendRoot
    try {
        & .\mvnw.cmd 'spring-boot:run' "-Dspring-boot.run.arguments=$arguments"
        if ($LASTEXITCODE -ne 0) { throw 'AI indexing failed. Check the backend error above; no success is assumed.' }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($name in $originalValues.Keys) {
        if ($null -eq $originalValues[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name, $originalValues[$name], 'Process') }
    }
}
