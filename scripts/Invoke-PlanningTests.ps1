$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
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
& $java -cp $classpath scripts/PlanningTestRunner.java com.peb.service.planning.PlanningExcelServiceTest com.peb.service.planning.PlanningRulesTest com.peb.service.planning.ProcessPlanningRulesTest com.peb.service.planning.PlanningValidationServiceTest com.peb.service.planning.PlanningProjectServiceTest
if ($LASTEXITCODE -ne 0) { throw 'Fertigungsplanungstests fehlgeschlagen.' }