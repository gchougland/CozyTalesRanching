# Renders every rare variant beside its normal ranch model and joins them into side-by-side pictures.
#   .\notes\pictures\wildlife\render.ps1        (from the worktree root, after recolour.py)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$here = $PSScriptRoot
$proj = 'CozyTales-Ranching'
$models = "$proj/src/main/resources/Server/Models/CozyRanch"
$tmp = Join-Path $env:TEMP 'wildlife-render'
New-Item -ItemType Directory -Force $tmp | Out-Null
$steps = @()
foreach ($pair in @(@('Chicken', 'Chicken_Golden'), @('Cow', 'Cow_Red'), @('Pig', 'Pig_Spotted'), @('Frog', 'Frog_Purple'))) {
    foreach ($view in 'three_quarter', 'back_three_quarter') {
        $steps += @{ tool = 'model_render'; args = @{ root = 'projects'; path = "$models/CozyRanch_$($pair[0]).json"; views = @($view); size = 360 }; out = "$tmp/$($pair[0])_$view.png"; maxChars = 200 }
        $steps += @{ tool = 'model_render'; args = @{ root = 'projects'; path = "$models/Rare/CozyRanch_$($pair[1]).json"; views = @($view); size = 360 }; out = "$tmp/$($pair[1])_$view.png"; maxChars = 200 }
    }
}
foreach ($v in 'Normal', 'Black') {
    foreach ($view in 'three_quarter', 'back_three_quarter') {
        $steps += @{ tool = 'model_render'; args = @{ root = 'projects'; path = "$proj/build/wildlife-woolcheck/Sheep_$v.blockymodel"; texture = @{ root = 'projects'; path = "$proj/build/wildlife-woolcheck/Sheep_$v.png" }; views = @($view); size = 360 }; out = "$tmp/Sheep_$($v)_$view.png"; maxChars = 200 }
    }
}
$json = Join-Path $tmp 'steps.json'
[System.IO.File]::WriteAllText($json, ($steps | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))
Push-Location $root
try { node tools/mcp-session.mjs $json | Select-String -Pattern 'picture written|error|Error' } finally { Pop-Location }
python (Join-Path $here 'compose.py') $tmp $here
