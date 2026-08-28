$ErrorActionPreference = 'Stop'
# Generate dev fixtures: deterministic demo payload hashes + signed catalog/manifest.
# NOTE: keep this script pure ASCII. Non-ASCII values are emitted as JSON unicode
# escapes (e.g. "\\u4e2d\\u6587"), so any PowerShell codepage decodes the script the same way.
# Usage: powershell -ExecutionPolicy Bypass -File backend\fixtures\generate.ps1
$backend = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $backend 'build-tools'
$fixtures = Join-Path $backend 'fixtures'
$keysDir = Join-Path $backend 'signing\keys'
$keyId = 'release-2026-01-dev'

New-Item -ItemType Directory -Force -Path $tools, $fixtures, $keysDir | Out-Null

& javac -encoding UTF-8 -d $tools `
    (Join-Path $backend 'signing\ManifestSigner.java') `
    (Join-Path $backend 'fixture-server\DemoPayload.java') `
    (Join-Path $backend 'fixture-server\FixtureServer.java')
if ($LASTEXITCODE -ne 0) { exit 1 }

if (-not (Test-Path (Join-Path $keysDir "$keyId.pub"))) {
    & java -cp $tools ManifestSigner keygen $keyId $keysDir
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

function Get-Hash($id, $arch, $name, $size) {
    (& java -cp $tools FixtureServer hash $id $arch $name $size).Trim()
}

# Language labels as JSON unicode escapes (Chinese/English/Multilingual).
$langZh = '["\u4e2d\u6587","\u591a\u8bed\u8a00"]'
$langEn = '["\u82f1\u6587"]'
$langMulti = '["\u591a\u8bed\u8a00"]'
$langZhEn = '["\u4e2d\u6587","\u82f1\u6587"]'

$models = @(
    @{ id = 'qwen3-4b';       arch = 'qwen3';   name = 'Qwen3-4B-Instruct';           size = 6291456; publisher = 'Qwen';          spdx = 'Apache-2.0'; params = 4000000000; quant = 'Q4_K_M'; ctx = 32768; ver = '2026.08.1'; task = 'text';      langs = $langZh;     status = 'demo' }
    @{ id = 'llama3.2-3b';    arch = 'llama';   name = 'Llama-3.2-3B-Instruct';       size = 5242880; publisher = 'Meta';          spdx = 'Llama 3.2 Community'; params = 3000000000; quant = 'Q4_K_M'; ctx = 131072; ver = '2026.08.1'; task = 'text';      langs = $langEn;     status = 'demo' }
    @{ id = 'ds-r1-1.5b';     arch = 'qwen2';   name = 'DeepSeek-R1-Distill-1.5B';    size = 4194304; publisher = 'DeepSeek';      spdx = 'MIT'; params = 1500000000; quant = 'Q4_K_M'; ctx = 32768; ver = '2026.08.1'; task = 'text';      langs = $langZhEn;   status = 'demo' }
    @{ id = 'qwen-coder-1.5b'; arch = 'qwen2';  name = 'Qwen2.5-Coder-1.5B-Instruct'; size = 3145728; publisher = 'Qwen';          spdx = 'Apache-2.0'; params = 1500000000; quant = 'Q4_K_M'; ctx = 16384; ver = '2026.08.1'; task = 'code';      langs = $langMulti;  status = 'demo' }
    @{ id = 'gemma-3-1b';     arch = 'gemma3';  name = 'Gemma-3-1B-IT';               size = 2097152; publisher = 'Google';        spdx = 'Gemma Terms of Use'; params = 1000000000; quant = 'Q4_0'; ctx = 32768; ver = '2026.08.1'; task = 'text';      langs = $langMulti;  status = 'demo' }
    @{ id = 'phi-3.5-mini';   arch = 'phi3';    name = 'Phi-3.5-mini-instruct';       size = 5242880; publisher = 'Microsoft';     spdx = 'MIT'; params = 3800000000; quant = 'Q4_K_M'; ctx = 131072; ver = '2026.08.1'; task = 'text';      langs = $langMulti;  status = 'demo' }
    @{ id = 'yi-1.5-9b';      arch = 'yi';      name = 'Yi-1.5-9B-Chat';              size = 7340032; publisher = '01.AI';         spdx = 'Apache-2.0'; params = 9000000000; quant = 'Q4_K_M'; ctx = 32768; ver = '2026.08.1'; task = 'text';      langs = $langZhEn;   status = 'demo' }
    @{ id = 'smollm2-360m';   arch = 'llama';   name = 'SmolLM2-360M-Instruct';       size = 1048576; publisher = 'HuggingFaceTB'; spdx = 'Apache-2.0'; params = 360000000; quant = 'Q8_0'; ctx = 8192; ver = '2026.08.1'; task = 'text';      langs = $langEn;     status = 'demo' }
    @{ id = 'smollm-135m-instruct'; arch = 'llama'; name = 'SmolLM-135M-Instruct';    size = 105453984; publisher = 'HuggingFaceTB'; spdx = 'Apache-2.0'; params = 135000000; quant = 'Q4_K_M'; ctx = 2048; ver = '2026.08.1'; task = 'text'; langs = $langEn; status = 'approved' }
)

$catalogEntries = @()
foreach ($m in $models) {
    if ($m.status -eq 'approved') {
        # Approved model: real GGUF weights (registered in qa/fixtures/approved-model.json).
        $file = "SmolLM-135M-Instruct-Q4_K_M.gguf"
        $sha = "30a78b6b6b6130a32a704f32ad589e69790ac4e2772bf43dbd2ebdbe0b52788a"
        $desc = "Approved evaluation model (real GGUF weights, Apache-2.0, HuggingFaceTB)."
    } else {
        $file = "model-q4_k_m.gguf"
        $sha = Get-Hash $m.id $m.arch $m.name $m.size
        $desc = "Dev fixture payload (not real weights) for catalog/download integration testing."
    }
    $base = "/v1/models/$($m.id)/$($m.ver)"
    $manifest = @"
{
  "modelId": "$($m.id)",
  "version": "$($m.ver)",
  "displayName": "$($m.name)",
  "source": { "publisher": "$($m.publisher)", "url": "https://huggingface.co/$($m.publisher)" },
  "license": { "spdx": "$($m.spdx)", "url": "https://huggingface.co/$($m.publisher)" },
  "format": "gguf",
  "architecture": "$($m.arch)",
  "parameterCount": $($m.params),
  "quantization": "$($m.quant)",
  "contextLength": $($m.ctx),
  "description": "$desc",
  "tasks": ["$($m.task)"],
  "languages": $($m.langs),
  "weightStatus": "$($m.status)",
  "chatTemplate": "chatml",
  "updatedAt": "2026-08-23",
  "files": [
    { "name": "$file", "sizeBytes": $($m.size), "sha256": "$sha", "urls": ["$base/$file"] }
  ],
  "runtime": { "minAndroidApi": 26, "abis": ["arm64-v8a"], "backends": ["cpu"] }
}
"@
    if ($m.status -eq 'approved') { Write-Host "approved manifest: name=$file size=$($m.size) sha=$sha" }
    $dir = Join-Path $fixtures "models\$($m.id)\$($m.ver)"
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $dir 'manifest.json'), $manifest, (New-Object System.Text.UTF8Encoding($false)))
    & java -cp $tools ManifestSigner sign $keyId $keysDir (Join-Path $dir 'manifest.json') (Join-Path $dir 'manifest.sig')
    if ($LASTEXITCODE -ne 0) { exit 1 }
    $catalogEntries += "    { `"modelId`": `"$($m.id)`", `"version`": `"$($m.ver)`", `"displayName`": `"$($m.name)`", `"description`": `"$desc`" }"
}

$catalog = @"
{
  "schemaVersion": 1,
  "generatedAt": "2026-08-23T03:48:00Z",
  "models": [
$($catalogEntries -join ",`n")
  ]
}
"@
[System.IO.File]::WriteAllText((Join-Path $fixtures 'catalog.json'), $catalog, (New-Object System.Text.UTF8Encoding($false)))
& java -cp $tools ManifestSigner sign $keyId $keysDir (Join-Path $fixtures 'catalog.json') (Join-Path $fixtures 'catalog.sig')
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host "fixtures generated under $fixtures"
Write-Host "public key (embed into app TrustedKeys):"
Get-Content (Join-Path $keysDir "$keyId.pub")
