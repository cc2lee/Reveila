#!/usr/bin/env pwsh
$ErrorActionPreference = 'Stop'

# Maximum allowed file size in bytes (50 MB)
$MaxSizeBytes = 50 * 1024 * 1024
$MaxSizeMB    = [math]::Round($MaxSizeBytes / 1MB, 0)

# Hard-blocked extensions regardless of size
$BlockedExtensions = @('.gguf', '.ggml', '.safetensors', '.a', '.so', '.dylib', '.dll')

# Query staged files (Added, Copied, Modified, Renamed) using NUL-separated output
$rawOutput = git diff --cached --name-only --diff-filter=ACMR -z
if ([string]::IsNullOrEmpty($rawOutput)) {
    exit 0
}

$stagedFiles = $rawOutput -split "`0" | Where-Object { $_ -ne '' }
$violations  = @()

foreach ($file in $stagedFiles) {
    # Inspect the staged blob size directly from the Git index (accurate even if working tree differs)
    $blobSizeRaw = git cat-file -s ":$file" 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($blobSizeRaw)) {
        continue
    }

    $sizeBytes = [int64]$blobSizeRaw
    $ext       = [System.IO.Path]::GetExtension($file).ToLowerInvariant()

    if ($sizeBytes -gt $MaxSizeBytes) {
        $sizeMB = [math]::Round($sizeBytes / 1MB, 2)
        $violations += [PSCustomObject]@{
            File   = $file
            Reason = "Exceeds ${MaxSizeMB} MB limit (${sizeMB} MB)"
        }
    }
    elseif ($BlockedExtensions -contains $ext) {
        $sizeMB = [math]::Round($sizeBytes / 1MB, 2)
        $violations += [PSCustomObject]@{
            File   = $file
            Reason = "Blocked binary/model extension '$ext' (${sizeMB} MB)"
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Host "`n[Reveila Pre-Commit Guard] Commit blocked due to oversized or restricted artifacts:" -ForegroundColor Red
    foreach ($v in $violations) {
        Write-Host "  - $($v.File) -> $($v.Reason)" -ForegroundColor Yellow
    }
    Write-Host "`nFix: Run 'git reset HEAD <file>' and ensure the path is excluded in .gitignore." -ForegroundColor Cyan
    Write-Host "Emergency bypass (not recommended): git commit --no-verify`n" -ForegroundColor DarkGray
    exit 1
}

exit 0