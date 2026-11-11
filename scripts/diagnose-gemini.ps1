[CmdletBinding()]
param(
    [ValidatePattern('^gemini-[A-Za-z0-9._-]+$')][string]$Model,
    [ValidateRange(5, 120)][int]$TimeoutSeconds = 60,
    [ValidateSet('Rest', 'Java')][string]$Mode = 'Rest',
    [switch]$CompileOnly,
    [switch]$CheckOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$values = @{}
$sources = @{}

# Read configuration as data. Never execute dotenv contents or print the API key.
foreach ($file in @((Join-Path $repoRoot '.env'), (Join-Path $repoRoot 'backend/.env'))) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
    foreach ($raw in [IO.File]::ReadAllLines($file, [Text.Encoding]::UTF8)) {
        $line = $raw.TrimStart([char]0xFEFF).Trim()
        if ($line -notmatch '^(?:export\s+)?(GEMINI_API_KEY|SKYGO_AI_CHAT_MODEL)\s*=\s*(.*)$') { continue }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($value.StartsWith('"') -or $value.StartsWith("'")) {
            $closing = $value.IndexOf($value[0], 1)
            if ($closing -lt 1) { throw "Invalid quoted configuration: $name" }
            $tail = $value.Substring($closing + 1).Trim()
            if ($tail -and -not $tail.StartsWith('#')) { throw "Invalid quoted configuration: $name" }
            $value = $value.Substring(1, $closing - 1)
        } else { $value = ($value -replace '\s+#.*$', '').TrimEnd() }
        $values[$name] = $value
        $sources[$name] = if ($file -eq (Join-Path $repoRoot 'backend/.env')) { 'backend/.env' } else { 'root .env' }
    }
}
foreach ($name in @('GEMINI_API_KEY', 'SKYGO_AI_CHAT_MODEL')) {
    $processValue = [Environment]::GetEnvironmentVariable($name, 'Process')
    if (-not [string]::IsNullOrWhiteSpace($processValue)) {
        $values[$name] = $processValue.Trim()
        $sources[$name] = 'process environment'
    }
}
if (-not $values.ContainsKey('GEMINI_API_KEY') -or [string]::IsNullOrWhiteSpace($values['GEMINI_API_KEY'])) {
    throw 'GEMINI_API_KEY is missing. Add it to backend/.env or the process environment.'
}
$apiKey = [string]$values['GEMINI_API_KEY']
if (-not $Model) {
    $Model = if ($values.ContainsKey('SKYGO_AI_CHAT_MODEL')) { $values['SKYGO_AI_CHAT_MODEL'] } else { 'gemini-3.8-flash' }
}
if ($Model -notmatch '^gemini-[A-Za-z0-9._-]+$') { throw 'Invalid model identifier.' }

function Protect-Output([string]$Text) {
    if ($null -eq $Text) { return '' }
    $safe = $Text.Replace($apiKey, '[REDACTED]')
    $safe = $safe -replace 'AIza[A-Za-z0-9_-]+', '[REDACTED]'
    $safe = $safe -replace 'https?://[^\s"<>]+', '[URL]'
    $safe = $safe -replace '[\r\n]+', ' '
    return $safe.Substring(0, [Math]::Min($safe.Length, 1200))
}

Write-Host "Model: $Model"
Write-Host "API key source: $($sources['GEMINI_API_KEY']) (value hidden)"
if ($CheckOnly) {
    Write-Host 'CONFIG_OK. No network request was made.'
    return
}
if ($CompileOnly -and $Mode -ne 'Java') { throw '-CompileOnly requires -Mode Java.' }

if ($Mode -eq 'Java') {
    $backendRoot = Join-Path $repoRoot 'backend'
    $classpathFile = Join-Path $backendRoot 'target/gemini-mariadb-classpath.txt'
    $classes = Join-Path $backendRoot 'target/classes'
    if (-not (Test-Path -LiteralPath (Join-Path $classes 'com/skygo/backend/ai/chat/AiChatService.class'))) {
        throw 'Compile the backend first using mvnw.cmd -DskipTests compile, then run this probe again.'
    }
    if (-not (Test-Path -LiteralPath $classpathFile)) {
        Push-Location -LiteralPath $backendRoot
        try {
            & .\mvnw.cmd -q -o org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath '-Dmdep.outputFile=target/gemini-mariadb-classpath.txt' '-DincludeScope=test'
            if ($LASTEXITCODE -ne 0) { throw 'Could not resolve the existing backend classpath.' }
        } finally { Pop-Location }
    }
    # Match the JDK 21 used by BackendApplication in this workspace.
    $javaRoot = 'C:\Program Files\Java\jdk-21'
    if (-not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin/javac.exe'))) {
        $javaRoot = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Process')
    }
    if (-not $javaRoot) { throw 'Set JAVA_HOME to a JDK 21 or newer.' }
    $javac = Join-Path $javaRoot 'bin/javac.exe'
    $java = Join-Path $javaRoot 'bin/java.exe'
    if (-not (Test-Path -LiteralPath $javac)) { throw 'Set JAVA_HOME to a JDK 21 or newer.' }
    $probeDir = Join-Path $backendRoot 'target/gemini-request-probe'
    $null = [IO.Directory]::CreateDirectory($probeDir)
    $classpath = $classes + ';' + [IO.File]::ReadAllText($classpathFile).Trim()
    $source = Join-Path $PSScriptRoot 'diagnostics/GeminiRequestProbe.java'
    $compileArgs = Join-Path $probeDir 'compile.args'
    $runArgs = Join-Path $probeDir 'run.args'
    $utf8 = New-Object Text.UTF8Encoding($false)
    [IO.File]::WriteAllLines($compileArgs, @('--class-path', ('"' + $classpath.Replace('\', '/') + '"'),
        '-encoding', 'UTF-8', '-d', ('"' + $probeDir.Replace('\', '/') + '"'), ('"' + $source.Replace('\', '/') + '"')), $utf8)
    & $javac "@$compileArgs"
    if ($LASTEXITCODE -ne 0) { throw 'Java diagnostic compilation failed.' }
    if ($CompileOnly) {
        Write-Host 'JAVA_COMPILE_OK. No network request was made.'
        return
    }
    $runClasspath = $probeDir + ';' + $classpath
    [IO.File]::WriteAllLines($runArgs, @('--class-path', ('"' + $runClasspath.Replace('\', '/') + '"'), 'GeminiRequestProbe', $Model), $utf8)
    Push-Location -LiteralPath $backendRoot
    try {
        & $java "@$runArgs"
        if ($LASTEXITCODE -ne 0) { throw 'Java diagnostic process failed.' }
    } finally { Pop-Location }
    return
}

Write-Host 'Sending ONE short generateContent request directly to Google. Uses chat quota; no retries.'
Write-Host 'No Spring AI tools, database, embedding or indexing are involved.'
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
$response = $null
$request = $null
try {
    $uri = "https://generativelanguage.googleapis.com/v1beta/models/${Model}:generateContent"
    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, $uri)
    $null = $request.Headers.TryAddWithoutValidation('x-goog-api-key', $apiKey)
    $payload = @{
        contents = @(@{ role = 'user'; parts = @(@{ text = 'Reply with exactly OK.' }) })
        generationConfig = @{ maxOutputTokens = 256 }
    } | ConvertTo-Json -Depth 8 -Compress
    $request.Content = New-Object System.Net.Http.StringContent($payload, [Text.Encoding]::UTF8, 'application/json')
    $response = $client.SendAsync($request).GetAwaiter().GetResult()
    $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    Write-Host "HTTP $([int]$response.StatusCode)"
    $json = $null
    try { $json = $body | ConvertFrom-Json } catch { }
    if ($response.IsSuccessStatusCode) {
        if ($null -eq $json -or -not ($json.PSObject.Properties['candidates'] -or $json.PSObject.Properties['promptFeedback'])) {
            Write-Host 'UNEXPECTED_SUCCESS_RESPONSE: HTTP succeeded but the body is not a GenerateContent response.'
            return
        }
        Write-Host 'DIRECT_GENERATION_OK: Google accepted the real API key and a minimal generation request.'
        Write-Host 'Next investigate the application request, SDK or IDE environment; do not re-index.'
    } elseif ($null -ne $json -and $json.PSObject.Properties['error']) {
        $errorBody = $json.error
        if ($errorBody.PSObject.Properties['status']) { Write-Host "Google status: $(Protect-Output ([string]$errorBody.status))" }
        if ($errorBody.PSObject.Properties['message']) { Write-Host "Google message: $(Protect-Output ([string]$errorBody.message))" }
        Write-Host 'DIRECT_GENERATION_FAILED: the failure also occurs without the SkyGo application request.'
    } else {
        Write-Host "Nonstandard response: $(Protect-Output $body)"
    }
} catch {
    Write-Host 'TRANSPORT_ERROR: no complete HTTP response was received by this PowerShell process.'
    $failure = $_.Exception
    for ($depth = 0; $null -ne $failure -and $depth -lt 6; $depth++) {
        Write-Host "$($failure.GetType().FullName): $(Protect-Output $failure.Message)"
        $failure = $failure.InnerException
    }
} finally {
    if ($null -ne $response) { $response.Dispose() }
    if ($null -ne $request) { $request.Dispose() }
    $client.Dispose()
}
