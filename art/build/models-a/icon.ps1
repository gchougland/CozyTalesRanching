# Item icon: a three-quarter render on a clear background, cropped, padded square, area-averaged to 64 x 64.
#   .\icon.ps1 ChickenCoop
param([string]$House, [int]$Yaw = 35, [int]$Pitch = 28)
$id = "CozyRanch_House_$House"
$tools = 'C:\Users\gchou\OneDrive\Documents\Hytale-Modding\_worktrees\CozyTales-Ranching-models-a\tools\hmcp.ps1'
$res = 'C:\Users\gchou\OneDrive\Documents\Hytale-Modding\_worktrees\CozyTales-Ranching-models-a\src\main\resources'
$raw = "$env:TEMP\$id.icon_raw.png"
$json = "{`"root`":`"projects`",`"path`":`"_worktrees/CozyTales-Ranching-models-a/src/main/resources/Server/Item/Items/CozyRanch/Houses/$id.json`",`"yaw`":$Yaw,`"pitch`":$Pitch,`"size`":512,`"grid`":false,`"hitbox`":false,`"projection`":`"orthographic`",`"background`":`"#00000000`"}"
& $tools model_render $json -Out $raw 2>&1 | Out-Null
$py = @"
from PIL import Image
im = Image.open(r'$raw').convert('RGBA')
im = im.crop(im.getchannel('A').getbbox())
s = int(max(im.size) * 1.06)
sq = Image.new('RGBA', (s, s), (0, 0, 0, 0))
sq.paste(im, ((s - im.width) // 2, (s - im.height) // 2))
sq.resize((64, 64), Image.BOX).save(r'$res\Common\Icons\ItemsGenerated\$id.png')
"@
python -c $py
Write-Output "icon written: $res\Common\Icons\ItemsGenerated\$id.png"
