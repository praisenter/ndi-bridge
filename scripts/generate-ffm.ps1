param(
    [Parameter(Mandatory = $true)]
    [string]$SdkInclude,
    [Parameter(Mandatory = $true)]
    [string]$Jextract
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path $PSScriptRoot -Parent
$includePath = (Resolve-Path -LiteralPath $SdkInclude).Path
$jextractPath = (Resolve-Path -LiteralPath $Jextract).Path
$dynamicHeaderPath = Join-Path $includePath 'Processing.NDI.DynamicLoad.h'

if (-not (Test-Path -LiteralPath (Join-Path $includePath 'Processing.NDI.Lib.h') -PathType Leaf)) {
    throw "NDI header not found in SDK include directory: $includePath"
}
if (-not (Test-Path -LiteralPath $dynamicHeaderPath -PathType Leaf)) {
    throw "NDI dynamic-load header not found: $dynamicHeaderPath"
}

$packageDirectory = Join-Path $projectRoot 'src/main/java/org/praisenter/ndibridge/internal/generated'
New-Item -ItemType Directory -Path $packageDirectory -Force | Out-Null

$wrapperTempPath = [System.IO.Path]::GetTempFileName()
$wrapperPath = [System.IO.Path]::ChangeExtension($wrapperTempPath, '.h')
try {
    [System.IO.File]::WriteAllText($wrapperPath, "#include <Processing.NDI.Lib.h>`n#include <Processing.NDI.DynamicLoad.h>`n")

    $arguments = @(
        '--output', (Join-Path $projectRoot 'src/main/java'),
        '--target-package', 'org.praisenter.ndibridge.internal.generated',
        '--header-class-name', 'NdiDynamicLoad',
        '-I', $includePath,
        $wrapperPath
    )
    $structures = @(
        'NDIlib_v6_3',
        'NDIlib_source_t',
        'NDIlib_video_frame_v2_t',
        'NDIlib_audio_frame_v2_t',
        'NDIlib_audio_frame_interleaved_16s_t',
        'NDIlib_audio_frame_interleaved_32s_t',
        'NDIlib_audio_frame_interleaved_32f_t',
        'NDIlib_metadata_frame_t',
        'NDIlib_find_create_t',
        'NDIlib_send_create_t',
        'NDIlib_recv_create_v3_t',
        'NDIlib_tally_t',
        'NDIlib_recv_performance_t',
        'NDIlib_routing_create_t'
    )
    foreach ($structure in $structures) {
        $arguments += @('--include-struct', $structure)
    }
    & $jextractPath @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "jextract failed with exit code $LASTEXITCODE"
    }

    $tableSource = Join-Path $packageDirectory 'NDIlib_v6_3.java'
    if (-not (Test-Path -LiteralPath $tableSource -PathType Leaf)) {
        throw "Expected function table source was not generated: $tableSource"
    }

    $sharedSource = Join-Path $packageDirectory 'NdiDynamicLoad$shared.java'
    $sharedText = [System.IO.File]::ReadAllText($sharedSource)
    $portableText = [regex]::Replace($sharedText, '(?m)^[ \t]*public static final ValueLayout\.Of(?:Int|Long) C_LONG = [^\r\n]*\r?\n?', '')
    if ($portableText -eq $sharedText) {
        throw "Could not remove jextract's unused ABI-specific C_LONG helper: $sharedSource"
    }
    $longReferences = Get-ChildItem $packageDirectory -Filter '*.java' -File |
        Where-Object { $_.Name -ne 'NdiDynamicLoad$shared.java' } |
        Select-String -Pattern 'NdiDynamicLoad\.C_LONG\b'
    if ($longReferences) {
        throw 'Generated bindings use C long, whose width differs by ABI; keep platform-specific bindings for those types.'
    }
    [System.IO.File]::WriteAllText($sharedSource, $portableText, (New-Object System.Text.UTF8Encoding($false)))
} finally {
    if ($wrapperPath -and (Test-Path -LiteralPath $wrapperPath)) {
        Remove-Item -LiteralPath $wrapperPath -Force
    }
    if ($wrapperTempPath -and (Test-Path -LiteralPath $wrapperTempPath)) {
        Remove-Item -LiteralPath $wrapperTempPath -Force
    }
}

Write-Output "Generated platform-neutral FFM bindings under $packageDirectory"