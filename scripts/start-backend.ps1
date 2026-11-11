[CmdletBinding()]
param(
    [ValidateRange(1, 65535)][int]$Port = 8080,
    [switch]$DisableAi,
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$dotenv = @{}
$originalValues = @{}

try {
    # Match the IDE's dotenv setup without executing .env or printing secrets.
    foreach ($file in @((Join-Path $repoRoot '.env'), (Join-Path $backendRoot '.env'))) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
        foreach ($raw in [IO.File]::ReadAllLines($file, [Text.Encoding]::UTF8)) {
            $line = $raw.TrimStart([char]0xFEFF).Trim()
            if ($line -notmatch '^(?:export\s+)?((?:DB_|SKYGO_|AUTH_|PAYOS_)[A-Z0-9_]+|GEMINI_API_KEY)\s*=\s*(.*)$') { continue }
            $name = $Matches[1]
            $value = $Matches[2].Trim()
            if ($value.StartsWith('"') -or $value.StartsWith("'")) {
                $closing = $value.IndexOf($value[0], 1)
                if ($closing -lt 1) { throw "Invalid quoted configuration: $name" }
                $tail = $value.Substring($closing + 1).Trim()
                if ($tail -and -not $tail.StartsWith('#')) { throw "Invalid quoted configuration: $name" }
                $value = $value.Substring(1, $closing - 1)
            } else { $value = ($value -replace '\s+#.*$', '').TrimEnd() }
            $dotenv[$name] = $value
        }
    }
    foreach ($name in $dotenv.Keys) {
        $old = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($null -ne $old) { continue }
        $originalValues[$name] = $old
        [Environment]::SetEnvironmentVariable($name, $dotenv[$name], 'Process')
    }
    $javaRoot = 'C:\Program Files\Java\jdk-21'
    if (-not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin/java.exe'))) {
        $javaRoot = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    }
    if (-not $javaRoot -or -not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin/java.exe'))) {
        throw 'Install JDK 21 and set JAVA_HOME.'
    }
    $originalValues['JAVA_HOME'] = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $javaRoot, 'Process')
    if (-not $DisableAi -and [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('GEMINI_API_KEY', 'Process'))) {
        throw 'GEMINI_API_KEY is missing in the environment or backend/.env.'
    }
    Write-Host "JDK: $javaRoot"
    Write-Host "Backend port: $Port"
    Write-Host ('AI enabled: ' + (-not $DisableAi))
    Write-Host 'AI indexing is disabled for this run. The dotenv files are not modified.'
    Write-Host 'Application log: backend/target/backend-powershell.log'
    if ($CheckOnly) {
        Write-Host 'CONFIG_OK. Backend was not started; no network request was made.'
        return
    }
    $socket = New-Object Net.Sockets.TcpClient
    try {
        $pending = $socket.BeginConnect('127.0.0.1', $Port, $null, $null)
        if ($pending.AsyncWaitHandle.WaitOne(1000) -and $socket.Connected) {
            throw "Port $Port is already in use. Stop BackendApplication in IntelliJ first."
        }
    } finally { $socket.Close() }
    $arguments = @(
        "--server.port=$Port", ('--skygo.ai.enabled=' + (-not $DisableAi).ToString().ToLowerInvariant()),
        '--skygo.ai.index.enabled=false', '--skygo.ai.index.run-once=false',
        '--logging.file.name=target/backend-powershell.log'
    ) -join ' '
    Push-Location -LiteralPath $backendRoot
    try {
        & .\mvnw.cmd '-DskipTests' spring-boot:run "-Dspring-boot.run.arguments=$arguments"
        if ($LASTEXITCODE -ne 0) { throw 'Backend stopped with an error. See backend/target/backend-powershell.log.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $originalValues.Keys) {
        if ($null -eq $originalValues[$name]) {
            Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
        } else {
            [Environment]::SetEnvironmentVariable($name, $originalValues[$name], 'Process')
        }
    }
}
