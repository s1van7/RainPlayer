<#
.SYNOPSIS
    构建 Windows 发布包（绿色版 zip + 可选安装包）。

.DESCRIPTION
    1. 检查 native\libmpv-2.dll
    2. dotnet publish 自包含单目录
    3. 压成 zip
    4. 如果装了 Inno Setup，再出一个安装包

.PARAMETER Version
    版本号，默认读 csproj 里的 Version。

.PARAMETER SkipInstaller
    只出绿色版，跳过 Inno Setup。
#>
param(
    [string]$Version = "",
    [switch]$SkipInstaller
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "dist"
$out = Join-Path $root "release"

if (-not $Version) {
    $csproj = Join-Path $root "src\RainPlayer.App\RainPlayer.App.csproj"
    $match = Select-String -Path $csproj -Pattern '<Version>([^<]+)</Version>'
    $Version = if ($match) { $match.Matches[0].Groups[1].Value } else { "1.0.0" }
}

Write-Host "==> RainPlayer $Version Windows 构建" -ForegroundColor Cyan

$dll = Join-Path $root "native\libmpv-2.dll"
if (-not (Test-Path $dll)) {
    Write-Host "!! 缺少 native\libmpv-2.dll，先运行 scripts\fetch-libmpv.ps1" -ForegroundColor Red
    exit 1
}

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host "==> 发布 win-x64" -ForegroundColor Cyan
dotnet publish (Join-Path $root "src\RainPlayer.App\RainPlayer.App.csproj") `
    -c Release -r win-x64 --self-contained true `
    -p:PublishSingleFile=false -p:DebugType=none `
    -p:Version=$Version -o $dist
if ($LASTEXITCODE -ne 0) { throw "dotnet publish 失败" }

Copy-Item $dll (Join-Path $dist "libmpv-2.dll") -Force

$zip = Join-Path $out "RainPlayer-$Version-win-x64.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Write-Host "==> 打包绿色版" -ForegroundColor Cyan
Compress-Archive -Path (Join-Path $dist "*") -DestinationPath $zip -CompressionLevel Optimal
$zipSize = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host "    $zip（$zipSize MB）" -ForegroundColor Green

if (-not $SkipInstaller) {
    $iscc = Get-Command iscc.exe -ErrorAction SilentlyContinue
    if (-not $iscc) {
        $candidates = @(
            "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
            "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
        )
        $iscc = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    }

    if ($iscc) {
        Write-Host "==> 生成安装包" -ForegroundColor Cyan
        $iss = Join-Path $root "scripts\installer.iss"
        & $iscc "/DMyAppVersion=$Version" "/DSourceDir=$dist" "/DOutputDir=$out" $iss
        if ($LASTEXITCODE -eq 0) {
            Write-Host "    $out\RainPlayer-$Version-win-x64-setup.exe" -ForegroundColor Green
        }
    }
    else {
        Write-Host "!! 未找到 Inno Setup，跳过安装包" -ForegroundColor Yellow
        Write-Host "   安装后重试：winget install JRSoftware.InnoSetup" -ForegroundColor Yellow
    }
}

Write-Host "`n完成，产物在 $out" -ForegroundColor Green
