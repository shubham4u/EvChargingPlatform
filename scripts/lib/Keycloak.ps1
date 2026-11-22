# Keycloak helpers for local scripts. Dot-source this file: . "$PSScriptRoot\lib\Keycloak.ps1"
# Works on Windows PowerShell 5.1 and PowerShell 7.

$script:KeycloakUrl = "http://localhost:8081"
$script:Realm = "ev-charging"
$script:ClientId = "ev-cli"

# The admin password comes from $env:KEYCLOAK_ADMIN_PASSWORD, or from KEYCLOAK_ADMIN_PASSWORD in the repo's .env.
function Get-KeycloakAdminPassword {
    if ($env:KEYCLOAK_ADMIN_PASSWORD) { return $env:KEYCLOAK_ADMIN_PASSWORD }
    $envFile = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) ".env"
    if (Test-Path $envFile) {
        $line = Get-Content $envFile | Where-Object { $_ -match '^\s*KEYCLOAK_ADMIN_PASSWORD\s*=' } | Select-Object -First 1
        if ($line) { return ($line -split '=', 2)[1].Trim().Trim('"') }
    }
    throw "KEYCLOAK_ADMIN_PASSWORD is not set. Add it to .env (see .env.example) or set `$env:KEYCLOAK_ADMIN_PASSWORD."
}

function Get-KeycloakAdminToken {
    $body = @{ grant_type = "password"; client_id = "admin-cli"; username = "admin"; password = (Get-KeycloakAdminPassword) }
    (Invoke-RestMethod -Method Post -Uri "$script:KeycloakUrl/realms/master/protocol/openid-connect/token" -Body $body).access_token
}

# Creates an enabled, email-verified user with a permanent password and returns its id (the token `sub`).
# Every account gets the `driver` role by default; pass -Operator to add `operator`.
function New-KeycloakUser {
    param(
        [Parameter(Mandatory)] [string]$Username,
        [Parameter(Mandatory)] [string]$Email,
        [Parameter(Mandatory)] [string]$Password,
        [string]$FirstName = $Username,
        [string]$LastName = "Tester",
        [switch]$Operator
    )
    $headers = @{ Authorization = "Bearer $(Get-KeycloakAdminToken)" }
    $user = @{
        username = $Username; email = $Email; firstName = $FirstName; lastName = $LastName
        enabled = $true; emailVerified = $true
        credentials = @(@{ type = "password"; value = $Password; temporary = $false })
    } | ConvertTo-Json -Depth 4
    Invoke-RestMethod -Method Post -Uri "$script:KeycloakUrl/admin/realms/$script:Realm/users" -Headers $headers `
        -ContentType "application/json" -Body $user | Out-Null
    $id = (Invoke-RestMethod -Uri "$script:KeycloakUrl/admin/realms/$script:Realm/users?username=$Username&exact=true" -Headers $headers)[0].id
    if ($Operator) {
        $role = Invoke-RestMethod -Uri "$script:KeycloakUrl/admin/realms/$script:Realm/roles/operator" -Headers $headers
        Invoke-RestMethod -Method Post -Uri "$script:KeycloakUrl/admin/realms/$script:Realm/users/$id/role-mappings/realm" `
            -Headers $headers -ContentType "application/json" -Body (ConvertTo-Json @(@{ id = $role.id; name = $role.name }) -Depth 3) | Out-Null
    }
    return $id
}

# Signs in with username + password (direct access grant on the dev-only ev-cli client) and returns the access token.
function Get-AccessToken {
    param([Parameter(Mandatory)] [string]$Username, [Parameter(Mandatory)] [string]$Password)
    $body = @{ grant_type = "password"; client_id = $script:ClientId; username = $Username; password = $Password; scope = "openid" }
    (Invoke-RestMethod -Method Post -Uri "$script:KeycloakUrl/realms/$script:Realm/protocol/openid-connect/token" -Body $body).access_token
}

# Decodes a JWT payload for display. Does not verify the signature; the API does that.
function ConvertFrom-Jwt {
    param([Parameter(Mandatory)] [string]$Token)
    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) { 2 { $payload += '==' } 3 { $payload += '=' } }
    [System.Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
}
