You can find the exact path to your PowerShell profile by running this command in your terminal:

```powershell
$PROFILE
```

Typically, it is stored in one of these two locations depending on which version of PowerShell you are using:
*   **PowerShell 7+**: `C:\Users\<YourUser>\Documents\PowerShell\Microsoft.PowerShell_profile.ps1`
*   **Windows PowerShell 5.1**: `C:\Users\<YourUser>\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1`

### Quick Steps to Edit:
1.  **Open it in Notepad**: 
    ```powershell
    notepad $PROFILE
    ```
    *(If it says the file doesn't exist, create it first with: `New-Item -Path $PROFILE -Type File -Force`)*
2.  **Paste your aliases** (from the [**Phase 1: Preparation**](memory-bank/Demo.md) section).
3.  **Save and Refresh**: Close Notepad and run:
    ```powershell
    . $PROFILE
    ```

Now your custom commands like `Reset-Reveila` and `Watch-Fabric` will be available every time you open a new PowerShell window!