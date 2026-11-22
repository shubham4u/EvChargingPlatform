<#
.SYNOPSIS
  Signs in to the ev-charging realm and prints an access token (valid 15 minutes).

.EXAMPLE
  $token = .\scripts\get-token.ps1 -Username alice-1a2b3c -Password '...'
  Invoke-RestMethod http://localhost:8080/users/me -Headers @{ Authorization = "Bearer $token" }

.EXAMPLE
  .\scripts\get-token.ps1 -Username alice-1a2b3c -Password '...' -Decode   # show the claims instead
#>
param(
    [Parameter(Mandatory)] [string]$Username,
    [Parameter(Mandatory)] [string]$Password,
    [switch]$Decode
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\lib\Keycloak.ps1"

$token = Get-AccessToken -Username $Username -Password $Password
if ($Decode) { ConvertFrom-Jwt $token } else { $token }
