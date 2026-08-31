param(
    [string]$SourcePath = (Join-Path $PSScriptRoot '../app/src/main/ic_launcher-playstore.png'),
    [string]$OutputPath = (Join-Path $PSScriptRoot '../docs/play-store/icon-512.png')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$sourceFile = (Resolve-Path -LiteralPath $SourcePath).Path
$outputFile = [System.IO.Path]::GetFullPath($OutputPath)
if ($sourceFile -eq $outputFile) {
    throw 'Export to a separate path to preserve the source image.'
}
New-Item -ItemType Directory -Path ([System.IO.Path]::GetDirectoryName($outputFile)) -Force | Out-Null
$source = [System.Drawing.Bitmap]::new($sourceFile)
$converted = $null
$graphics = $null
$verified = $null
try {
    if ($source.Width -ne 512 -or $source.Height -ne 512) {
        throw 'The source must already be 512 x 512. This export does not resize or redraw the logo.'
    }
    $converted = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($converted)
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.DrawImageUnscaled($source, 0, 0)
    $graphics.Dispose()
    $graphics = $null
    $converted.Save($outputFile, [System.Drawing.Imaging.ImageFormat]::Png)

    $verified = [System.Drawing.Bitmap]::new($outputFile)
    $pngBytes = [System.IO.File]::ReadAllBytes($outputFile)
    if ($verified.PixelFormat -ne [System.Drawing.Imaging.PixelFormat]::Format32bppArgb -or
        $pngBytes[24] -ne 8 -or $pngBytes[25] -ne 6 -or $pngBytes.Length -gt 1MB) {
        throw 'Output must be an 8-bit-per-channel RGBA PNG smaller than 1024 KB.'
    }
    for ($y = 0; $y -lt 512; $y++) {
        for ($x = 0; $x -lt 512; $x++) {
            if ($source.GetPixel($x, $y).ToArgb() -ne $verified.GetPixel($x, $y).ToArgb()) {
                throw "Pixel mismatch at ($x, $y)."
            }
        }
    }
    [pscustomobject]@{
        File = $outputFile
        Dimensions = '512 x 512'
        Format = '32-bit RGBA PNG'
        Bytes = $pngBytes.Length
        PixelsUnchanged = $true
    }
} finally {
    if ($verified) { $verified.Dispose() }
    if ($graphics) { $graphics.Dispose() }
    if ($converted) { $converted.Dispose() }
    $source.Dispose()
}
