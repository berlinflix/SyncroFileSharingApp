<#
  Builds SyncroSetup-<version>.exe: a single-file, per-user Windows installer for Syncro.

  Usage (normally run through Gradle: ./gradlew :desktop:packageSetup -Psyncro.packagingJdk=<JDK with jpackage>):
    powershell -ExecutionPolicy Bypass -File installer\build.ps1 -AppImage desktop\build\compose\binaries\main-release\app\Syncro -Version 2.0.0

  Needs only what ships with Windows 10/11: the .NET Framework 4.8 C# compiler and WPF.
#>
param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [string]$Version = "2.0.0",
    [string]$OutDir = ""
)
$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
if (-not $OutDir) { $OutDir = Join-Path $root "desktop\build\setup" }
$work = Join-Path $OutDir "work"
New-Item -ItemType Directory -Force $work | Out-Null

$AppImage = (Resolve-Path $AppImage).Path
if (-not (Test-Path (Join-Path $AppImage "Syncro.exe"))) { throw "No Syncro.exe in $AppImage - run :desktop:createReleaseDistributable first." }

Write-Host "Packing app image from $AppImage"
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$payload = Join-Path $work "payload.zip"
if (Test-Path $payload) { Remove-Item $payload -Force }
# Build the archive entry by entry so paths use '/' (Windows PowerShell's CreateFromDirectory writes '\').
$zip = [System.IO.Compression.ZipFile]::Open($payload, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    $base = $AppImage.TrimEnd('\') + '\'
    Get-ChildItem -Path $AppImage -Recurse -Force | ForEach-Object {
        $relative = $_.FullName.Substring($base.Length).Replace('\', '/')
        if ($_.PSIsContainer) {
            if (-not (Get-ChildItem -Path $_.FullName -Force | Select-Object -First 1)) { [void]$zip.CreateEntry($relative + '/') }
        } else {
            [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $_.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal)
        }
    }
} finally { $zip.Dispose() }
Set-Content -Path (Join-Path $work "version.txt") -Value $Version -NoNewline -Encoding ascii

$fonts = Join-Path $root "desktop\src\main\resources\font"
$icon = Join-Path $root "desktop\icons\syncro.ico"
$fx = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319"
$csc = Join-Path $fx "csc.exe"
$wpf = Join-Path $fx "WPF"
$out = Join-Path $OutDir "SyncroSetup-$Version.exe"

$args = @(
    "/nologo", "/target:winexe", "/optimize+", "/platform:anycpu",
    "/out:$out",
    "/win32icon:$icon",
    "/win32manifest:$(Join-Path $here 'app.manifest')",
    "/r:$(Join-Path $wpf 'PresentationCore.dll')",
    "/r:$(Join-Path $wpf 'PresentationFramework.dll')",
    "/r:$(Join-Path $wpf 'WindowsBase.dll')",
    "/r:$(Join-Path $fx 'System.Xaml.dll')",
    "/r:$(Join-Path $fx 'System.IO.Compression.dll')",
    "/r:$(Join-Path $fx 'System.IO.Compression.FileSystem.dll')",
    "/resource:$payload,Syncro.payload.zip",
    "/resource:$(Join-Path $here 'Setup.xaml'),Syncro.Setup.xaml",
    "/resource:$(Join-Path $work 'version.txt'),Syncro.version.txt",
    "/resource:$icon,Syncro.icon.ico"
)
foreach ($weight in "regular", "medium", "semibold", "bold") {
    $args += "/resource:$(Join-Path $fonts "roboto_$weight.ttf"),Syncro.font.roboto_$weight.ttf"
}
$args += (Join-Path $here "SyncroSetup.cs")

Write-Host "Compiling $out"
& $csc @args
if ($LASTEXITCODE -ne 0) { throw "csc failed ($LASTEXITCODE)" }
$size = [math]::Round((Get-Item $out).Length / 1MB, 1)
Write-Host "Built $out ($size MB)"
