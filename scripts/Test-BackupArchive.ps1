param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$backupProject = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) { throw 'Set JAVA_HOME or pass -JavaHome with a JDK directory.' }
$backupGradleCache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$backupJunit = Get-ChildItem (Join-Path $backupGradleCache 'caches/modules-2/files-2.1/junit/junit/4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1
$backupHamcrest = Get-ChildItem (Join-Path $backupGradleCache 'caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3') -Recurse -Filter 'hamcrest-core-1.3.jar' | Select-Object -First 1
if (-not $backupJunit -or -not $backupHamcrest) { throw 'Resolve project test dependencies first.' }
$backupOutput = Join-Path $backupProject 'build/backup-unit-tests'
New-Item -ItemType Directory -Path $backupOutput -Force | Out-Null
$backupLibraries = $backupJunit.FullName + ';' + $backupHamcrest.FullName
& (Join-Path $JavaHome 'bin/javac.exe') -encoding UTF-8 --release 8 -cp $backupLibraries -d $backupOutput `
    (Join-Path $backupProject 'app/src/main/java/tool/xfy9326/floatpicture/Methods/BackupArchive.java') `
    (Join-Path $backupProject 'app/src/test/java/tool/xfy9326/floatpicture/Methods/BackupArchiveTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Backup tests did not compile.' }
& (Join-Path $JavaHome 'bin/java.exe') -cp ($backupOutput + ';' + $backupLibraries) org.junit.runner.JUnitCore tool.xfy9326.floatpicture.Methods.BackupArchiveTest
if ($LASTEXITCODE -ne 0) { throw 'Backup tests failed.' }
