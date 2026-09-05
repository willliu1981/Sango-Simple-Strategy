[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$StudyRoutineSource = ""
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$fontDestination = Join-Path $projectRoot "assets\font"
$requiredFontNames = @(
    "SourceHanSansCN-Regular.otf",
    "SourceHanSansCN-Heavy.otf"
)

function Resolve-StudyRoutineSource {
    param(
        [string]$ExplicitSource,
        [string]$RootDirectory
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitSource)) {
        return (Resolve-Path $ExplicitSource).Path
    }

    $candidatePaths = @(
        (Join-Path (Split-Path $RootDirectory -Parent) "StudyRoutineCurated-source.zip"),
        (Join-Path (Split-Path $RootDirectory -Parent) "StudyRoutineCurated"),
        (Join-Path (Split-Path $RootDirectory -Parent) "StudyRoutine")
    )

    foreach ($candidatePath in $candidatePaths) {
        if (Test-Path $candidatePath) {
            return (Resolve-Path $candidatePath).Path
        }
    }

    throw "找不到 StudyRoutine source。請使用 -StudyRoutineSource 指定 StudyRoutineCurated-source.zip 或解壓後的專案目錄。"
}

function Find-RequiredFont {
    param(
        [string]$SearchRoot,
        [string]$FontName
    )

    $fontFile = Get-ChildItem -Path $SearchRoot -Filter $FontName -File -Recurse | Select-Object -First 1
    if ($null -eq $fontFile) {
        throw "在 StudyRoutine source 中找不到 $FontName。"
    }
    return $fontFile.FullName
}

$resolvedSource = Resolve-StudyRoutineSource -ExplicitSource $StudyRoutineSource -RootDirectory $projectRoot
$temporaryDirectory = $null
$searchRoot = $resolvedSource

try {
    if ((Get-Item $resolvedSource).PSIsContainer -eq $false) {
        if ([System.IO.Path]::GetExtension($resolvedSource) -ne ".zip") {
            throw "StudyRoutineSource 必須是 ZIP 或專案目錄：$resolvedSource"
        }

        $temporaryDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("sango-fonts-" + [Guid]::NewGuid().ToString("N"))
        New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
        Expand-Archive -Path $resolvedSource -DestinationPath $temporaryDirectory -Force
        $searchRoot = $temporaryDirectory
    }

    New-Item -ItemType Directory -Path $fontDestination -Force | Out-Null

    foreach ($fontName in $requiredFontNames) {
        $sourceFontPath = Find-RequiredFont -SearchRoot $searchRoot -FontName $fontName
        $destinationFontPath = Join-Path $fontDestination $fontName
        Copy-Item -Path $sourceFontPath -Destination $destinationFontPath -Force
        Write-Host "已準備：$destinationFontPath"
    }

    Write-Host "Sango 字型資產已準備完成。"
}
finally {
    if ($null -ne $temporaryDirectory -and (Test-Path $temporaryDirectory)) {
        Remove-Item -Path $temporaryDirectory -Recurse -Force
    }
}
