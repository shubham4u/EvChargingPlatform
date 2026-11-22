<#
.SYNOPSIS
  Creates a Keycloak account for manual testing and signs it in.

.EXAMPLE
  $alice = .\scripts\new-user.ps1 -Name Alice
  $olivia = .\scripts\new-user.ps1 -Name Olivia -Operator
  Invoke-RestMethod -Method Post http://localhost:8080/users/me -Headers @{ Authorization = "Bearer $($alice.Token)" }

.DESCRIPTION
  Returns an object with Id (the token subject), Username, Password and Token. Tokens expire after
  15 minutes; get a new one with .\scripts\get-token.ps1 -Username $alice.Username -Password $alice.Password.
  Needs KEYCLOAK_ADMIN_PASSWORD in .env (or $env:KEYCLOAK_ADMIN_PASSWORD).
#>
param(
    [Parameter(Mandatory)] [string]$Name,
    [switch]$Operator
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\lib\Keycloak.ps1"

$username = "$($Name.ToLower())-$([guid]::NewGuid().ToString('N').Substring(0, 6))"
$password = [guid]::NewGuid().ToString()
$id = New-KeycloakUser -Username $username -Email "$username@example.com" -Password $password -FirstName $Name -Operator:$Operator

[pscustomobject]@{
    Id       = $id
    Username = $username
    Password = $password
    Roles    = if ($Operator) { "driver, operator" } else { "driver" }
    Token    = Get-AccessToken -Username $username -Password $password
}
