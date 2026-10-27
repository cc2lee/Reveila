### Step 1: Export Installed Extensions from Existing VS Code

Open **PowerShell** (or Command Prompt) on your current machine. Run the following command to query the official VS Code CLI and export all active extension IDs to a clean text file on your Desktop:

```powershell
code --list-extensions | Out-File -FilePath "$HOME\Desktop\vscode_extensions.txt" -Encoding utf8

```

*(If you are using Command Prompt rather than PowerShell, the equivalent command is `code --list-extensions > "%USERPROFILE%\Desktop\vscode_extensions.txt"`).*

---

### Step 2: (Optional) Back Up Custom Settings, Keybindings, and Snippets

If you also want your custom editor configuration alongside your extensions, run this command in PowerShell to copy your core JSON configuration files into the same backup folder:

```powershell
$backupDir = "$HOME\Desktop\VSCode_Config_Backup"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null

# Copy settings, keybindings, and snippets
Copy-Item "$env:APPDATA\Code\User\settings.json" -Destination $backupDir -Force -ErrorAction SilentlyContinue
Copy-Item "$env:APPDATA\Code\User\keybindings.json" -Destination $backupDir -Force -ErrorAction SilentlyContinue
Copy-Item "$env:APPDATA\Code\User\snippets" -Destination $backupDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "Config files backed up to: $backupDir" -ForegroundColor Green

```

---

### Step 3: Install and Verify the New VS Code Instance

1. Install the fresh instance of Visual Studio Code.
2. Ensure the `code` executable is available in your system path by opening a new terminal window and running:
```powershell
code --version

```


*If it reports a version number, the CLI is ready.*

---

### Step 4: Restore All Extensions from the File

Place `vscode_extensions.txt` on your Desktop (or navigate to its directory) and run this restore script in **PowerShell**:

```powershell
$listFile = "$HOME\Desktop\vscode_extensions.txt"

if (Test-Path $listFile) {
    Get-Content $listFile | ForEach-Object {
        $ext = $_.Trim()
        if ($ext -ne "") {
            Write-Host "Installing extension: $ext" -ForegroundColor Cyan
            code --install-extension $ext --force
        }
    }
    Write-Host "All extensions successfully restored!" -ForegroundColor Green
} else {
    Write-Warning "File not found: $listFile"
}

```

*The `--force` flag ensures the installer proceeds without pausing if an extension has pending updates or minor version bumps.*

---

### Step 5: (Optional) Restore Configuration Files

If you backed up your configuration in Step 2, restore them to the newly installed user profile:

```powershell
$backupDir = "$HOME\Desktop\VSCode_Config_Backup"
$targetDir = "$env:APPDATA\Code\User"

if (Test-Path $backupDir) {
    Copy-Item "$backupDir\settings.json" -Destination $targetDir -Force -ErrorAction SilentlyContinue
    Copy-Item "$backupDir\keybindings.json" -Destination $targetDir -Force -ErrorAction SilentlyContinue
    Copy-Item "$backupDir\snippets" -Destination $targetDir -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "User settings and keybindings restored." -ForegroundColor Green
}

```

---

For a quick visual demonstration of executing extension installations via terminal commands, check out this guide on [Installing VS Code Extensions via CLI](https://www.youtube.com/watch?v=TbPo1LAIyqA).

---

### Standalone PowerShell Script

Here is a unified, standalone PowerShell script (`Manage-VSCodeExtensions.ps1`) and a clean Windows batch wrapper (`Manage-VSCodeExtensions.bat`) that combine both the export and restore workflows with interactive menus and automatic path verification.

---

### Option 1: Standalone PowerShell Script (`Manage-VSCodeExtensions.ps1`)

Save the code below as `Manage-VSCodeExtensions.ps1` (e.g., in a tools folder or on your Desktop):

```powershell
<#
.SYNOPSIS
    Automated Export and Restore utility for VS Code Extensions and User Configuration.
#>

param(
    [ValidateSet('Export', 'Restore', 'Menu')]
    [string]$Action = 'Menu'
)

$BackupFolder = "$HOME\Desktop\VSCode_Migration_Package"
$ExtensionsFile = "$BackupFolder\vscode_extensions.txt"
$SettingsSource = "$env:APPDATA\Code\User"
$ConfigBackupFolder = "$BackupFolder\UserConfig"

function Test-VSCodeCLI {
    $codeCmd = Get-Command code -ErrorAction SilentlyContinue
    if (-not $codeCmd) {
        Write-Error "VS Code command-line tool ('code') not found in PATH. Ensure VS Code is installed and added to environment variables."
        return $false
    }
    return $true
}

function Export-VSCodeData {
    if (-not (Test-VSCodeCLI)) { return }

    Write-Host "`n[+] Initializing VS Code Export..." -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $BackupFolder | Out-Null
    New-Item -ItemType Directory -Force -Path $ConfigBackupFolder | Out-Null

    # 1. Export extensions
    Write-Host "[1/3] Exporting active extensions list..." -ForegroundColor Yellow
    code --list-extensions | Out-File -FilePath $ExtensionsFile -Encoding utf8
    $count = (Get-Content $ExtensionsFile | Where-Object { $_.Trim() -ne "" }).Count
    Write-Host "      Successfully exported $count extensions to: $ExtensionsFile" -ForegroundColor Green

    # 2. Export user settings & keybindings
    Write-Host "[2/3] Backing up configuration files (settings, keybindings, snippets)..." -ForegroundColor Yellow
    if (Test-Path "$SettingsSource\settings.json") {
        Copy-Item "$SettingsSource\settings.json" -Destination $ConfigBackupFolder -Force
    }
    if (Test-Path "$SettingsSource\keybindings.json") {
        Copy-Item "$SettingsSource\keybindings.json" -Destination $ConfigBackupFolder -Force
    }
    if (Test-Path "$SettingsSource\snippets") {
        Copy-Item "$SettingsSource\snippets" -Destination $ConfigBackupFolder -Recurse -Force
    }

    # 3. Create a quick-restore runner script inside the backup bundle
    Write-Host "[3/3] Creating standalone portable restore script..." -ForegroundColor Yellow
    $portableRunner = @"
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& { & '%~dp0..\Manage-VSCodeExtensions.ps1' -Action Restore }"
pause
"@
    Set-Content -Path "$BackupFolder\Run_Restore.bat" -Value $portableRunner

    Write-Host "`n[✓] Export Complete! Everything is packaged at:" -ForegroundColor Green
    Write-Host "    $BackupFolder" -ForegroundColor White
}

function Restore-VSCodeData {
    if (-not (Test-VSCodeCLI)) { return }

    if (-not (Test-Path $ExtensionsFile)) {
        Write-Error "Extensions list file not found at: $ExtensionsFile"
        return
    }

    Write-Host "`n[+] Initializing VS Code Restore..." -ForegroundColor Cyan

    # 1. Restore Extensions
    $extensions = Get-Content $ExtensionsFile | Where-Object { $_.Trim() -ne "" }
    $total = $extensions.Count
    $current = 0

    Write-Host "[1/2] Installing $total extensions via VS Code CLI..." -ForegroundColor Yellow
    foreach ($ext in $extensions) {
        $current++
        Write-Host "      [$current/$total] Installing: $ext" -ForegroundColor Gray
        code --install-extension $ext --force | Out-Null
    }
    Write-Host "      All extensions restored successfully." -ForegroundColor Green

    # 2. Restore User Configuration
    Write-Host "[2/2] Restoring configuration files..." -ForegroundColor Yellow
    if (Test-Path $ConfigBackupFolder) {
        New-Item -ItemType Directory -Force -Path $SettingsSource | Out-Null
        if (Test-Path "$ConfigBackupFolder\settings.json") {
            Copy-Item "$ConfigBackupFolder\settings.json" -Destination "$SettingsSource\settings.json" -Force
        }
        if (Test-Path "$ConfigBackupFolder\keybindings.json") {
            Copy-Item "$ConfigBackupFolder\keybindings.json" -Destination "$SettingsSource\keybindings.json" -Force
        }
        if (Test-Path "$ConfigBackupFolder\snippets") {
            Copy-Item "$ConfigBackupFolder\snippets" -Destination $SettingsSource -Recurse -Force
        }
        Write-Host "      Configuration files restored to: $SettingsSource" -ForegroundColor Green
    }

    Write-Host "`n[✓] Restore process finished successfully!" -ForegroundColor Green
}

# Main routing
switch ($Action) {
    'Export'  { Export-VSCodeData }
    'Restore' { Restore-VSCodeData }
    'Menu'    {
        Clear-Host
        Write-Host "=============================================" -ForegroundColor Cyan
        Write-Host "   VS Code Migration & Extension Manager     " -ForegroundColor White
        Write-Host "=============================================" -ForegroundColor Cyan
        Write-Host "1. Export Extensions & Settings to Backup"
        Write-Host "2. Restore Extensions & Settings from Backup"
        Write-Host "3. Exit"
        $choice = Read-Host "`nSelect an option (1-3)"

        switch ($choice) {
            '1' { Export-VSCodeData }
            '2' { Restore-VSCodeData }
            default { Write-Host "Exiting." -ForegroundColor Gray }
        }
    }
}

```

---

### Option 2: Double-Clickable Batch File (`Manage-VSCodeExtensions.bat`)

If you want a one-click launcher from Windows Explorer without launching a PowerShell terminal first, save this snippet as `Manage-VSCodeExtensions.bat` in the same directory:

```batch
@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Manage-VSCodeExtensions.ps1"
pause

```

---

### How to Use

1. **On your existing setup:**
* Run `Manage-VSCodeExtensions.bat` and select `1` (Export).
* A folder named `VSCode_Migration_Package` will be created on your Desktop containing:
* `vscode_extensions.txt` (the list of all extension IDs)
* `UserConfig/` (your `settings.json`, `keybindings.json`, and snippets)
* `Run_Restore.bat` (automated restore launcher)




2. **On your new installation:**
* Install standard Visual Studio Code.
* Copy the `VSCode_Migration_Package` folder (and the `.ps1` script) to the new machine's Desktop.
* Run `Manage-VSCodeExtensions.bat` and select `2` (Restore), or double-click `Run_Restore.bat` inside the package folder.