# Compile le plugin SurviePlus avec les jars de Paper deja presents dans le serveur.
# Usage : powershell -File build.ps1 [-Installer] [-SansTests]
param([switch]$Installer, [switch]$SansTests)
$ErrorActionPreference = 'Stop'
$ici = $PSScriptRoot
$serveur = Split-Path $ici -Parent
$jar = 'SurviePlus-1.0.0.jar'
Set-Location $ici

# libraries du serveur + lib\ (dependances de compilation seulement, ex : annotations JetBrains)
$cp = (@(Get-ChildItem "$serveur\libraries" -Recurse -Filter *.jar) + @(Get-ChildItem "$ici\lib" -Filter *.jar -ErrorAction SilentlyContinue) | ForEach-Object FullName) -join ';'
if (Test-Path build) { Remove-Item build -Recurse -Force }
New-Item -ItemType Directory -Force build\classes | Out-Null
$sources = Get-ChildItem src\main\java -Recurse -Filter *.java | ForEach-Object FullName
& javac --release 25 -encoding UTF-8 -Xlint:deprecation -cp $cp -d build\classes @sources
if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation" }
Copy-Item src\main\resources\* build\classes\

# Tests JUnit (src\test\java), lances avant de creer le jar. -SansTests pour les sauter.
$junit = Get-ChildItem "$ici\lib\test" -Filter 'junit-platform-console-standalone-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $SansTests -and (Test-Path src\test\java)) {
    if (-not $junit) { throw "lib\test\junit-platform-console-standalone-*.jar manquant (ou -SansTests)" }
    New-Item -ItemType Directory -Force build\test-classes | Out-Null
    $tests = @(Get-ChildItem src\test\java -Recurse -Filter *.java | ForEach-Object FullName)
    & javac --release 25 -encoding UTF-8 -cp "$cp;build\classes;$($junit.FullName)" -d build\test-classes @tests
    if ($LASTEXITCODE -ne 0) { throw "Echec de la compilation des tests" }
    & java -jar $junit.FullName execute --disable-banner --details=summary --details-theme=ascii `
        --class-path "$cp;build\classes;build\test-classes" --scan-class-path build\test-classes
    if ($LASTEXITCODE -ne 0) { throw "Des tests echouent : jar non cree" }
}


& jar --create --file "build\$jar" -C build\classes .
if ($LASTEXITCODE -ne 0) { throw "Echec de la creation du jar" }
Write-Host "OK -> $ici\build\$jar"

if ($Installer) {
    Copy-Item "build\$jar" "$serveur\plugins\$jar" -Force
    Write-Host "Copie dans $serveur\plugins (redemarre le serveur)"
}
