param([string]$CacheRoot = (Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'), [switch]$AndroidSourcesOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Find-CachedJar([string]$RelativePath) {
    $jar = Get-ChildItem -LiteralPath (Join-Path $CacheRoot $RelativePath) -Filter '*.jar' -Recurse | Select-Object -First 1
    if ($null -eq $jar) { throw "No cached jar in $RelativePath" }
    return $jar.FullName
}
$stdlib = Find-CachedJar 'org.jetbrains.kotlin\kotlin-stdlib\2.0.21'
$junit = Find-CachedJar 'junit\junit\4.13.2'
$hamcrest = Find-CachedJar 'org.hamcrest\hamcrest-core\1.3'
$annotations = Find-CachedJar 'org.jetbrains\annotations\13.0'
$compilerClasspath = (@(
    (Find-CachedJar 'org.jetbrains.kotlin\kotlin-compiler-embeddable\2.0.21'),
    $stdlib,
    (Find-CachedJar 'org.jetbrains.kotlin\kotlin-script-runtime\2.0.21'),
    (Find-CachedJar 'org.jetbrains.kotlin\kotlin-reflect\2.0.21'),
    (Find-CachedJar 'org.jetbrains.intellij.deps\trove4j\1.0.20200330'),
    (Find-CachedJar 'org.jetbrains.kotlinx\kotlinx-coroutines-core-jvm\1.6.4'),
    $annotations
) -join ';')
$runtimeClasspath = (@($stdlib, $junit, $hamcrest, $annotations) -join ';')
$outputDir = Join-Path $projectRoot 'build\pairing-audit'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
if ($AndroidSourcesOnly) {
    $androidJar = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk\platforms\android-36\android.jar'
    if (!(Test-Path -LiteralPath $androidJar)) { throw 'Android SDK 36 is required for source compilation' }
    $transforms = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.gradle\caches\8.13\transforms') -Recurse -Filter '*.jar'
    $modules = Get-ChildItem -LiteralPath $CacheRoot -Recurse -Filter '*.jar' | Where-Object { $_.FullName -notmatch '\\org.jetbrains.kotlin\\' }
    $rJar = Join-Path $projectRoot 'app\build\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug\processDebugResources\R.jar'
    $androidClasspath = (@($stdlib, $androidJar, $rJar, $annotations) + @($transforms.FullName) + @($modules.FullName)) -join ';'
    $plugin = Find-CachedJar 'org.jetbrains.kotlin\kotlin-compose-compiler-plugin-embeddable\2.0.21'
    $androidOutput = Join-Path $outputDir 'android-classes'
    New-Item -ItemType Directory -Path $androidOutput -Force | Out-Null
    $androidSources = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\main\java') -Recurse -Filter '*.kt' | Select-Object -ExpandProperty FullName
    $androidSources += Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\androidTest\java') -Recurse -Filter '*.kt' | Select-Object -ExpandProperty FullName
    $unitTests = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\test\java') -Recurse -Filter '*.kt'
    $androidSources += $unitTests | Select-Object -ExpandProperty FullName
    $buildConfig = Join-Path $projectRoot 'app\build\generated\source\buildConfig\debug\com\example\chesspairmobile\BuildConfig.java'
    function Quote-CompilerArgument([string]$Value) { return '"' + $Value.Replace('\', '/') + '"' }
    $arguments = @('-no-stdlib', '-no-reflect', '-jvm-target', '11', '-Xskip-metadata-version-check',
        '-classpath', (Quote-CompilerArgument $androidClasspath),
        (Quote-CompilerArgument "-Xplugin=$plugin"), '-d', (Quote-CompilerArgument $androidOutput),
        (Quote-CompilerArgument $buildConfig)) + @($androidSources | ForEach-Object { Quote-CompilerArgument $_ })
    $argumentFile = Join-Path $outputDir 'android-compiler.args'
    [IO.File]::WriteAllLines($argumentFile, $arguments, [Text.UTF8Encoding]::new($false))
    & java -Xmx3g -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "@$argumentFile"
    if ($LASTEXITCODE -ne 0) { throw 'Android source compilation failed' }
    Write-Output 'Android Kotlin sources compiled successfully (without packaging or KSP generation).'
    $testNames = $unitTests | ForEach-Object {
        $package = (Get-Content -LiteralPath $_.FullName | Select-String '^package ').Line.Substring(8).Trim()
        $package + '.' + $_.BaseName
    }
    $testArguments = @('-cp', (Quote-CompilerArgument "$androidOutput;$androidClasspath"), 'org.junit.runner.JUnitCore') + @($testNames)
    $testArgumentFile = Join-Path $outputDir 'android-jvm-tests.args'
    [IO.File]::WriteAllLines($testArgumentFile, $testArguments, [Text.UTF8Encoding]::new($false))
    & java "@$testArgumentFile"
    if ($LASTEXITCODE -ne 0) { throw 'Full JVM unit tests failed' }
    return
}
$sources = @((Join-Path $projectRoot 'app\src\main\java\com\example\chesspairmobile\data\models\Models.kt'))
$sources += Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\main\java\com\example\chesspairmobile\logic') -Filter '*.kt' | Where-Object { $_.Name -ne 'TestTournamentPresets.kt' } | Select-Object -ExpandProperty FullName
$tests = Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\test\java\com\example\chesspairmobile\logic') -Filter '*.kt' | Where-Object { $_.Name -ne 'TestTournamentPresetsTest.kt' }
$sources += $tests | Select-Object -ExpandProperty FullName
& java -cp $compilerClasspath org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 11 -classpath $runtimeClasspath -d $outputDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Kotlin compilation failed' }
$testNames = $tests | ForEach-Object { 'com.example.chesspairmobile.logic.' + $_.BaseName }
& java -cp "$outputDir;$runtimeClasspath" org.junit.runner.JUnitCore @testNames
if ($LASTEXITCODE -ne 0) { throw 'Pairing regression tests failed' }
