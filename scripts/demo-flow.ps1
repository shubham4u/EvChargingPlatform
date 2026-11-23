<#
.SYNOPSIS
  End-to-end demo of the driver journey with real logins:
  sign up -> profile -> add EV -> operator sets up a station -> reserve -> charge -> stop.

.DESCRIPTION
  Creates three fresh Keycloak accounts each run (drivers Alice and Bob, operator Olivia) with random
  passwords, signs each in to get an access token, then calls the API as them and asserts every step,
  including what each of them is NOT allowed to do. Doubles as a smoke test.

  Needs the stack running and KEYCLOAK_ADMIN_PASSWORD in .env (or $env:KEYCLOAK_ADMIN_PASSWORD):
      docker compose up --build -d
      .\scripts\demo-flow.ps1
  Exit code is 0 when every check passes, 1 otherwise. Works on Windows PowerShell 5.1 and PowerShell 7.
#>
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
. "$PSScriptRoot\lib\Keycloak.ps1"
$script:failures = 0
$script:checks = 0
$suffix = [guid]::NewGuid().ToString("N").Substring(0, 8).ToUpper()

function Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }

function Check($label, $condition) {
    $script:checks++
    if ($condition) { Write-Host "  [PASS] $label" -ForegroundColor Green }
    else { Write-Host "  [FAIL] $label" -ForegroundColor Red; $script:failures++ }
}

function Api($token, $method, $path, $body) {
    $params = @{ Method = $method; Uri = "$BaseUrl$path"; ContentType = "application/json"; Headers = @{} }
    if ($token) { $params.Headers.Authorization = "Bearer $token" }
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Compress) }
    Invoke-RestMethod @params
}

# Calls an endpoint that is expected to fail and returns the HTTP status and problem title.
function ApiError($token, $method, $path, $body) {
    try {
        Api $token $method $path $body | Out-Null
        return @{ Status = 200; Title = "(succeeded)" }
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        # Windows PowerShell 5.1 leaves ErrorDetails empty, so read the problem body from the response stream.
        $raw = if ($_.ErrorDetails -and $_.ErrorDetails.Message) { $_.ErrorDetails.Message } else {
            $stream = $_.Exception.Response.GetResponseStream()
            if ($stream.CanSeek) { $stream.Position = 0 }
            (New-Object System.IO.StreamReader($stream)).ReadToEnd()
        }
        $title = ""
        try { $title = ($raw | ConvertFrom-Json).title } catch { }
        return @{ Status = $status; Title = $title }
    }
}

function ExpectError($label, $expectedStatus, $result) {
    Check "$label -> $expectedStatus $($result.Title)" ($result.Status -eq $expectedStatus)
}

# Windows PowerShell 5.1 emits a JSON array as one pipeline object, so @(...).Count is wrong there.
function Count($items) { return ($items | Measure-Object).Count }

# Availability comes from read models fed by Kafka, so give events a moment to land before failing.
function WaitFor($label, [scriptblock]$condition) {
    for ($i = 0; $i -lt 20; $i++) {
        if (& $condition) { Check $label $true; return }
        Start-Sleep -Milliseconds 500
    }
    Check $label $false
}

function ConnectorStatus($stationId, $connectorId) {
    (Api $null Get "/stations/$stationId/connectors" | ForEach-Object { $_ } | Where-Object { $_.id -eq $connectorId }).status
}

function New-Account($name, [switch]$Operator) {
    $username = "$($name.ToLower())-$($suffix.ToLower())"
    $password = [guid]::NewGuid().ToString()
    $id = New-KeycloakUser -Username $username -Email "$username@example.com" -Password $password -FirstName $name -Operator:$Operator
    $token = Get-AccessToken -Username $username -Password $password
    Write-Host ("  {0,-7} {1}  {2}" -f $name, $id, $username)
    return @{ Id = $id; Token = $token; Username = $username }
}

Step "0. Sign up three accounts in Keycloak"
$alice = New-Account "Alice"
$bob = New-Account "Bob"
$olivia = New-Account "Olivia" -Operator
Check "Alice's token is for the API and carries the driver role" ((ConvertFrom-Jwt $alice.Token).aud -contains "ev-platform-api" -and (ConvertFrom-Jwt $alice.Token).realm_access.roles -contains "driver")

Step "1. The API refuses anonymous and forged callers"
Check "health is public and UP" ((Api $null Get "/actuator/health").status -eq "UP")
Check "browsing stations needs no login" ($null -ne (Api $null Get "/stations"))
ExpectError "registering a car without a token" 401 (ApiError $null Post "/vehicles" @{ vin = "5YJ3E1EA7KF317000"; make = "Tesla"; model = "Model 3" })
$forged = $alice.Token.Substring(0, $alice.Token.LastIndexOf('.') + 1) + "invalidsignature"
ExpectError "a token with a forged signature" 401 (ApiError $forged Get "/users/me")

Step "2. Alice creates her profile from her login"
$aliceProfile = Api $alice.Token Post "/users/me" @{ phoneNumber = "+44 7700 900123" }
Write-Host "  profile $($aliceProfile.id)  $($aliceProfile.email)  '$($aliceProfile.displayName)'"
Check "profile id is her Keycloak account id" ($aliceProfile.id -eq $alice.Id)
Check "GET /users/me returns her" ((Api $alice.Token Get "/users/me").email -eq "$($alice.Username)@example.com")
ExpectError "registering a second profile" 409 (ApiError $alice.Token Post "/users/me" @{})

Step "3. Alice registers her EV (no owner id needed: it's hers)"
$vin = ("5YJ3E1EA7" + $suffix).Substring(0, 17)
$car = Api $alice.Token Post "/vehicles" @{ vin = $vin; make = "Tesla"; model = "Model 3" }
Write-Host "  vehicle $($car.id)  $($car.make) $($car.model)  VIN $($car.vin)"
Check "vehicle owned by Alice" ($car.ownerUserId -eq $alice.Id)
Check "GET /vehicles lists her 1 car" ((Count (Api $alice.Token Get "/vehicles")) -eq 1)
ExpectError "Bob adding a car before creating his profile" 404 (ApiError $bob.Token Post "/vehicles" @{ vin = ("WVWZZZE1Z" + $suffix).Substring(0, 17); make = "VW"; model = "ID.3" })
ExpectError "Bob reading Alice's car" 403 (ApiError $bob.Token Get "/vehicles/$($car.id)")

Step "4. Only an operator can set up stations"
ExpectError "Alice creating a station" 403 (ApiError $alice.Token Post "/stations" @{ externalReference = "ST-X-$suffix"; name = "Nope"; countryCode = "GB"; latitude = 51.5; longitude = -0.1 })
# A random spot in London each run, so stations left by earlier runs don't crowd the search results.
$lat = [math]::Round(51.45 + (Get-Random -Maximum 1500) / 10000, 6)
$lng = [math]::Round(-0.25 + (Get-Random -Maximum 3000) / 10000, 6)
$station = Api $olivia.Token Post "/stations" @{ externalReference = "ST-$suffix"; name = "Kings Cross Hub $suffix"; countryCode = "GB"; latitude = $lat; longitude = $lng }
$ccs = Api $olivia.Token Post "/stations/$($station.id)/connectors" @{ connectorType = "CCS2"; maxPowerKw = 150 }
$type2 = Api $olivia.Token Post "/stations/$($station.id)/connectors" @{ connectorType = "TYPE2"; maxPowerKw = 22 }
Write-Host "  station $($station.id)  $($station.name)"
Write-Host "  CCS2    $($ccs.id)  150 kW   TYPE2 $($type2.id)  22 kW"
Check "anyone can list the 2 connectors" ((Count (Api $null Get "/stations/$($station.id)/connectors")) -eq 2)

Step "4b. A driver searches for a fast CCS2 charger nearby (no login needed)"
# 300 m north of the station, as if searching from the street
$searchLat = $lat + 0.0027
$found = Api $null Get "/stations/search?lat=$searchLat&lng=$lng&radiusKm=1&connectorType=CCS2&minPowerKw=100&limit=100" | ForEach-Object { $_ } | Where-Object { $_.id -eq $station.id }
Write-Host "  found   $($found.name)  $($found.distanceKm) km away  $($found.availableConnectors) available"
Check "search finds the station about 0.3 km away" ($null -ne $found -and [math]::Abs($found.distanceKm - 0.3) -lt 0.05)
Check "only the matching CCS2 150 kW connector is listed, AVAILABLE" ((Count $found.connectors) -eq 1 -and $found.connectors[0].id -eq $ccs.id -and $found.connectors[0].status -eq "AVAILABLE")
$tooSlow = Api $null Get "/stations/search?lat=$searchLat&lng=$lng&radiusKm=1&connectorType=CCS2&minPowerKw=200&limit=100" | ForEach-Object { $_ } | Where-Object { $_.id -eq $station.id }
Check "asking for 200 kW excludes it" ($null -eq $tooSlow)
$farAway = Api $null Get "/stations/search?lat=48.8809&lng=2.3553&radiusKm=50&limit=100" | ForEach-Object { $_ } | Where-Object { $_.id -eq $station.id }
Check "searching from Paris excludes it" ($null -eq $farAway)
ExpectError "search with half a location" 400 (ApiError $null Get "/stations/search?lat=51.5" $null)

Step "5. Alice reserves the CCS2 connector"
$reservation = Api $alice.Token Post "/reservations" @{ stationId = $station.id; chargerId = $ccs.id; vehicleId = $car.id; startTime = (Get-Date).ToUniversalTime().AddMinutes(1).ToString("o") }
Write-Host "  reservation $($reservation.id)  $($reservation.status)"
Check "reservation CREATED for Alice" ($reservation.status -eq "CREATED" -and $reservation.userId -eq $alice.Id)
ExpectError "overlapping reservation on same connector" 409 (ApiError $alice.Token Post "/reservations" @{ stationId = $station.id; chargerId = $ccs.id; vehicleId = $car.id; startTime = (Get-Date).ToUniversalTime().AddMinutes(2).ToString("o") })
ExpectError "Bob reading Alice's reservation" 403 (ApiError $bob.Token Get "/reservations/$($reservation.id)")
ExpectError "Bob cancelling Alice's reservation" 403 (ApiError $bob.Token Patch "/reservations/$($reservation.id)/cancel" $null)
WaitFor "CCS2 connector now shows RESERVED" { (ConnectorStatus $station.id $ccs.id) -eq "RESERVED" }

Step "6. Bob signs up; he can't use Alice's car or her reserved connector"
Api $bob.Token Post "/users/me" @{} | Out-Null
$bobCar = Api $bob.Token Post "/vehicles" @{ vin = ("KMHK381GF" + $suffix).Substring(0, 17); make = "Hyundai"; model = "Kona" }
ExpectError "Bob charging Alice's car" 409 (ApiError $bob.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; vehicleId = $car.id })
# The station's reservation projection is fed by Kafka, so give the event a moment to land.
$held = $null
for ($i = 0; $i -lt 10; $i++) {
    $held = ApiError $bob.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; vehicleId = $bobCar.id }
    if ($held.Status -eq 409) { break }
    Start-Sleep -Milliseconds 500
}
ExpectError "Bob walking up to Alice's reserved connector" 409 $held

Step "7. Alice plugs in and starts charging against her reservation"
$session = Api $alice.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; vehicleId = $car.id; reservationId = $reservation.id }
Write-Host "  session $($session.id)  $($session.chargingSessionNumber)  $($session.status)"
Check "session ACTIVE for Alice, linked to her reservation" ($session.status -eq "ACTIVE" -and $session.userId -eq $alice.Id -and $session.reservationId -eq $reservation.id)
WaitFor "CCS2 connector now shows OCCUPIED" { (ConnectorStatus $station.id $ccs.id) -eq "OCCUPIED" }
Check "her reservation is now ACTIVE" ((Api $alice.Token Get "/reservations/$($reservation.id)").status -eq "ACTIVE")
ExpectError "same car starting a second session" 409 (ApiError $alice.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; vehicleId = $car.id })

Step "8. Bob charges on the free TYPE2 connector at the same time"
$bobSession = Api $bob.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; vehicleId = $bobCar.id }
Check "Bob's walk-up session ACTIVE" ($bobSession.status -eq "ACTIVE")
ExpectError "Bob stopping Alice's session" 403 (ApiError $bob.Token Patch "/charging/sessions/$($session.id)/stop" @{ totalEnergyKwh = 1 })
Check "Bob sees only his own session" ((Count (Api $bob.Token Get "/charging/sessions")) -eq 1)
WaitFor "search with available=true no longer offers this station" {
    $null -eq (Api $null Get "/stations/search?lat=$lat&lng=$lng&radiusKm=1&available=true&limit=100" | ForEach-Object { $_ } | Where-Object { $_.id -eq $station.id })
}

Step "9. Alice unplugs"
$stopped = Api $alice.Token Patch "/charging/sessions/$($session.id)/stop" @{ totalEnergyKwh = 42.75 }
Write-Host "  session $($stopped.id)  $($stopped.status)  $($stopped.totalEnergyKwh) kWh"
Check "session STOPPED with 42.75 kWh" ($stopped.status -eq "STOPPED" -and [decimal]$stopped.totalEnergyKwh -eq [decimal]42.75)
ExpectError "stopping twice" 409 (ApiError $alice.Token Patch "/charging/sessions/$($session.id)/stop" @{ totalEnergyKwh = 1 })
Check "her reservation is COMPLETED" ((Api $alice.Token Get "/reservations/$($reservation.id)").status -eq "COMPLETED")
WaitFor "CCS2 connector is AVAILABLE again (not left RESERVED)" { (ConnectorStatus $station.id $ccs.id) -eq "AVAILABLE" }

Step "10. The operator can see and act on everything; drivers can't"
Check "Olivia sees both sessions at her new station" ((Count (Api $olivia.Token Get "/charging/sessions" | ForEach-Object { $_ } | Where-Object { $_.stationId -eq $station.id })) -eq 2)
$bobStopped = Api $olivia.Token Patch "/charging/sessions/$($bobSession.id)/stop" @{ totalEnergyKwh = 7.5 }
Check "Olivia stops Bob's session" ($bobStopped.status -eq "STOPPED")
WaitFor "both connectors AVAILABLE, and search offers the station again" {
    $s = Api $null Get "/stations/search?lat=$lat&lng=$lng&radiusKm=1&available=true&limit=100" | ForEach-Object { $_ } | Where-Object { $_.id -eq $station.id }
    $null -ne $s -and $s.availableConnectors -eq 2
}
Check "Olivia can list users" ((Count (Api $olivia.Token Get "/users")) -ge 2)
ExpectError "Alice listing all users" 403 (ApiError $alice.Token Get "/users")

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "All $($script:checks) checks passed." -ForegroundColor Green
    Write-Host "Kafka events: docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic charging.events.v1 --from-beginning --timeout-ms 10000"
    exit 0
}
Write-Host "$($script:failures) of $($script:checks) check(s) failed." -ForegroundColor Red
exit 1
