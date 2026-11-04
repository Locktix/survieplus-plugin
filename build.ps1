# Compile le plugin SurviePlus avec les jars de Paper deja presents dans le serveur.
# Usage : powershell -File build.ps1 [-Installer]
param([switch]$Installer)
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
& jar --create --file "build\$jar" -C build\classes .
if ($LASTEXITCODE -ne 0) { throw "Echec de la creation du jar" }
Write-Host "OK -> $ici\build\$jar"

if ($Installer) {
    Copy-Item "build\$jar" "$serveur\plugins\$jar" -Force
    Write-Host "Copie dans $serveur\plugins (redemarre le serveur)"
}
