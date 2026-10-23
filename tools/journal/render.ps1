# Renders each species' ranch model for the journal portraits: .\tools\journal\render.ps1 <outdir>
# The sheep is rendered through tools/animator/woolcheck.py, since the renderer drops its wool attachment.
param([Parameter(Mandatory = $true)][string]$OutDir)
$repo = (Resolve-Path "$PSScriptRoot\..\..").Path
$project = Split-Path -Leaf $repo
$root = if ($project -eq 'CozyTales-Ranching') { $project } else { "_worktrees/$project" }
New-Item -ItemType Directory -Force $OutDir | Out-Null
python "$repo\tools\animator\woolcheck.py" make | Out-Null
try {
    foreach ($s in 'Cow', 'Chicken', 'Pig', 'Sheep', 'Frog') {
        $asset = if ($s -eq 'Sheep') { '_WoolCheck_Sheep' } else { "CozyRanch_$s" }
        $json = '{"root":"projects","path":"' + $root + '/src/main/resources/Server/Models/CozyRanch/' + $asset + '.json","views":["three_quarter"],"size":480,"background":"#00000000","hitbox":false,"grid":false,"animation":"Idle","frame":0}'
        & "$PSScriptRoot\..\hmcp.ps1" model_render $json -Out (Join-Path $OutDir "$s.png") | Out-Null
    }
} finally {
    python "$repo\tools\animator\woolcheck.py" clean | Out-Null
}
