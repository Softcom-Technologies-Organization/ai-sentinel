<#
.SYNOPSIS
  Creates or rotates a 32-byte KEK (base64) stored as a single user environment variable.
  Variables:
    SENTINELLE_ENCRYPTION_KEY
    SENTINELLE_ENCRYPTION_KEY_CREATED_AT

.EXAMPLES
  .\set-kek.ps1
  .\set-kek.ps1 -Rotate
#>

param(
  [switch]$Rotate  # if present, rotates without prompt
)

$Name = 'SENTINELLE_ENCRYPTION_KEY'

function New-Base64Key32 {
  $bytes = New-Object byte[] 32
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  [Convert]::ToBase64String($bytes)
}

# --- Check existing key ----------------------------------------------------
$current = [Environment]::GetEnvironmentVariable($Name, 'User')
if ($current -and -not $Rotate) {
  Write-Warning "Environment variable '$Name' already exists."
  $answer = Read-Host "Do you want to ROTATE (generate a new key and overwrite it)? (y/N)"
  if ($answer -notmatch '^(y|yes)$') {
    Write-Host "Operation cancelled."
    exit 0
  }
}

# --- Generate and set new key ----------------------------------------------
$kek = New-Base64Key32
$now = (Get-Date).ToString('s')

[Environment]::SetEnvironmentVariable($Name, $kek, 'User')
[Environment]::SetEnvironmentVariable("${Name}_CREATED_AT", $now, 'User')

# --- Summary ----------------------------------------------------------------
Write-Host ""
Write-Host "User encryption key set successfully:"
Write-Host "  $Name = <base64 32B>"
Write-Host "  ${Name}_CREATED_AT = $now"
Write-Host ""
Write-Host "Note: Restart your apps or shells to see updated environment variables."
