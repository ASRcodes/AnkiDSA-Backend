param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$jdkCandidates = @("$env:USERPROFILE/.jdks/ms-21.0.9", "$env:ProgramFiles/Android/Android Studio/jbr")
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) {
    foreach ($jdkPath in $jdkCandidates) {
        if (Test-Path "$jdkPath/bin/javac.exe") { $env:JAVA_HOME = $jdkPath; break }
    }
}
$maven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($maven) { & $maven.Source @MavenArgs; exit $LASTEXITCODE }
$mavenPath = Get-ChildItem "$env:ProgramFiles/JetBrains" -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
if (-not $mavenPath) { throw 'Install Maven 3.9+ and Java 21, then rerun this script.' }
$ErrorActionPreference = 'Continue'
& $mavenPath @MavenArgs
exit $LASTEXITCODE
