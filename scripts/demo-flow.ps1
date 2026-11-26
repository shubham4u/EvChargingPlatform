<#
.SYNOPSIS
  End-to-end demo of the driver journey with real logins:
  sign up -> profile -> card -> add EV -> operator sets up a priced station -> find it -> reserve -> charge -> pay.

.DESCRIPTION
  Creates fresh Keycloak accounts each run (drivers Alice, Bob, Carol, Dave; operator Olivia) with random
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

Step "0. Sign up accounts in Keycloak"
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
ExpectError "saving a raw card number instead of a provider token" 400 (ApiError $alice.Token Put "/payments/methods/me" @{ token = "4242424242424242" })
$aliceCard = Api $alice.Token Put "/payments/methods/me" @{ token = "tok_visa" }
Check "Alice saves a card (visa ending 4242)" ($aliceCard.brand -eq "visa" -and $aliceCard.last4 -eq "4242")

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
ExpectError "Alice setting the station's prices" 403 (ApiError $alice.Token Put "/stations/$($station.id)/tariff" @{ currency = "GBP"; pricePerKwh = 0.01; pricePerMinute = 0; sessionFee = 0 })
$tariff = Api $olivia.Token Put "/stations/$($station.id)/tariff" @{ currency = "GBP"; pricePerKwh = 0.45; pricePerMinute = 0.05; sessionFee = 0.50 }
Write-Host "  tariff  GBP 0.45/kWh + 0.05/min + 0.50 per session"
Check "anyone can read the prices before plugging in" ((Api $null Get "/stations/$($station.id)/tariff").id -eq $tariff.id)

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
ExpectError "Bob charging before adding a card" 402 (ApiError $bob.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; vehicleId = $bobCar.id })
Api $bob.Token Put "/payments/methods/me" @{ token = "tok_mastercard" } | Out-Null

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
Write-Host "  bill    $($stopped.currency) $($stopped.totalCost)"
# 0.50 + 42.75 x 0.45 = 19.7375, plus a few seconds at 0.05/min
Check "billed GBP 19.74-19.80 (fee + energy + time at the tariff it started under)" ($stopped.currency -eq "GBP" -and [decimal]$stopped.totalCost -ge 19.74 -and [decimal]$stopped.totalCost -le 19.80)
$alicePayment = Api $alice.Token Get "/payments" | ForEach-Object { $_ } | Where-Object { $_.sessionId -eq $session.id }
Check "her card was charged exactly the bill" ($alicePayment.status -eq "CAPTURED" -and [decimal]$alicePayment.capturedAmount -eq [decimal]$stopped.totalCost -and [decimal]$alicePayment.authorizedAmount -eq 50)
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

Step "11. Payment problems are never silent"
$carol = New-Account "Carol"
$dave = New-Account "Dave"
foreach ($who in $carol, $dave) { Api $who.Token Post "/users/me" @{} | Out-Null }
Api $carol.Token Put "/payments/methods/me" @{ token = "tok_chargeDeclined" } | Out-Null
Api $dave.Token Put "/payments/methods/me" @{ token = "tok_captureFails" } | Out-Null
$carolCar = Api $carol.Token Post "/vehicles" @{ vin = ("WVWZZZE1Z" + $suffix).Substring(0, 17); make = "VW"; model = "ID.3" }
$daveCar = Api $dave.Token Post "/vehicles" @{ vin = ("SALYA2BN1" + $suffix).Substring(0, 17); make = "Jaguar"; model = "I-Pace" }
ExpectError "Carol's declined card stops the session starting" 402 (ApiError $carol.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; vehicleId = $carolCar.id })
Check "no session was created for Carol" ((Count (Api $carol.Token Get "/charging/sessions")) -eq 0)
$carolPayment = Api $carol.Token Get "/payments" | ForEach-Object { $_ }
Check "but the decline is on her record (FAILED, card_declined)" ((Count $carolPayment) -eq 1 -and $carolPayment.status -eq "FAILED" -and $carolPayment.failureCode -eq "card_declined")
Check "the connector is still AVAILABLE" ((ConnectorStatus $station.id $ccs.id) -eq "AVAILABLE")
$daveSession = Api $dave.Token Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; vehicleId = $daveCar.id }
Check "Dave's hold succeeds, so he can charge" ($daveSession.status -eq "ACTIVE")
$daveStopped = Api $dave.Token Patch "/charging/sessions/$($daveSession.id)/stop" @{ totalEnergyKwh = 10 }
Write-Host "  session $($daveStopped.id)  $($daveStopped.status)  owes $($daveStopped.currency) $($daveStopped.totalCost)"
Check "when the final charge fails, the session ends PAYMENT_FAILED, not STOPPED" ($daveStopped.status -eq "PAYMENT_FAILED" -and [decimal]$daveStopped.totalCost -gt 0)
$davePayment = Api $dave.Token Get "/payments" | ForEach-Object { $_ }
Check "his payment is FAILED (capture_failed)" ($davePayment.status -eq "FAILED" -and $davePayment.failureCode -eq "capture_failed")
WaitFor "his connector is freed anyway" { (ConnectorStatus $station.id $ccs.id) -eq "AVAILABLE" }
$unpriced = Api $olivia.Token Post "/stations" @{ externalReference = "ST-NP-$suffix"; name = "Unpriced $suffix"; countryCode = "GB"; latitude = $lat; longitude = $lng }
$npConnector = Api $olivia.Token Post "/stations/$($unpriced.id)/connectors" @{ connectorType = "CCS2"; maxPowerKw = 50 }
ExpectError "charging at a station with no prices set" 404 (ApiError $alice.Token Post "/charging/sessions" @{ stationId = $unpriced.id; connectorId = $npConnector.id; vehicleId = $car.id })

Step "12. The operator refunds Alice"
ExpectError "Alice refunding herself" 403 (ApiError $alice.Token Post "/payments/$($alicePayment.id)/refund" $null)
$refund = Api $olivia.Token Post "/payments/$($alicePayment.id)/refund" $null
Check "Olivia refunds Alice's payment" ($refund.status -eq "REFUNDED")
ExpectError "refunding it twice" 409 (ApiError $olivia.Token Post "/payments/$($alicePayment.id)/refund" $null)
ExpectError "refunding Dave's failed payment" 409 (ApiError $olivia.Token Post "/payments/$($davePayment.id)/refund" $null)

Step "13. Everyone is told, and everything is on the record"
# Notifications and the audit are built from events, so give the consumers a moment.
WaitFor "Alice's inbox: reservation confirmed, charging finished, payment received, refund issued" {
    $types = (Api $alice.Token Get "/notifications" | ForEach-Object { $_ }).type
    ($types -contains "RESERVATION_CONFIRMED") -and ($types -contains "CHARGING_FINISHED") -and ($types -contains "PAYMENT_RECEIVED") -and ($types -contains "REFUND_ISSUED")
}
WaitFor "Carol was told her card was declined" { (Api $carol.Token Get "/notifications" | ForEach-Object { $_ }).type -contains "PAYMENT_DECLINED" }
WaitFor "Dave was told his payment failed and to update his card" {
    $n = Api $dave.Token Get "/notifications" | ForEach-Object { $_ } | Where-Object { $_.type -eq "PAYMENT_FAILED" }
    $null -ne $n -and $n.body -like "*update your card*"
}
$unreadBefore = Count (Api $alice.Token Get "/notifications?unread=true")
$first = Api $alice.Token Get "/notifications" | ForEach-Object { $_ } | Select-Object -First 1
Api $alice.Token Patch "/notifications/$($first.id)/read" $null | Out-Null
Check "marking one read leaves one fewer unread" ((Count (Api $alice.Token Get "/notifications?unread=true")) -eq $unreadBefore - 1)
ExpectError "Bob marking Alice's notification read" 403 (ApiError $bob.Token Patch "/notifications/$($first.id)/read" $null)

$expected = "PaymentAuthorizedEvent", "ChargingSessionRequestedEvent", "ChargingSessionStartedEvent", "PaymentCapturedEvent", "ChargingSessionStoppedEvent", "PaymentRefundedEvent"
WaitFor "the audit trail for Alice's session has all 6 events, each with a trace id" {
    $trail = Api $olivia.Token Get "/audit/events?key=$($session.id)" | ForEach-Object { $_ }
    $types = $trail.eventType
    (@($expected | Where-Object { $types -notcontains $_ }).Count -eq 0) -and (@($trail | Where-Object { -not $_.traceId }).Count -eq 0)
}
$trail = Api $olivia.Token Get "/audit/events?key=$($session.id)" | ForEach-Object { $_ }
Write-Host "  audit   $(($trail.eventType) -join ' > ')"
Check "start, stop and refund are three requests, so three trace ids" ((@($trail.traceId | Select-Object -Unique)).Count -eq 3)
ExpectError "Alice reading the audit" 403 (ApiError $alice.Token Get "/audit/events?key=$($session.id)" $null)

$report = Api $olivia.Token Get "/reports/stations/$($station.id)"
Write-Host "  report  $($report.sessions) sessions, $($report.energyKwh) kWh, revenue $($report.revenue), refunded $($report.refunded), outstanding $($report.outstanding) $($report.currency)"
Check "report: 3 sessions (Carol never started), 2 paid, 1 payment failure" ($report.sessions -eq 3 -and $report.paidSessions -eq 2 -and $report.paymentFailures -eq 1)
Check "report: revenue = Bob's bill, refunded = Alice's, outstanding = Dave's" ([decimal]$report.revenue -eq [decimal]$bobStopped.totalCost -and [decimal]$report.refunded -eq [decimal]$stopped.totalCost -and [decimal]$report.outstanding -eq [decimal]$daveStopped.totalCost)
ExpectError "Bob reading the station report" 403 (ApiError $bob.Token Get "/reports/stations/$($station.id)" $null)

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "All $($script:checks) checks passed." -ForegroundColor Green
    Write-Host "Kafka events: docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic charging.events.v1 --from-beginning --timeout-ms 10000"
    exit 0
}
Write-Host "$($script:failures) of $($script:checks) check(s) failed." -ForegroundColor Red
exit 1
