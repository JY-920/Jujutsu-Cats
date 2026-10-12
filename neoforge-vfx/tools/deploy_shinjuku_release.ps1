$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskReceipt = Get-Content -LiteralPath (Join-Path $taskRoot 'build\release-067-package.json') -Raw | ConvertFrom-Json
$taskPrevious = Get-Content -LiteralPath (Join-Path $taskRoot 'build\release-066-install.json') -Raw | ConvertFrom-Json
$taskMods = [IO.Path]::GetFullPath('D:\PCL2\PCL\.minecraft\versions\1.21.1-NeoForge模组开发\mods')
$taskOld = Join-Path $taskMods 'jujutsu-vfx-0.66.0-neoforge-1.21.1.jar'
$taskNew = Join-Path $taskMods 'jujutsu-vfx-0.67.0-neoforge-1.21.1.jar'
$taskPending = $taskNew + '.pending'
$taskBackup = $taskOld + '.disabled-before-067-shinjuku-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
foreach ($taskTarget in @($taskOld, $taskNew, $taskPending, $taskBackup)) {
    if (-not ([IO.Path]::GetFullPath($taskTarget).StartsWith($taskMods + '\', [StringComparison]::OrdinalIgnoreCase))) { throw 'Deployment target escaped instance mods directory' }
}
$taskActive = @(Get-ChildItem -LiteralPath $taskMods -Filter 'jujutsu*.jar' -File)
if ($taskActive.Count -ne 1 -or $taskActive[0].FullName -ne $taskOld) { throw 'Installed version changed; rebase before deploying' }
if ((Get-FileHash -LiteralPath $taskOld -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskReceipt.baselineSha256) { throw 'Installed baseline hash changed' }
if ((Get-FileHash -LiteralPath $taskReceipt.output -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskReceipt.sha256) { throw 'Packaged artifact hash changed' }
if ((Test-Path -LiteralPath $taskNew) -or (Test-Path -LiteralPath $taskPending) -or (Test-Path -LiteralPath $taskBackup)) { throw 'Destination already exists' }
Copy-Item -LiteralPath $taskReceipt.output -Destination $taskPending
if ((Get-FileHash -LiteralPath $taskPending -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskReceipt.sha256) { throw 'Staged copy hash mismatch' }
Move-Item -LiteralPath $taskOld -Destination $taskBackup
try {
    Move-Item -LiteralPath $taskPending -Destination $taskNew
} catch {
    if (-not (Test-Path -LiteralPath $taskOld) -and -not (Test-Path -LiteralPath $taskNew)) { Move-Item -LiteralPath $taskBackup -Destination $taskOld }
    throw
}
if ((Get-FileHash -LiteralPath $taskNew -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskReceipt.sha256) { throw 'Final installed hash mismatch' }
$taskResult = [ordered]@{
    version = '0.67.0'
    installed = $taskNew
    sha256 = $taskReceipt.sha256
    bytes = (Get-Item -LiteralPath $taskNew).Length
    previousBackup = $taskBackup
    previousSha256 = $taskReceipt.baselineSha256
    activeJujutsuJars = @(Get-ChildItem -LiteralPath $taskMods -Filter 'jujutsu*.jar' -File | ForEach-Object Name)
    installedAt = (Get-Date -Format 'o')
    gameStarted = $false
}
$taskResult | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRoot 'build\release-067-install.json') -Encoding utf8
$taskResult | ConvertTo-Json -Depth 5
