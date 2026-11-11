[CmdletBinding()]
param(
    [switch]$Apply,
    [string]$SourceUrl = 'http://127.0.0.1:6333',
    [ValidatePattern('^[a-zA-Z0-9_-]{1,200}$')][string]$SourceCollection = 'skygo_knowledge_v1_gemini-embedding-001_768',
    [ValidateRange(1,500000)][int]$MaxDocuments = 50000
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$original = @{}
try {
    $dotenv = @{}
    foreach ($file in @((Join-Path $repoRoot '.env'), (Join-Path $backendRoot '.env'))) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
        foreach ($raw in [IO.File]::ReadAllLines($file,[Text.Encoding]::UTF8)) {
            $line = $raw.TrimStart([char]0xFEFF).Trim()
            if ($line -notmatch '^(?:export\s+)?((?:DB_|SKYGO_|AUTH_|PAYOS_)[A-Z0-9_]+|GEMINI_API_KEY)\s*=\s*(.*)$') { continue }
            $name = $Matches[1]; $value = $Matches[2].Trim()
            if ($value.StartsWith('"') -or $value.StartsWith("'")) {
                $end = $value.IndexOf($value[0],1)
                if ($end -lt 1) { throw "Invalid quoted configuration: $name" }
                $tail = $value.Substring($end+1).Trim()
                if ($tail -and -not $tail.StartsWith('#')) { throw "Invalid quoted configuration: $name" }
                $value = $value.Substring(1,$end-1)
            } else { $value = ($value -replace '\s+#.*$','').TrimEnd() }
            $dotenv[$name] = $value
        }
    }
    foreach ($name in $dotenv.Keys) {
        if ($null -ne [Environment]::GetEnvironmentVariable($name,'Process')) { continue }
        $original[$name] = $null
        [Environment]::SetEnvironmentVariable($name,$dotenv[$name],'Process')
    }
    $javaRoot = 'C:\Program Files\Java\jdk-21'
    if (-not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin/javac.exe'))) { $javaRoot = $env:JAVA_HOME }
    if (-not $javaRoot -or -not (Test-Path -LiteralPath (Join-Path $javaRoot 'bin/javac.exe'))) { throw 'Set JAVA_HOME to JDK 21.' }
    $original['JAVA_HOME'] = [Environment]::GetEnvironmentVariable('JAVA_HOME','Process')
    $env:JAVA_HOME = $javaRoot
    Write-Host 'Stop backend/index writers first. Qdrant is read only; existing MariaDB rows are never overwritten.'
    Write-Host ('Mode: ' + $(if ($Apply) { 'APPLY (insert missing rows atomically)' } else { 'DRY RUN (validation only)' }))
    Write-Host 'V12 must already be applied. This command does not run Flyway or call Gemini.'
    Push-Location -LiteralPath $backendRoot
    try {
        & .\mvnw.cmd '-DskipTests' compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath '-Dmdep.outputFile=target/vector-transfer-classpath.txt' '-DincludeScope=runtime'
        if ($LASTEXITCODE -ne 0) { throw 'Transfer build failed; no import was run.' }
        $out = Join-Path $backendRoot 'target/vector-transfer'
        $null = [IO.Directory]::CreateDirectory($out)
        $cp = (Join-Path $backendRoot 'target/classes') + ';' + [IO.File]::ReadAllText((Join-Path $backendRoot 'target/vector-transfer-classpath.txt')).Trim()
        $utf8 = New-Object Text.UTF8Encoding($false)
        $compileArgs = Join-Path $out 'compile.args'
        [IO.File]::WriteAllLines($compileArgs,@('--class-path',('"'+$cp.Replace('\','/')+'"'),'-encoding','UTF-8','-d',('"'+$out.Replace('\','/')+'"'),('"'+(Join-Path $PSScriptRoot 'diagnostics/VectorTransferMain.java').Replace('\','/')+'"')),$utf8)
        & (Join-Path $javaRoot 'bin/javac.exe') "@$compileArgs"
        if ($LASTEXITCODE -ne 0) { throw 'Transfer compilation failed; no import was run.' }
        $mode = if ($Apply) { 'apply' } else { 'dry-run' }
        # JVM argument file avoids the Windows command-line length limit; source settings remain literal arguments.
        $runArgs = Join-Path $out 'run.args'
        [IO.File]::WriteAllLines($runArgs,@('--class-path',('"'+($out+';'+$cp).Replace('\','/')+'"'),'VectorTransferMain'),$utf8)
        & (Join-Path $javaRoot 'bin/java.exe') "@$runArgs" $mode $SourceUrl $SourceCollection ([string]$MaxDocuments)
        if ($LASTEXITCODE -ne 0) { throw 'Vector transfer failed. No success is assumed.' }
    } finally { Pop-Location }
} finally {
    foreach ($name in $original.Keys) {
        if ($null -eq $original[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name,$original[$name],'Process') }
    }
}
