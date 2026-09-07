[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceProject,
    [string]$DestinationProject = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path -LiteralPath $SourceProject).Path
$destinationRoot = (Resolve-Path -LiteralPath $DestinationProject).Path
$relativePaths = @(
    'assets/font/SourceHanSansCN-Regular.otf',
    'assets/font/SourceHanSansCN-Heavy.otf',
    'assets/skin/default/default.fnt',
    'assets/skin/default/default.png',
    'assets/skin/default/uiskin.png',
    'libs/simpleui-1.1.2.jar'
)

# 先核對所有來源與既有目標，確認完整後才複製；不覆寫不同內容的本機檔案。
foreach ($relativePath in $relativePaths) {
    $sourcePath = Join-Path $sourceRoot $relativePath
    $destinationPath = Join-Path $destinationRoot $relativePath
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw "缺少來源資產： $sourcePath"
    }
    if (Test-Path -LiteralPath $destinationPath -PathType Leaf) {
        $sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
        $destinationHash = (Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash
        if ($sourceHash -ne $destinationHash) {
            throw "目標內容不同，未覆寫： $destinationPath"
        }
    }
}

foreach ($relativePath in $relativePaths) {
    $sourcePath = Join-Path $sourceRoot $relativePath
    $destinationPath = Join-Path $destinationRoot $relativePath
    if (-not (Test-Path -LiteralPath $destinationPath -PathType Leaf)) {
        $parentPath = Split-Path -Parent $destinationPath
        New-Item -ItemType Directory -Path $parentPath -Force | Out-Null
        Copy-Item -LiteralPath $sourcePath -Destination $destinationPath
    }
    $sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash
    $destinationHash = (Get-FileHash -LiteralPath $destinationPath -Algorithm SHA256).Hash
    if ($sourceHash -ne $destinationHash) {
        throw "複製後 SHA-256 核對失敗： $destinationPath"
    }
    Write-Host "已核對： $relativePath"
}

Write-Host '本機資產已備妥；沒有修改玩家存檔或遊戲原始碼。'
