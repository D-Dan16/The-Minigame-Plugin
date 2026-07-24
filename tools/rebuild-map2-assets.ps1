$ErrorActionPreference = 'Stop'
# The development server loads this pack directly. Keep source resources untouched while iterating.
$root = Join-Path $PSScriptRoot '..\run\plugins\MinigamePlugin\Minigames\HoleInTheWall\Map2'

function B16($w, [int]$v) { $b = [BitConverter]::GetBytes([int16]$v); [Array]::Reverse($b); $w.Write($b) }
function B32($w, [int]$v) { $b = [BitConverter]::GetBytes($v); [Array]::Reverse($b); $w.Write($b) }
function B64($w, [long]$v) { $b = [BitConverter]::GetBytes($v); [Array]::Reverse($b); $w.Write($b) }
function Str($w, [string]$s) { $b = [Text.Encoding]::UTF8.GetBytes($s); B16 $w $b.Length; $w.Write($b) }
function Tag($w, [byte]$type, [string]$name) { $w.Write($type); Str $w $name }
function VarInt($bytes, [int]$value) { do { $b = $value -band 127; $value = $value -shr 7; if ($value) { $b = $b -bor 128 }; $bytes.Add([byte]$b) } while ($value) }

function Save-Schem([string]$path, [int]$width, [int]$height, [int]$length, [int[]]$offset, [string[]]$blocks) {
    if ($blocks.Count -ne $width * $height * $length) { throw "Invalid block count for $path" }
    $palette = [ordered]@{}
    foreach ($block in $blocks) { if (-not $palette.Contains($block)) { $palette[$block] = $palette.Count } }
    $data = [Collections.Generic.List[byte]]::new(); foreach ($block in $blocks) { VarInt $data $palette[$block] }
    [IO.Directory]::CreateDirectory((Split-Path $path)) | Out-Null
    $file = [IO.File]::Create($path); $gzip = [IO.Compression.GZipStream]::new($file, [IO.Compression.CompressionLevel]::Optimal); $w = [IO.BinaryWriter]::new($gzip)
    try {
        Tag $w 10 ''; Tag $w 10 'Schematic'; Tag $w 3 'Version'; B32 $w 3; Tag $w 3 'DataVersion'; B32 $w 4325
        Tag $w 10 'Metadata'; Tag $w 4 'Date'; B64 $w ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()); Tag $w 10 'WorldEdit'; Tag $w 8 'Version'; Str $w '7.3.0'; Tag $w 8 'EditingPlatform'; Str $w 'enginehub:unknown'; $w.Write([byte]0); Tag $w 11 'Origin'; B32 $w 3; 0..2 | ForEach-Object { B32 $w 0 }; $w.Write([byte]0)
        Tag $w 2 'Width'; B16 $w $width; Tag $w 2 'Height'; B16 $w $height; Tag $w 2 'Length'; B16 $w $length; Tag $w 11 'Offset'; B32 $w 3; $offset | ForEach-Object { B32 $w $_ }
        Tag $w 10 'Blocks'; Tag $w 10 'Palette'; foreach ($entry in $palette.GetEnumerator()) { Tag $w 3 $entry.Key; B32 $w $entry.Value }; $w.Write([byte]0); Tag $w 7 'Data'; B32 $w $data.Count; $w.Write($data.ToArray()); Tag $w 9 'BlockEntities'; $w.Write([byte]10); B32 $w 0; $w.Write([byte]0); $w.Write([byte]0); $w.Write([byte]0)
    } finally { $w.Dispose(); $gzip.Dispose(); $file.Dispose() }
}

function Platform([int]$stage) {
    $result = [Collections.Generic.List[string]]::new()
    for ($z = 0; $z -lt 12; $z++) { for ($x = 0; $x -lt 12; $x++) {
        $core = $x -ge 3 -and $x -le 8 -and $z -ge 3 -and $z -le 8
        $yellowRing = $x -ge 2 -and $x -le 9 -and $z -ge 2 -and $z -le 9
        $result.Add($(if ($core) { 'minecraft:green_glazed_terracotta[facing=north]' } elseif ($stage -eq 1 -and -not $yellowRing) { 'minecraft:red_glazed_terracotta[facing=north]' } elseif ($stage -le 2 -and $yellowRing) { $(if ($stage -eq 1) { 'minecraft:yellow_glazed_terracotta[facing=north]' } else { 'minecraft:red_glazed_terracotta[facing=north]' }) } else { 'minecraft:air' }))
    } }
    $result.ToArray()
}

# The platforms are nested: red outer ring disappears, yellow ring becomes red, then that ring disappears.
1..3 | ForEach-Object { Save-Schem (Join-Path $root "platforms\Platform-$_.schem") 12 1 12 @(-5,0,-6) (Platform $_) }

function New-WallData([string[]]$front, [string[]]$pistons) {
    $result = [Collections.Generic.List[string]]::new()
    for ($y = 0; $y -lt 5; $y++) { for ($z = 0; $z -lt 2; $z++) { for ($x = 0; $x -lt 14; $x++) {
        $frontBlock = $front[$x + 14 * $y]
        $result.Add($(if ($z -eq 0) { if ($pistons -contains "$x,$y") { 'minecraft:piston[extended=false,facing=south]' } else { 'minecraft:air' } } else { $frontBlock }))
    } } }
    $result.ToArray()
}

function New-ExplicitWall([string[]]$rowsTopDown, [string[]]$pistons) {
    if ($rowsTopDown.Count -ne 5 -or ($rowsTopDown | Where-Object { $_.Length -ne 14 })) { throw 'An explicit wall needs five 14-block rows' }
    $states = @{ S='minecraft:slime_block'; C='minecraft:oxidized_cut_copper'; A='minecraft:oxidized_cut_copper_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]'; B='minecraft:oxidized_cut_copper_stairs[facing=west,half=top,shape=straight,waterlogged=false]'; T='minecraft:iron_trapdoor[facing=south,half=bottom,open=false,powered=false,waterlogged=false]'; F='minecraft:warped_fence[east=false,north=false,south=false,waterlogged=false,west=false]'; N='minecraft:nether_brick_fence[east=false,north=false,south=false,waterlogged=false,west=false]' }
    $front = [string[]]::new(70)
    for ($y = 0; $y -lt 5; $y++) { for ($x = 0; $x -lt 14; $x++) { $symbol = [string]$rowsTopDown[4 - $y][$x]; $front[$x + 14 * $y] = $(if ($states.ContainsKey($symbol)) { $states[$symbol] } else { 'minecraft:air' }) } }
    foreach ($piston in $pistons) {
        $coordinates = $piston.Split(',')
        $x = [int]$coordinates[0]; $y = [int]$coordinates[1]
        if ($x -notin 0..13 -or $y -notin 0..4 -or $front[$x + 14 * $y] -eq 'minecraft:air') { throw "Piston $piston must point at a wall block" }
    }
    New-WallData $front $pistons
}

# Each entry below is an authored wall layout, not a parametrized variation or a copied schematic.
$wallRoot = Join-Path $root 'wallpack'
# Easy is still readable, but now requires a jump, a committed side-step, or a low duck route.
Save-Schem (Join-Path $wallRoot 'easy\Wall-E1.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSS..SSSS....','S..S..S..S..SS','SSSS..SSSS..SS','....SS....SS..','SSSSSS..SSSS..') @('1,2','4,0','7,2','10,0','12,2'))
Save-Schem (Join-Path $wallRoot 'easy\Wall-E2.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSS..SSS..SSS.','S.S..S.S..S.S.','S.S..S.S..S.S.','SSS..SSS..SSS.','..SS...SS...SS') @('0,2','5,2','10,2'))
Save-Schem (Join-Path $wallRoot 'easy\Wall-E3.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSS....SSSS..','S..S.SS.S..S..','SSSS.SS.SSSS..','....S..S....S.','SSSSS..SSSSSS.') @('1,2','5,2','9,2','2,0','9,0'))
Save-Schem (Join-Path $wallRoot 'easy\Wall-E4.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSS..SSSS..SS.','..S..S..SS..S.','SSSSS..S..SSS.','S....SSS..S...','SSSS...SSSSSS.') @('2,2','5,3','10,2','1,0','10,0'))

# Medium: each wall has a deliberately narrow primary route plus a late obstruction.
Save-Schem (Join-Path $wallRoot 'medium\Wall-M1.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSS..SSSS....','S..S..S..S..SS','SSSC..SSCSSSSS','S..S....S..S..','SSSSS..SSSSSS.') @('1,2','6,2','11,2','2,0','9,0'))
Save-Schem (Join-Path $wallRoot 'medium\Wall-M2.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('..SSSS..SSSS..','S.S..S..S..S.S','SSS..SSSS..SSS','..S....S....S.','SSSSS..SSSSSS.') @('2,2','6,2','11,2','2,0','9,0'))
Save-Schem (Join-Path $wallRoot 'medium\Wall-M3.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSSS....SSSS.','S...S.SS.S...S','SSCSS.SS.SSSSS','..S..S..S..S..','SSSSS..SSSSSS.') @('1,2','6,3','10,2','2,0','9,0'))
Save-Schem (Join-Path $wallRoot 'medium\Wall-M4.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SS..SSSS..SSSS','S...S..S..S..S','SSSSS..SSSS..S','..S..SS..S..SS','SSSSS..SSSSSS.') @('2,2','7,3','10,2','2,0','10,0'))

# Hard: tight alternating holes with stairs, fences, and trapdoors creating awkward jump timing.
Save-Schem (Join-Path $wallRoot 'hard\Wall-H1.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSCSS..SSSSSS','S..SFS..S..S.S','SSSSSSCCSSCSSS','..S..T..S..S..','SSSSS..SSSSSS.') @('1,2','5,2','9,2','2,0','10,0'))
Save-Schem (Join-Path $wallRoot 'hard\Wall-H2.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSS..SSSSSS..','S..S..S..F.S..','SSCSSSSS..SSSS','S..S..T...S..S','SSSSSS..SSSSSS') @('1,2','6,2','11,2','2,0','10,0'))
Save-Schem (Join-Path $wallRoot 'hard\Wall-H3.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSSSS..SSSS..','S..S.S..S..S.S','SSSS.SCCSSSSSS','..S..F..S..T..','SSSSS..SSSSSS.') @('1,2','5,2','10,2','2,0','10,0'))
Save-Schem (Join-Path $wallRoot 'hard\Wall-H4.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SS..SSSSSS..SS','S...S..F.S..S.','SSSSSCCSSSSSS.','..S..T..S..S..','SSSSSS..SSSSSS') @('2,2','6,2','10,2','2,0','10,0'))

# Very hard: dense final-platform walls; the passable route is narrow, offset, and changes height.
Save-Schem (Join-Path $wallRoot 'very_hard\Wall-VH1.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSSSS..SSSSSS','S..SFS..S..S.S','SSSSSSCCSSSSSS','S..S.T..S..S..','SSSSSS..SSSSSS') @('1,2','5,2','9,2','2,0','10,0'))
Save-Schem (Join-Path $wallRoot 'very_hard\Wall-VH2.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSS..SSSSSSSS','S..S..S..F...S','SSCSSSSSSSSCSS','S..S..T..S...S','SSSSSS..SSSSSS') @('1,2','6,2','11,2','2,0','10,0'))
Save-Schem (Join-Path $wallRoot 'very_hard\Wall-VH3.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSSSSSS..SSSS','S..S....S..S.S','SSSSSCCSSSSSSS','..S..F..S..T..','SSSSSS..SSSSSS') @('1,2','5,2','10,2','2,0','10,0'))
Save-Schem (Join-Path $wallRoot 'very_hard\Wall-VH4.schem') 14 5 2 @(-6,0,-1) (New-ExplicitWall @('SSSS..SSSSSSSS','S...S.F.S..S.S','SSSSSCCSSSSSSS','..S..T..S..S..','SSSSSS..SSSSSS') @('2,2','6,2','10,2','2,0','10,0'))
