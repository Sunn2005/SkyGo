[CmdletBinding()]
param(
    [string]$CloudflaredPath,
    [string]$BackendUrl = 'http://localhost:8080',
    [string]$EnvFile,
    [string]$FrontendEnvFile,
    [string]$StateDirectory,
    [ValidateRange(5, 300)][int]$TunnelTimeoutSeconds = 120,
    [ValidateRange(1, 10)][int]$RegistrationAttempts = 5,
    [switch]$UpdateFrontendEnv,
    [switch]$SkipRegistration
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'payos-demo-functions.ps1')
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $repoRoot 'backend\.env' }
if (-not $FrontendEnvFile) { $FrontendEnvFile = Join-Path $repoRoot 'frontend\.env.local' }
if (-not $StateDirectory) { $StateDirectory = Join-Path $repoRoot 'backend\target\payos-demo' }
$StateDirectory = [IO.Path]::GetFullPath($StateDirectory)
$tunnel = $null
$mutex = $null
$ownsMutex = $false
$state = $null
$sessionFile = $null
$latestFile = Join-Path $StateDirectory 'latest.json'

try {
    $backend = $null
    if (-not [Uri]::TryCreate($BackendUrl, [UriKind]::Absolute, [ref]$backend) -or
        $backend.Scheme -notin @('http', 'https') -or -not $backend.IsLoopback -or
        $backend.AbsolutePath -ne '/' -or $backend.Query -or $backend.Fragment -or $backend.UserInfo) {
        throw 'BackendUrl must be a local HTTP(S) server root, for example http://localhost:8080.'
    }
    $BackendUrl = $backend.AbsoluteUri.TrimEnd('/')
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $hashBytes = [Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($StateDirectory.ToUpperInvariant()))
    $mutexName = 'Local\SkyGoPayOSDemo-' + ([BitConverter]::ToString($hashBytes).Replace('-', '').Substring(0, 16))
    $mutex = [Threading.Mutex]::new($false, $mutexName)
    try { $ownsMutex = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $ownsMutex = $true }
    if (-not $ownsMutex) {
        Write-Host 'A SkyGo demo tunnel is already running. Use its PowerShell window or stop it with Ctrl+C first.' -ForegroundColor Yellow
        Write-Host "Current session details: $latestFile"
        return
    }
    Write-Host 'SkyGo - Cloudflare tunnel and PayOS webhook' -ForegroundColor Cyan
    Write-Host "Checking backend: $BackendUrl/api/v1/home"
    try {
        $health = Invoke-WebRequest -UseBasicParsing -Uri "$BackendUrl/api/v1/home" -TimeoutSec 8 -ErrorAction Stop
        if ($health.StatusCode -ne 200) { throw 'Backend did not return HTTP 200.' }
    } catch { throw "Backend is not ready at $BackendUrl. Start MariaDB and the backend with its environment variables, then run this script again." }

    if (-not $CloudflaredPath) {
        $installed = Get-Command cloudflared.exe -ErrorAction SilentlyContinue
        $downloaded = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Downloads\cloudflared-windows-amd64.exe'
        if ($null -ne $installed) { $CloudflaredPath = $installed.Source }
        elseif (Test-Path -LiteralPath $downloaded -PathType Leaf) { $CloudflaredPath = $downloaded }
        else { throw 'cloudflared was not found. Install it or pass -CloudflaredPath with the full path to the EXE.' }
    }
    $CloudflaredPath = (Resolve-Path -LiteralPath $CloudflaredPath).ProviderPath
    $sessionId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $sessionDir = Join-Path $StateDirectory $sessionId
    [void][IO.Directory]::CreateDirectory($sessionDir)
    $stdout = Join-Path $sessionDir 'cloudflared.stdout.log'
    $stderr = Join-Path $sessionDir 'cloudflared.stderr.log'
    $sessionFile = Join-Path $sessionDir 'session.json'
    Write-Host "Starting cloudflared: $CloudflaredPath"
    $tunnel = Start-Process -FilePath $CloudflaredPath -ArgumentList @('tunnel', '--url', $BackendUrl) `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $state = [ordered]@{startedAt = [DateTime]::UtcNow.ToString('o'); backendUrl = $BackendUrl;
        tunnelUrl = $null; webhookUrl = $null; powerShellPid = $PID; cloudflaredPid = $tunnel.Id;
        registration = 'WAITING_FOR_TUNNEL'; running = $true; logDirectory = $sessionDir}
    function Save-DemoState {
        $json = $state | ConvertTo-Json
        [IO.File]::WriteAllText($sessionFile, $json, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($latestFile, $json, [Text.UTF8Encoding]::new($false))
    }
    Save-DemoState
    $seen = @{}
    function Read-DemoLogs {
        $combined = ''
        foreach ($file in @($stdout, $stderr)) {
            if (Test-Path -LiteralPath $file) {
                $lines = @(Get-Content -LiteralPath $file -Encoding UTF8 -ErrorAction SilentlyContinue)
                $offset = if ($seen.ContainsKey($file)) { [int]$seen[$file] } else { 0 }
                for ($i = $offset; $i -lt $lines.Count; $i++) { Write-Host $lines[$i] -ForegroundColor DarkGray }
                $seen[$file] = $lines.Count
                $combined += ($lines -join "`n") + "`n"
            }
        }
        return $combined
    }
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $ready = $false
    while ($timer.Elapsed.TotalSeconds -lt $TunnelTimeoutSeconds) {
        $logText = Read-DemoLogs
        $url = Get-PayOSDemoTunnelUrl -LogText $logText
        if ($url) { $state.tunnelUrl = $url; $state.webhookUrl = "$url/api/v1/payos/webhook" }
        $tunnel.Refresh()
        if ($tunnel.HasExited) { throw "cloudflared stopped before setup completed. Check the logs in $sessionDir" }
        if ($state.tunnelUrl -and $logText -match 'Registered tunnel connection') { $ready = $true; break }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw "Timed out waiting for a connected Cloudflare tunnel. Check the logs in $sessionDir" }
    $state.registration = 'REGISTERING'
    Save-DemoState
    Write-Host ''
    Write-Host "TUNNEL URL : $($state.tunnelUrl)" -ForegroundColor Green
    Write-Host "WEBHOOK URL: $($state.webhookUrl)" -ForegroundColor Green
    $credentials = Read-PayOSDemoCredentials -EnvFile $EnvFile
    if ($SkipRegistration -or $null -eq $credentials) {
        $state.registration = if ($SkipRegistration) { 'SKIPPED' } else { 'MISSING_CREDENTIALS' }
    } else {
        Write-Host "Reading PayOS credentials from $($credentials.Source) (values are hidden)."
        $registered = Register-PayOSDemoWebhook -WebhookUrl $state.webhookUrl -Credentials $credentials -MaxAttempts $RegistrationAttempts
        $state.registration = if ($registered.Registered) { 'CONFIRMED' } else { 'MANUAL_REQUIRED' }
        $credentials = $null
    }
    Save-DemoState
    if ($state.registration -eq 'CONFIRMED') {
        Write-Host 'PayOS webhook registration succeeded. No Postman registration is needed for this tunnel.' -ForegroundColor Green
    } else { Show-PayOSDemoManualRegistration -WebhookUrl $state.webhookUrl -EnvFile $EnvFile }

    Write-Host ''
    if ($UpdateFrontendEnv) {
        try {
            Set-PayOSDemoFrontendUrl -EnvFile $FrontendEnvFile -TunnelUrl $state.tunnelUrl
            Write-Host "Updated $FrontendEnvFile" -ForegroundColor Green
            Write-Host 'Restart Expo to load the new API URL.'
        } catch { Write-Host "Could not update frontend config. Paste the following line into $FrontendEnvFile" -ForegroundColor Yellow }
    } else { Write-Host "If the frontend uses this tunnel, paste the following line into $FrontendEnvFile and restart Expo:" }
    Write-Host "EXPO_PUBLIC_API_URL=$($state.tunnelUrl)"
    Write-Host "Session details: $latestFile"
    Write-Host 'Keep this window open during the demo. Press Ctrl+C to stop the tunnel.' -ForegroundColor Cyan
    while (-not $tunnel.HasExited) {
        [void](Read-DemoLogs)
        Start-Sleep -Milliseconds 500
        $tunnel.Refresh()
    }
    Write-Host 'The Cloudflare tunnel has stopped. Its public URL is no longer active.' -ForegroundColor Yellow
} catch {
    Write-Host ''
    Write-Host ("Setup failed: " + $_.Exception.Message) -ForegroundColor Red
    if ($null -ne $state) { $state.registration = 'SETUP_FAILED' }
} finally {
    if ($null -ne $tunnel) {
        try { if (-not $tunnel.HasExited) { $tunnel.Kill(); [void]$tunnel.WaitForExit(5000) } } catch { }
    }
    if ($null -ne $state -and $sessionFile) {
        $state.running = $false
        try { Save-DemoState } catch { }
    }
    if ($ownsMutex) { $mutex.ReleaseMutex() }
    if ($null -ne $mutex) { $mutex.Dispose() }
}
