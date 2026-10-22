# Render a models-a asset (path relative to src/main/resources) to a picture.
#   .\render.ps1 Server/Item/Items/CozyRanch/Houses/CozyRanch_House_ChickenCoop.json C:\temp\coop.png [views] [size]
param([string]$Rel, [string]$Out, [string]$Views = '"three_quarter","front","right","back","left"', [int]$Size = 512, [string]$Extra = '')
$tools = 'C:\Users\gchou\OneDrive\Documents\Hytale-Modding\_worktrees\CozyTales-Ranching-models-a\tools\hmcp.ps1'
$p = "_worktrees/CozyTales-Ranching-models-a/src/main/resources/$Rel"
$json = "{`"root`":`"projects`",`"path`":`"$p`",`"views`":[$Views],`"size`":$Size,`"grid`":true,`"hitbox`":true$Extra}"
& $tools model_render $json -Out $Out 2>&1 | Select-String -Pattern 'notes|Faces|rror|hitbox|sizeBlocks' -Context 0,4
