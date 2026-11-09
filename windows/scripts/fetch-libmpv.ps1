<#
.SYNOPSIS
    下载 libmpv-2.dll 到 native\ 目录。

.DESCRIPTION
    RainPlayer 用 mpv 做播放内核，Windows 上需要 libmpv-2.dll。
    该文件约 116 MB，不适合放进 git，因此由本脚本按需下载。

    依赖 7-Zip（7z.exe）。没有安装的话先装：
        winget install 7zip.7zip

.PARAMETER Version
    mpv 构建版本号，默认取最新一次 shinchiro 构建。
#>
param(
    [string]$Version = "20261109"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$nativeDir = Join-Path $root "native"
$target = Join-Path $nativeDir "libmpv-2.dll"

if (Test-Path $target) {
    $size = [math]::Round((Get-Item $target).Length / 1MB, 1)
    Write-Host "libmpv-2.dll 已存在（$size MB），跳过下载。" -ForegroundColor Green
    exit 0
}

$sevenZip = Get-Command 7z.exe -ErrorAction SilentlyContinue
if (-not $sevenZip) {
    Write-Host "未找到 7z.exe，请先安装 7-Zip：" -ForegroundColor Yellow
    Write-Host "    winget install 7zip.7zip" -ForegroundColor Yellow
    Write-Host "或手动下载 mpv-dev-x86_64-*.7z 并把 libmpv-2.dll 放到 native\ 目录。" -ForegroundColor Yellow
    exit 1
}

$asset = "mpv-dev-x86_64-$Version-git-8bc468896e.7z"
$url = "https://github.com/shinchiro/mpv-winbuild-cmake/releases/download/$Version/$asset"

New-Item -ItemType Directory -Force -Path $nativeDir | Out-Null
$archive = Join-Path $env:TEMP $asset

Write-Host "正在下载 libmpv（约 30 MB）…" -ForegroundColor Cyan
Write-Host "  $url"
Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing

Write-Host "正在解压…" -ForegroundColor Cyan
$extractDir = Join-Path $env:TEMP "rainplayer-mpv-$Version"
if (Test-Path $extractDir) { Remove-Item $extractDir -Recurse -Force }
& $sevenZip.Source x $archive "-o$extractDir" -y | Out-Null

$dll = Join-Path $extractDir "libmpv-2.dll"
if (-not (Test-Path $dll)) {
    throw "解压后没有找到 libmpv-2.dll"
}

Move-Item $dll $target -Force
Remove-Item $archive -Force -ErrorAction SilentlyContinue
Remove-Item $extractDir -Recurse -Force -ErrorAction SilentlyContinue

$size = [math]::Round((Get-Item $target).Length / 1MB, 1)
Write-Host "完成：native\libmpv-2.dll（$size MB）" -ForegroundColor Green
