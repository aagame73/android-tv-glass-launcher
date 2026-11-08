# Compile, signe et installe le lanceur sur la télé (sans Android Studio ni Gradle).
# Usage : ./build.ps1            (compile + installe)
#         ./build.ps1 -SansInstall
param([switch]$SansInstall, [string]$Tele)
$ErrorActionPreference = 'Stop'

# Réglages locaux (non publiés) : copier config.example.properties en config.properties.
$cfgFile = Join-Path $PSScriptRoot 'config.properties'
if (-not (Test-Path $cfgFile)) { throw "config.properties manquant : copiez config.example.properties et adaptez-le." }
$cfg = ConvertFrom-StringData ((Get-Content $cfgFile -Encoding UTF8 | Where-Object { $_ -notmatch '^\s*#' }) -join "`n")
if (-not $Tele) { $Tele = $cfg['tv.address'] }

$sdk  = "$env:LOCALAPPDATA\Android\Sdk"
$bt   = "$sdk\build-tools\35.0.0"
$jar  = "$sdk\platforms\android-35\android.jar"
$adb  = "$sdk\platform-tools\adb.exe"
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$racine = $PSScriptRoot
$out = Join-Path $racine 'build'
function Verifier($etape) { if ($LASTEXITCODE -ne 0) { throw "Échec : $etape" } }

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory "$out\classes", "$out\gen", "$out\dex" | Out-Null

# 1. Ressources
& "$bt\aapt2.exe" compile --dir "$racine\res" -o "$out\res.zip"; Verifier 'aapt2 compile'
& "$bt\aapt2.exe" link -o "$out\base.apk" -I $jar --manifest "$racine\AndroidManifest.xml" `
    --java "$out\gen" --min-sdk-version 26 --target-sdk-version 35 "$out\res.zip"; Verifier 'aapt2 link'

# Classe Config générée à partir de config.properties
$ville = $cfg['weather.city'].Replace('\', '\\').Replace('"', '\"')
$lat = [double]::Parse($cfg['weather.latitude'], [Globalization.CultureInfo]::InvariantCulture).ToString([Globalization.CultureInfo]::InvariantCulture)
$lon = [double]::Parse($cfg['weather.longitude'], [Globalization.CultureInfo]::InvariantCulture).ToString([Globalization.CultureInfo]::InvariantCulture)
New-Item -ItemType Directory -Force "$out\gen\fr\maison\lanceur" | Out-Null
@"
package fr.maison.lanceur;
/** Généré par build.ps1 depuis config.properties : ne pas modifier. */
final class Config {
    static final String VILLE = "$ville";
    static final String LATITUDE = "$lat";
    static final String LONGITUDE = "$lon";
}
"@ | Set-Content "$out\gen\fr\maison\lanceur\Config.java" -Encoding UTF8

# 2. Java -> .class -> .dex
$sources = Get-ChildItem "$racine\src", "$out\gen" -Recurse -Filter *.java | ForEach-Object FullName
& javac -encoding UTF-8 --release 8 -Xlint:-options -classpath $jar -d "$out\classes" $sources; Verifier 'javac'
$classes = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$bt\d8.bat" --min-api 26 --release --lib $jar --output "$out\dex" $classes; Verifier 'd8'

# 3. Assemblage de l'APK
Copy-Item "$out\base.apk" "$out\nonsigne.apk"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open("$out\nonsigne.apk", 'Update')
[System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", 'classes.dex') | Out-Null
$zip.Dispose()
& "$bt\zipalign.exe" -f 4 "$out\nonsigne.apk" "$out\aligne.apk"; Verifier 'zipalign'

# 4. Signature (clé locale créée au premier lancement, à ne pas perdre : sans elle, plus de mise à jour possible)
$ks = Join-Path $racine 'lanceur.jks'
if (-not (Test-Path $ks)) {
    & keytool -genkeypair -keystore $ks -storepass android -keypass android -alias lanceur `
        -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Lanceur maison'; Verifier 'keytool'
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --out "$out\lanceur.apk" "$out\aligne.apk"; Verifier 'apksigner'
Write-Host "APK prêt : $out\lanceur.apk"

# 5. Installation
if (-not $SansInstall) {
    & $adb connect $Tele | Out-Null
    & $adb -s $Tele install -r "$out\lanceur.apk"; Verifier 'adb install'
}
