param([int]$Port = 8181)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port ist belegt. Mit -Port einen freien Port waehlen."
}
$build = & mvn '-Dmaven.test.skip=false' -X test-compile 2>&1
if ($LASTEXITCODE -ne 0) {
    $build | Select-Object -Last 30 | Write-Output
    throw 'Maven-Kompilierung fehlgeschlagen.'
}
$line = $build | Select-String 'testPath = ' | Select-Object -Last 1
if (-not $line) { throw 'Maven-Test-Classpath konnte nicht ermittelt werden.' }
$resolved = $line.ToString()
$entries = $resolved.Substring($resolved.IndexOf('[', $resolved.IndexOf('testPath =')) + 1).TrimEnd(']').Split(',') | ForEach-Object { $_.Trim() }
$classpath = $entries -join ';'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
Write-Output "Lokale H2-Vorschau: http://127.0.0.1:$Port/manufacturing-planning"
& $java -cp $classpath com.peb.service.planning.PlanningPreviewApplication "--server.port=$Port"