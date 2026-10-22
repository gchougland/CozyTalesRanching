# Call one Hytale MCP tool from PowerShell without quoting trouble.
#   .\tools\hmcp.ps1 model_render '{"root":"project","path":"..."}' -Out C:\tmp\pic.png
#   .\tools\hmcp.ps1 --list
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Tool,
    [Parameter(Position = 1)][string]$Json = '{}',
    [string]$Out
)
$mcp = 'C:\Users\gchou\OneDrive\Documents\Hytale-Modding\HytaleMCP'
if ($Tool -eq '--list') { Push-Location $mcp; node scripts/call-tool.mjs --list; Pop-Location; exit $LASTEXITCODE }
$tmp = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($tmp, $Json, (New-Object System.Text.UTF8Encoding($false)))
Push-Location $mcp
try {
    if ($Out) { node scripts/call-tool.mjs $Tool "@$tmp" --out $Out } else { node scripts/call-tool.mjs $Tool "@$tmp" }
    $code = $LASTEXITCODE
} finally { Pop-Location; Remove-Item $tmp -ErrorAction SilentlyContinue }
exit $code
