# Deterministic LeoVer v1.0 asset generation from logo_leover.jpg (System.Drawing / Pillow-free).
# Removes exterior white via flood-fill; preserves internal whites; crops; normalizes brand hues.

param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$srcPath = Join-Path $RepoRoot "app\src\main\res\drawable\logo_leover.jpg"
if (-not (Test-Path $srcPath)) { throw "Source not found: $srcPath" }

$docsLogo = Join-Path $RepoRoot "docs\08-marca\assets\productivos\logo-principal"
$docsIso = Join-Path $RepoRoot "docs\08-marca\assets\productivos\isotipo"
$docsLaunch = Join-Path $RepoRoot "docs\08-marca\assets\productivos\launcher"
$docsMono = Join-Path $RepoRoot "docs\08-marca\assets\productivos\monocromatico"
$docsSplash = Join-Path $RepoRoot "docs\08-marca\assets\productivos\splash"
$docsPreview = Join-Path $RepoRoot "docs\08-marca\assets\productivos\previews"
$androidNodpi = Join-Path $RepoRoot "app\src\main\res\drawable-nodpi"
$androidDrawable = Join-Path $RepoRoot "app\src\main\res\drawable"

@(
    $docsLogo, $docsIso, $docsLaunch, $docsMono, $docsSplash, $docsPreview, $androidNodpi
) | ForEach-Object { New-Item -ItemType Directory -Force -Path $_ | Out-Null }

function Test-NearWhite([byte]$r, [byte]$g, [byte]$b, [int]$thresh = 248) {
    return ($r -ge $thresh -and $g -ge $thresh -and $b -ge $thresh)
}

function ClampByte([double]$v) {
    if ($v -lt 0) { return [byte]0 }
    if ($v -gt 255) { return [byte]255 }
    return [byte][Math]::Round($v)
}

function Normalize-BrandPixel([byte]$r, [byte]$g, [byte]$b) {
    # Soft remap toward official palette while keeping relative luminance / gradients.
    $max = [Math]::Max($r, [Math]::Max($g, $b))
    $min = [Math]::Min($r, [Math]::Min($g, $b))
    if (($max - $min) -lt 18) { return @($r, $g, $b) } # near-gray/white detail — leave

    $lum = (0.2126 * $r + 0.7152 * $g + 0.0722 * $b) / 255.0

    # Orange family (Leo)
    if ($r -gt $g + 15 -and $r -gt $b + 25 -and $r -gt 90) {
        # Blend toward #FF7A00 / #FFA64D band by luminance
        $t = [Math]::Min(1.0, [Math]::Max(0.0, ($lum - 0.35) / 0.45))
        $tr = 255; $tg = [int](122 + (166 - 122) * $t); $tb = [int](0 + 77 * $t) # FF7A00 -> FFA64D-ish
        $nr = ClampByte($r * 0.45 + $tr * 0.55)
        $ng = ClampByte($g * 0.45 + $tg * 0.55)
        $nb = ClampByte($b * 0.45 + $tb * 0.55)
        return @($nr, $ng, $nb)
    }

    # Green family (Ver)
    if ($g -gt $r + 10 -and $g -gt $b + 10 -and $g -gt 70) {
        $t = [Math]::Min(1.0, [Math]::Max(0.0, ($lum - 0.25) / 0.45))
        # #247A3D -> #49B749
        $tr = [int](36 + (73 - 36) * $t)
        $tg = [int](122 + (183 - 122) * $t)
        $tb = [int](61 + (73 - 61) * $t)
        $nr = ClampByte($r * 0.45 + $tr * 0.55)
        $ng = ClampByte($g * 0.45 + $tg * 0.55)
        $nb = ClampByte($b * 0.45 + $tb * 0.55)
        return @($nr, $ng, $nb)
    }

    return @($r, $g, $b)
}

function Remove-ExteriorWhite([System.Drawing.Bitmap]$src) {
    $w = $src.Width; $h = $src.Height
    $dst = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.DrawImage($src, 0, 0, $w, $h)
    $g.Dispose()

    $visited = New-Object 'bool[,]' $w, $h
    $queue = New-Object System.Collections.Generic.Queue[object]
    $thresh = 248

    for ($x = 0; $x -lt $w; $x++) {
        foreach ($y in @(0, $h - 1)) {
            $p = $dst.GetPixel($x, $y)
            if (Test-NearWhite $p.R $p.G $p.B $thresh) { $queue.Enqueue(@($x, $y)) }
        }
    }
    for ($y = 0; $y -lt $h; $y++) {
        foreach ($x in @(0, $w - 1)) {
            $p = $dst.GetPixel($x, $y)
            if (Test-NearWhite $p.R $p.G $p.B $thresh) { $queue.Enqueue(@($x, $y)) }
        }
    }

    $dirs = @(@(1,0),@(-1,0),@(0,1),@(0,-1))
    while ($queue.Count -gt 0) {
        $item = $queue.Dequeue()
        $x = $item[0]; $y = $item[1]
        if ($x -lt 0 -or $y -lt 0 -or $x -ge $w -or $y -ge $h) { continue }
        if ($visited[$x, $y]) { continue }
        $visited[$x, $y] = $true
        $p = $dst.GetPixel($x, $y)
        if (-not (Test-NearWhite $p.R $p.G $p.B $thresh)) { continue }
        $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 255, 255, 255))
        foreach ($d in $dirs) { $queue.Enqueue(@(($x + $d[0]), ($y + $d[1]))) }
    }

    # Soften remaining near-white fringe (anti-alias against removed bg)
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $p = $dst.GetPixel($x, $y)
            if ($p.A -eq 0) { continue }
            $whiteness = [Math]::Min($p.R, [Math]::Min($p.G, $p.B))
            if ($whiteness -ge 230) {
                # If neighbor transparent, treat as fringe
                $hasTrans = $false
                foreach ($d in $dirs) {
                    $nx = $x + $d[0]; $ny = $y + $d[1]
                    if ($nx -lt 0 -or $ny -lt 0 -or $nx -ge $w -or $ny -ge $h) { $hasTrans = $true; break }
                    if ($dst.GetPixel($nx, $ny).A -eq 0) { $hasTrans = $true; break }
                }
                if ($hasTrans) {
                    $a = ClampByte(255 * (1.0 - (($whiteness - 230) / 25.0)))
                    if ($a -lt 12) { $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)) }
                    else { $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb([int]$a, $p.R, $p.G, $p.B)) }
                }
            }
        }
    }
    return $dst
}

function Get-OpaqueBounds([System.Drawing.Bitmap]$bmp, [int]$alphaMin = 8) {
    $minX = $bmp.Width; $minY = $bmp.Height; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            if ($bmp.GetPixel($x, $y).A -ge $alphaMin) {
                if ($x -lt $minX) { $minX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    if ($maxX -lt 0) { throw "No opaque pixels found" }
    return [System.Drawing.Rectangle]::FromLTRB($minX, $minY, $maxX + 1, $maxY + 1)
}

function Crop-Bitmap([System.Drawing.Bitmap]$bmp, [System.Drawing.Rectangle]$rect) {
    $out = New-Object System.Drawing.Bitmap $rect.Width, $rect.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.DrawImage($bmp, (New-Object System.Drawing.Rectangle 0, 0, $rect.Width, $rect.Height), $rect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    return $out
}

function Normalize-BitmapColors([System.Drawing.Bitmap]$bmp) {
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $p = $bmp.GetPixel($x, $y)
            if ($p.A -lt 8) { continue }
            $n = Normalize-BrandPixel $p.R $p.G $p.B
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb([int]$p.A, [int]$n[0], [int]$n[1], [int]$n[2]))
        }
    }
}

function Resize-MaxSide([System.Drawing.Bitmap]$bmp, [int]$minMaxSide, [bool]$forceSquare = $false, [int]$squareSize = 0) {
    if ($forceSquare) {
        $size = $squareSize
        $out = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($out)
        $g.Clear([System.Drawing.Color]::Transparent)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $scale = [Math]::Min(($size * 0.86) / $bmp.Width, ($size * 0.86) / $bmp.Height)
        $dw = [int][Math]::Round($bmp.Width * $scale)
        $dh = [int][Math]::Round($bmp.Height * $scale)
        $dx = [int](($size - $dw) / 2)
        $dy = [int](($size - $dh) / 2)
        $g.DrawImage($bmp, $dx, $dy, $dw, $dh)
        $g.Dispose()
        return $out
    }

    $maxSide = [Math]::Max($bmp.Width, $bmp.Height)
    if ($maxSide -ge $minMaxSide) {
        # still copy to new bitmap
        $out = New-Object System.Drawing.Bitmap $bmp.Width, $bmp.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($out)
        $g.DrawImage($bmp, 0, 0, $bmp.Width, $bmp.Height)
        $g.Dispose()
        return $out
    }
    $scale = $minMaxSide / [double]$maxSide
    $nw = [int][Math]::Round($bmp.Width * $scale)
    $nh = [int][Math]::Round($bmp.Height * $scale)
    $out = New-Object System.Drawing.Bitmap $nw, $nh, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($bmp, 0, 0, $nw, $nh)
    $g.Dispose()
    return $out
}

function Resize-MinWidth([System.Drawing.Bitmap]$bmp, [int]$minWidth) {
    if ($bmp.Width -ge $minWidth) {
        $out = New-Object System.Drawing.Bitmap $bmp.Width, $bmp.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($out)
        $g.DrawImage($bmp, 0, 0, $bmp.Width, $bmp.Height)
        $g.Dispose()
        return $out
    }
    $scale = $minWidth / [double]$bmp.Width
    $nw = $minWidth
    $nh = [int][Math]::Round($bmp.Height * $scale)
    $out = New-Object System.Drawing.Bitmap $nw, $nh, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($bmp, 0, 0, $nw, $nh)
    $g.Dispose()
    return $out
}

function Save-Png([System.Drawing.Bitmap]$bmp, [string]$path) {
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Host "Wrote $path ($($bmp.Width)x$($bmp.Height))"
}

function Find-GapY([System.Drawing.Bitmap]$bmp, [System.Drawing.Rectangle]$bounds) {
    $bestY = -1; $bestScore = [int]::MaxValue
    for ($y = $bounds.Top + [int]($bounds.Height * 0.45); $y -lt $bounds.Top + [int]($bounds.Height * 0.85); $y++) {
        $count = 0
        for ($x = $bounds.Left; $x -lt $bounds.Right; $x++) {
            if ($bmp.GetPixel($x, $y).A -ge 16) { $count++ }
        }
        if ($count -lt $bestScore) { $bestScore = $count; $bestY = $y }
    }
    return $bestY
}

function Make-Monochrome([System.Drawing.Bitmap]$src) {
    $out = New-Object System.Drawing.Bitmap $src.Width, $src.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $src.Height; $y++) {
        for ($x = 0; $x -lt $src.Width; $x++) {
            $p = $src.GetPixel($x, $y)
            if ($p.A -lt 20) {
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            } else {
                # Solid white silhouette (themed icons), alpha from source opacity
                $out.SetPixel($x, $y, [System.Drawing.Color]::FromArgb([int]$p.A, 255, 255, 255))
            }
        }
    }
    return $out
}

function Compose-OnBackground([System.Drawing.Bitmap]$fg, [System.Drawing.Color]$bg, [int]$canvasW, [int]$canvasH, [double]$scaleFill = 0.72) {
    $out = New-Object System.Drawing.Bitmap $canvasW, $canvasH, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.Clear($bg)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $scale = [Math]::Min(($canvasW * $scaleFill) / $fg.Width, ($canvasH * $scaleFill) / $fg.Height)
    $dw = [int][Math]::Round($fg.Width * $scale)
    $dh = [int][Math]::Round($fg.Height * $scale)
    $dx = [int](($canvasW - $dw) / 2)
    $dy = [int](($canvasH - $dh) / 2)
    $g.DrawImage($fg, $dx, $dy, $dw, $dh)
    $g.Dispose()
    return $out
}

function Compose-Horizontal([System.Drawing.Bitmap]$iso, [System.Drawing.Bitmap]$word, [int]$minWidth) {
    $gap = [int][Math]::Round($iso.Height * 0.08)
    $targetH = [Math]::Max($iso.Height, $word.Height)
    # scale word to ~55% of iso height for optical balance
    $wordScale = ($iso.Height * 0.55) / $word.Height
    $ww = [int][Math]::Round($word.Width * $wordScale)
    $wh = [int][Math]::Round($word.Height * $wordScale)
    $isoScale = 1.0
    $iw = $iso.Width; $ih = $iso.Height
    $totalW = $iw + $gap + $ww
    $totalH = [Math]::Max($ih, $wh)
    $canvas = New-Object System.Drawing.Bitmap $totalW, $totalH, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($canvas)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($iso, 0, [int](($totalH - $ih) / 2), $iw, $ih)
    $g.DrawImage($word, $iw + $gap, [int](($totalH - $wh) / 2), $ww, $wh)
    $g.Dispose()
    return (Resize-MinWidth $canvas $minWidth)
}

function Make-LauncherForeground([System.Drawing.Bitmap]$iso, [int]$size = 1024) {
    # Safe zone ~66% (18% inset) — content inside center
    $out = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $safe = $size * 0.64
    $scale = [Math]::Min($safe / $iso.Width, $safe / $iso.Height)
    $dw = [int][Math]::Round($iso.Width * $scale)
    $dh = [int][Math]::Round($iso.Height * $scale)
    $dx = [int](($size - $dw) / 2)
    $dy = [int](($size - $dh) / 2)
    $g.DrawImage($iso, $dx, $dy, $dw, $dh)
    $g.Dispose()
    return $out
}

function Make-RoundPreview([System.Drawing.Bitmap]$fg, [System.Drawing.Color]$bg, [int]$size = 1024) {
    $composed = Compose-OnBackground $fg $bg $size $size 1.0
    # Actually fg already has safe padding; draw bg then fg full
    $out = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($out)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddEllipse(0, 0, $size - 1, $size - 1)
    $g.SetClip($path)
    $g.Clear($bg)
    $g.DrawImage($fg, 0, 0, $size, $size)
    $g.ResetClip()
    $g.Dispose()
    $composed.Dispose()
    return $out
}

Write-Host "Source: $srcPath"
$src = [System.Drawing.Bitmap]::FromFile($srcPath)
Write-Host "Loaded $($src.Width)x$($src.Height)"

Write-Host "Removing exterior white (flood-fill)..."
$transparent = Remove-ExteriorWhite $src
Normalize-BitmapColors $transparent
$bounds = Get-OpaqueBounds $transparent
Write-Host "Content bounds: $bounds"
$fullCrop = Crop-Bitmap $transparent $bounds

$gapY = Find-GapY $transparent $bounds
Write-Host "Gap Y (absolute): $gapY"
$isoRect = [System.Drawing.Rectangle]::FromLTRB($bounds.Left, $bounds.Top, $bounds.Right, $gapY - 4)
$wordRect = [System.Drawing.Rectangle]::FromLTRB($bounds.Left, $gapY + 4, $bounds.Right, $bounds.Bottom)
$isoRaw = Crop-Bitmap $transparent $isoRect
$wordRaw = Crop-Bitmap $transparent $wordRect
# tighten crops
$iso = Crop-Bitmap $isoRaw (Get-OpaqueBounds $isoRaw)
$word = Crop-Bitmap $wordRaw (Get-OpaqueBounds $wordRaw)
$isoRaw.Dispose(); $wordRaw.Dispose()

# --- Docs productivos ---
$vertical = Resize-MaxSide $fullCrop 1600
Save-Png $vertical (Join-Path $docsLogo "leover-logo-vertical-v1.png")

$horizontal = Compose-Horizontal $iso $word 1800
Save-Png $horizontal (Join-Path $docsLogo "leover-logo-horizontal-v1.png")

$wordmark = Resize-MinWidth $word 1200
Save-Png $wordmark (Join-Path $docsLogo "leover-wordmark-v1.png")

$isotipo1024 = Resize-MaxSide $iso 1024 -forceSquare $true -squareSize 1024
Save-Png $isotipo1024 (Join-Path $docsIso "leover-isotipo-v1.png")
Save-Png $isotipo1024 (Join-Path $docsIso "leover-isotipo-transparente-v1.png")

$launcherFg = Make-LauncherForeground $iso 1024
Save-Png $launcherFg (Join-Path $docsLaunch "leover-launcher-foreground-v1.png")

$cream = [System.Drawing.Color]::FromArgb(255, 255, 246, 234)
$launcherPreview = Compose-OnBackground $launcherFg $cream 1024 1024 1.0
Save-Png $launcherPreview (Join-Path $docsLaunch "leover-launcher-preview-v1.png")

$roundPreview = Make-RoundPreview $launcherFg $cream 1024
Save-Png $roundPreview (Join-Path $docsLaunch "leover-launcher-round-preview-v1.png")

$mono = Make-Monochrome $isotipo1024
Save-Png $mono (Join-Path $docsMono "leover-isotipo-monochrome-v1.png")

$splash = Compose-OnBackground $vertical $cream 1600 1600 0.62
Save-Png $splash (Join-Path $docsSplash "leover-splash-v1.png")

# Verification previews
Save-Png (Compose-OnBackground $vertical ([System.Drawing.Color]::White) 1600 1600 0.7) (Join-Path $docsPreview "preview-logo-vertical-white.png")
Save-Png (Compose-OnBackground $vertical $cream 1600 1600 0.7) (Join-Path $docsPreview "preview-logo-vertical-cream.png")
Save-Png (Compose-OnBackground $isotipo1024 $cream 1024 1024 0.85) (Join-Path $docsPreview "preview-isotipo-cream.png")
Save-Png $launcherPreview (Join-Path $docsPreview "preview-launcher-square.png")
Save-Png $roundPreview (Join-Path $docsPreview "preview-launcher-round.png")
Save-Png (Compose-OnBackground $mono ([System.Drawing.Color]::FromArgb(255, 47, 58, 55)) 1024 1024 0.85) (Join-Path $docsPreview "preview-monochrome.png")
Save-Png $splash (Join-Path $docsPreview "preview-splash.png")

# --- Android nodpi ---
Copy-Item (Join-Path $docsLogo "leover-logo-vertical-v1.png") (Join-Path $androidNodpi "leover_logo_official.png") -Force
Copy-Item (Join-Path $docsLogo "leover-logo-horizontal-v1.png") (Join-Path $androidNodpi "leover_logo_horizontal.png") -Force
Copy-Item (Join-Path $docsIso "leover-isotipo-transparente-v1.png") (Join-Path $androidNodpi "leover_isotype_official.png") -Force
Copy-Item (Join-Path $docsSplash "leover-splash-v1.png") (Join-Path $androidNodpi "leover_splash_logo.png") -Force

# Launcher PNGs in drawable (referenced by inset XML)
Copy-Item (Join-Path $docsLaunch "leover-launcher-foreground-v1.png") (Join-Path $androidDrawable "leover_launcher_foreground.png") -Force
Copy-Item (Join-Path $docsMono "leover-isotipo-monochrome-v1.png") (Join-Path $androidDrawable "leover_isotype_monochrome.png") -Force

# cleanup
$src.Dispose(); $transparent.Dispose(); $fullCrop.Dispose()
$iso.Dispose(); $word.Dispose(); $vertical.Dispose(); $horizontal.Dispose()
$wordmark.Dispose(); $isotipo1024.Dispose(); $launcherFg.Dispose()
$launcherPreview.Dispose(); $roundPreview.Dispose(); $mono.Dispose(); $splash.Dispose()

Write-Host "DONE"
