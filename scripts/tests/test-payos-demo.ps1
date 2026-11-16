Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$scriptsRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $scriptsRoot
. (Join-Path $scriptsRoot 'payos-demo-functions.ps1')
$entry = Join-Path $scriptsRoot 'start-payos-demo.ps1'
$testRoot = Join-Path $repoRoot ('backend\target\payos-demo-script-tests\' + [Guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($testRoot)
$utf8 = [Text.UTF8Encoding]::new($false)
$script:passed = 0
function Assert-DemoTest {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "FAIL: $Message" }
}
function Pass-DemoTest {
    param([string]$Name)
    $script:passed++
    Write-Host "PASS: $Name" -ForegroundColor Green
}

# Only dummy credentials are used. The HTTP cmdlets are mocked in this process.
$client = 'demo-client-for-script-tests'
$apiKey = 'dummy-api-key-not-for-real-payos'
$envFile = Join-Path $testRoot 'fixture.env'
[IO.File]::WriteAllText($envFile, "# fixture`r`nexport PAYOS_CLIENT_ID = `"$client`"`r`nPAYOS_API_KEY='$apiKey'`r`n", $utf8)
$credentials = Read-PayOSDemoCredentials -EnvFile $envFile
Assert-DemoTest ($credentials.ClientId -eq $client -and $credentials.ApiKey -eq $apiKey) 'Read quoted dotenv values without executing the file.'
Pass-DemoTest 'Read dotenv credentials (quotes, whitespace and export)'

$url = Get-PayOSDemoTunnelUrl -LogText "INF | https://radio-mozilla-covering-human.trycloudflare.com |`r`nINF Registered tunnel connection"
Assert-DemoTest ($url -eq 'https://radio-mozilla-covering-human.trycloudflare.com') 'Extract the URL from the actual Cloudflare log format.'
Assert-DemoTest ($null -eq (Get-PayOSDemoTunnelUrl -LogText 'https://fake.trycloudflare.com.attacker.example')) 'Reject lookalike URL hosts.'
Pass-DemoTest 'Extract only a real trycloudflare hostname'

$frontendEnv = Join-Path $testRoot 'frontend.env.local'
[IO.File]::WriteAllText($frontendEnv, "# keep`r`nEXPO_PUBLIC_OTHER=preserve-me`r`nEXPO_PUBLIC_API_URL=http://old`r`nEXPO_PUBLIC_API_URL=http://duplicate`r`n", $utf8)
Set-PayOSDemoFrontendUrl -EnvFile $frontendEnv -TunnelUrl $url
$frontendText = [IO.File]::ReadAllText($frontendEnv)
Assert-DemoTest ($frontendText.Contains('# keep') -and $frontendText.Contains('EXPO_PUBLIC_OTHER=preserve-me')) 'Preserve other frontend configuration.'
Assert-DemoTest (([regex]::Matches($frontendText, '(?m)^EXPO_PUBLIC_API_URL=')).Count -eq 1) 'Do not leave duplicate API URL settings.'
Assert-DemoTest ($frontendText.Contains("EXPO_PUBLIC_API_URL=$url")) 'Write the base URL without /api/v1.'
Pass-DemoTest 'Update frontend URL without replacing unrelated settings'

$global:payosDemoTestRequests = [Collections.Generic.List[object]]::new()
$global:payosDemoTestMode = 'success'
$global:payosDemoTestBackendReady = $true
function Invoke-WebRequest {
    param([switch]$UseBasicParsing, $Uri, $TimeoutSec, $ErrorAction)
    if (-not $global:payosDemoTestBackendReady) { throw 'Simulated unavailable backend' }
    return [pscustomobject]@{StatusCode = 200}
}
function Invoke-RestMethod {
    param($Method, $Uri, $Headers, $ContentType, $Body, $TimeoutSec, $ErrorAction)
    $global:payosDemoTestRequests.Add([pscustomobject]@{Method = $Method; Uri = $Uri; Headers = $Headers; ContentType = $ContentType; Body = $Body})
    if ($global:payosDemoTestMode -eq 'failure' -or ($global:payosDemoTestMode -eq 'retry' -and $global:payosDemoTestRequests.Count -lt 3)) {
        throw [Net.WebException]::new("Simulated response that must not leak $apiKey")
    }
    $webhook = ($Body | ConvertFrom-Json).webhookUrl
    if ($global:payosDemoTestMode -eq 'mismatch') { $webhook = 'https://wrong.trycloudflare.com/api/v1/payos/webhook' }
    return [pscustomobject]@{code = '00'; data = [pscustomobject]@{webhookUrl = $webhook}}
}
$registration = Register-PayOSDemoWebhook -WebhookUrl "$url/api/v1/payos/webhook" -Credentials $credentials -MaxAttempts 1
Assert-DemoTest $registration.Registered 'Confirm the requested webhook URL.'
$request = $global:payosDemoTestRequests[0]
Assert-DemoTest ($request.Uri -eq 'https://api-merchant.payos.vn/confirm-webhook' -and $request.Method -eq 'Post') 'Use the official confirmation endpoint.'
Assert-DemoTest ($request.Headers['x-client-id'] -eq $client -and $request.Headers['x-api-key'] -eq $apiKey) 'Authenticate the matching PayOS channel.'
Assert-DemoTest (($request.Body | ConvertFrom-Json).webhookUrl -eq "$url/api/v1/payos/webhook") 'Send the complete backend webhook path.'
Pass-DemoTest 'Register the correct URL and PayOS channel'

$global:payosDemoTestRequests.Clear(); $global:payosDemoTestMode = 'retry'
$registration = Register-PayOSDemoWebhook -WebhookUrl "$url/api/v1/payos/webhook" -Credentials $credentials -MaxAttempts 3 -RetryDelaySeconds 0
Assert-DemoTest ($registration.Registered -and $registration.Attempts -eq 3) 'Recover from temporary network failures.'
Pass-DemoTest 'Retry transient registration failures'

$global:payosDemoTestRequests.Clear(); $global:payosDemoTestMode = 'mismatch'
$registration = Register-PayOSDemoWebhook -WebhookUrl "$url/api/v1/payos/webhook" -Credentials $credentials -MaxAttempts 1
Assert-DemoTest (-not $registration.Registered) 'Do not claim success for another webhook URL.'
Pass-DemoTest 'Reject a confirmation for the wrong URL'

$fakeDirectory = Join-Path $testRoot 'fake cloudflared with spaces'
[void][IO.Directory]::CreateDirectory($fakeDirectory)
$fakeExe = Join-Path $fakeDirectory 'cloudflared.exe'
$source = @'
using System;
using System.IO;
using System.Threading;
public class FakeCloudflared {
    public static void Main(string[] args) {
        File.WriteAllLines(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "arguments.txt"), args);
        Console.Error.WriteLine("INF | https://script-demo.trycloudflare.com |");
        Console.Error.WriteLine("INF Registered tunnel connection connIndex=0");
        Thread.Sleep(2500);
    }
}
'@
Add-Type -TypeDefinition $source -OutputAssembly $fakeExe -OutputType ConsoleApplication
$global:payosDemoTestRequests.Clear(); $global:payosDemoTestMode = 'success'
$successDir = Join-Path $testRoot 'successful session'
$output = (& $entry -CloudflaredPath $fakeExe -EnvFile $envFile -FrontendEnvFile $frontendEnv `
    -StateDirectory $successDir -TunnelTimeoutSeconds 5 -RegistrationAttempts 1 -UpdateFrontendEnv 6>&1 | Out-String)
if (-not (Test-Path -LiteralPath (Join-Path $successDir 'latest.json'))) { Write-Host $output }
$state = Get-Content -LiteralPath (Join-Path $successDir 'latest.json') -Raw | ConvertFrom-Json
Assert-DemoTest ($state.registration -eq 'CONFIRMED' -and -not $state.running) 'Record successful setup and shutdown.'
Assert-DemoTest ($state.tunnelUrl -eq 'https://script-demo.trycloudflare.com') 'Persist the public URL.'
Assert-DemoTest ([IO.File]::ReadAllText($frontendEnv).Contains('EXPO_PUBLIC_API_URL=https://script-demo.trycloudflare.com')) 'Apply the optional frontend update.'
Assert-DemoTest (-not $output.Contains($client) -and -not $output.Contains($apiKey)) 'Never print credential values.'
$arguments = [IO.File]::ReadAllLines((Join-Path $fakeDirectory 'arguments.txt'))
Assert-DemoTest (($arguments -join '|') -eq 'tunnel|--url|http://localhost:8080') 'Start cloudflared without leaking keys in process arguments.'
Pass-DemoTest 'Full setup with a fake executable, optional frontend update and clean shutdown'

$global:payosDemoTestRequests.Clear(); $global:payosDemoTestMode = 'failure'
$failureDir = Join-Path $testRoot 'failed registration'
$output = (& $entry -CloudflaredPath $fakeExe -EnvFile $envFile -StateDirectory $failureDir `
    -TunnelTimeoutSeconds 5 -RegistrationAttempts 1 6>&1 | Out-String)
$state = Get-Content -LiteralPath (Join-Path $failureDir 'latest.json') -Raw | ConvertFrom-Json
Assert-DemoTest ($state.registration -eq 'MANUAL_REQUIRED') 'Keep a manual registration fallback.'
Assert-DemoTest ($output.Contains('Manual registration in Postman:') -and $output.Contains('https://script-demo.trycloudflare.com/api/v1/payos/webhook')) 'Print the exact URL and manual steps.'
Assert-DemoTest (-not $output.Contains($apiKey)) 'Do not leak secrets from an HTTP exception.'
Pass-DemoTest 'Failed registration shows manual instructions without exposing secrets'

$noConnectionExe = Join-Path $fakeDirectory 'cloudflared-no-connection.exe'
$noConnectionSource = $source.Replace('FakeCloudflared', 'FakeCloudflaredNoConnection').Replace('Registered tunnel connection connIndex=0', 'Still waiting for a connection').Replace('Thread.Sleep(2500)', 'Thread.Sleep(15000)')
Add-Type -TypeDefinition $noConnectionSource -OutputAssembly $noConnectionExe -OutputType ConsoleApplication
$timeoutDir = Join-Path $testRoot 'timeout session'
$output = (& $entry -CloudflaredPath $noConnectionExe -EnvFile $envFile -StateDirectory $timeoutDir `
    -TunnelTimeoutSeconds 5 -RegistrationAttempts 1 6>&1 | Out-String)
$state = Get-Content -LiteralPath (Join-Path $timeoutDir 'latest.json') -Raw | ConvertFrom-Json
Assert-DemoTest ($state.registration -eq 'SETUP_FAILED' -and -not $state.running) 'Fail if no connected tunnel is reported.'
Assert-DemoTest ($null -eq (Get-Process -Id $state.cloudflaredPid -ErrorAction SilentlyContinue)) 'Stop only the child process started by this script.'
Pass-DemoTest 'Tunnel startup timeout cleans up its child process'

$global:payosDemoTestBackendReady = $false
$unavailableDir = Join-Path $testRoot 'unavailable backend'
$output = (& $entry -CloudflaredPath $fakeExe -EnvFile $envFile -StateDirectory $unavailableDir 6>&1 | Out-String)
Assert-DemoTest (-not (Test-Path -LiteralPath (Join-Path $unavailableDir 'latest.json'))) 'Do not expose a backend that is not ready.'
Assert-DemoTest ($output.Contains('Backend is not ready')) 'Explain how to start the backend first.'
Pass-DemoTest 'Unavailable backend stops before starting a tunnel'

Write-Host "All $script:passed script tests passed. HTTP calls were mocked; no PayOS account was changed." -ForegroundColor Green
Write-Host "Test artifacts: $testRoot"
