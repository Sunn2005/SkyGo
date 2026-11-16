Set-StrictMode -Version Latest

function Read-PayOSDemoCredentials {
    param([Parameter(Mandatory = $true)][string]$EnvFile)
    $values = @{}
    if (Test-Path -LiteralPath $EnvFile -PathType Leaf) {
        foreach ($line in [IO.File]::ReadAllLines($EnvFile, [Text.Encoding]::UTF8)) {
            if ($line -match '^\s*(?:export\s+)?(PAYOS_CLIENT_ID|PAYOS_API_KEY)\s*=\s*(.*)$') {
                $name = $Matches[1]
                $value = $Matches[2].Trim()
                if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                        ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                    $value = $value.Substring(1, $value.Length - 2)
                } else {
                    $value = ($value -replace '\s+#.*$', '').Trim()
                }
                $values[$name] = $value
            }
        }
    }
    if ($values.ContainsKey('PAYOS_CLIENT_ID') -and $values.ContainsKey('PAYOS_API_KEY') -and
        -not [string]::IsNullOrWhiteSpace($values['PAYOS_CLIENT_ID']) -and
        -not [string]::IsNullOrWhiteSpace($values['PAYOS_API_KEY'])) {
        return [pscustomobject]@{ClientId = $values['PAYOS_CLIENT_ID']; ApiKey = $values['PAYOS_API_KEY']; Source = $EnvFile}
    }
    $client = [Environment]::GetEnvironmentVariable('PAYOS_CLIENT_ID', 'Process')
    $key = [Environment]::GetEnvironmentVariable('PAYOS_API_KEY', 'Process')
    if (-not [string]::IsNullOrWhiteSpace($client) -and -not [string]::IsNullOrWhiteSpace($key)) {
        return [pscustomobject]@{ClientId = $client; ApiKey = $key; Source = 'process environment'}
    }
    return $null
}

function Get-PayOSDemoTunnelUrl {
    param([AllowEmptyString()][string]$LogText)
    $found = [regex]::Match($LogText, 'https://[a-z0-9-]+\.trycloudflare\.com(?=[/\s|]|$)', 'IgnoreCase')
    if ($found.Success) { return $found.Value.ToLowerInvariant() }
    return $null
}

function Register-PayOSDemoWebhook {
    param(
        [Parameter(Mandatory = $true)][string]$WebhookUrl,
        [Parameter(Mandatory = $true)]$Credentials,
        [ValidateRange(1, 10)][int]$MaxAttempts = 5,
        [ValidateRange(0, 10)][int]$RetryDelaySeconds = 2
    )
    if ($WebhookUrl -notmatch '^https://[a-z0-9-]+\.trycloudflare\.com/api/v1/payos/webhook$') {
        throw 'Unexpected webhook URL. A Cloudflare HTTPS URL with the SkyGo webhook path is required.'
    }
    $headers = @{'x-client-id' = $Credentials.ClientId; 'x-api-key' = $Credentials.ApiKey}
    $body = @{webhookUrl = $WebhookUrl} | ConvertTo-Json -Compress
    $detail = 'Unknown response'
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $httpStatus = 0
        Write-Host "Registering webhook with PayOS ($attempt/$MaxAttempts)..."
        try {
            $result = Invoke-RestMethod -Method Post -Uri 'https://api-merchant.payos.vn/confirm-webhook' `
                -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 20 -ErrorAction Stop
            $codeProperty = $result.PSObject.Properties['code']
            $dataProperty = $result.PSObject.Properties['data']
            $confirmed = $null
            if ($null -ne $dataProperty -and $null -ne $result.data) {
                $urlProperty = $result.data.PSObject.Properties['webhookUrl']
                if ($null -ne $urlProperty) { $confirmed = [string]$result.data.webhookUrl }
            }
            if ($null -ne $codeProperty -and [string]$result.code -eq '00' -and $confirmed -eq $WebhookUrl) {
                return [pscustomobject]@{Registered = $true; Detail = 'Confirmed by PayOS'; Attempts = $attempt}
            }
            # Do not print provider response bodies, credentials, or account information.
            $detail = 'PayOS did not confirm the requested webhook URL.'
        } catch {
            $responseProperty = $_.Exception.PSObject.Properties['Response']
            if ($null -ne $responseProperty -and $null -ne $responseProperty.Value) {
                $statusProperty = $responseProperty.Value.PSObject.Properties['StatusCode']
                if ($null -ne $statusProperty) { $httpStatus = [int]$statusProperty.Value }
            }
            $detail = if ($httpStatus -gt 0) { "PayOS returned HTTP $httpStatus." } else { 'Could not confirm registration (network, timeout, or unexpected response).' }
        }
        Write-Host $detail -ForegroundColor Yellow
        if ($httpStatus -in @(401, 403) -or $attempt -eq $MaxAttempts) { break }
        Start-Sleep -Seconds ($RetryDelaySeconds * $attempt)
    }
    return [pscustomobject]@{Registered = $false; Detail = $detail; Attempts = $attempt}
}

function Set-PayOSDemoFrontendUrl {
    param([Parameter(Mandatory = $true)][string]$EnvFile, [Parameter(Mandatory = $true)][string]$TunnelUrl)
    $lines = [Collections.Generic.List[string]]::new()
    $replaced = $false
    if (Test-Path -LiteralPath $EnvFile) {
        foreach ($line in [IO.File]::ReadAllLines($EnvFile, [Text.Encoding]::UTF8)) {
            if ($line -match '^\s*(?:export\s+)?EXPO_PUBLIC_API_URL\s*=') {
                if (-not $replaced) { $lines.Add("EXPO_PUBLIC_API_URL=$TunnelUrl"); $replaced = $true }
            } else { $lines.Add($line) }
        }
    }
    if (-not $replaced) { $lines.Add("EXPO_PUBLIC_API_URL=$TunnelUrl") }
    [IO.File]::WriteAllLines($EnvFile, $lines, [Text.UTF8Encoding]::new($false))
}

function Show-PayOSDemoManualRegistration {
    param([string]$WebhookUrl, [string]$EnvFile)
    Write-Host ''
    Write-Host 'Manual registration in Postman:' -ForegroundColor Yellow
    Write-Host 'POST https://api-merchant.payos.vn/confirm-webhook'
    Write-Host 'Authorization: No Auth'
    Write-Host 'Headers: Content-Type = application/json'
    Write-Host "         x-client-id = PAYOS_CLIENT_ID from $EnvFile"
    Write-Host "         x-api-key   = PAYOS_API_KEY from $EnvFile"
    Write-Host 'Body -> raw -> JSON:'
    Write-Host (@{webhookUrl = $WebhookUrl} | ConvertTo-Json)
    Write-Host 'Keep this tunnel and the backend running, then press Send.'
    Write-Host 'If verification fails, check backend PAYOS_CHECKSUM_KEY and the keys belonging to the same PayOS channel.'
}
