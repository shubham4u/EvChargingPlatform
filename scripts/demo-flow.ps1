<#
.SYNOPSIS
  End-to-end demo of the driver journey: register -> add EV -> find a connector -> reserve -> charge -> stop.

.DESCRIPTION
  Calls the REST API only and asserts each step, so it doubles as a smoke test.
  Works on Windows PowerShell 5.1 and PowerShell 7. Start the stack first:
      docker compose up --build -d
  then run:
      .\scripts\demo-flow.ps1
  Exit code is 0 when every check passes, 1 otherwise.
#>
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$script:failures = 0
$suffix = [guid]::NewGuid().ToString("N").Substring(0, 8).ToUpper()

function Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }

function Check($label, $condition) {
    if ($condition) { Write-Host "  [PASS] $label" -ForegroundColor Green }
    else { Write-Host "  [FAIL] $label" -ForegroundColor Red; $script:failures++ }
}

function Api($method, $path, $body) {
    $params = @{ Method = $method; Uri = "$BaseUrl$path"; ContentType = "application/json" }
    if ($null -ne $body) { $params.Body = ($body | ConvertTo-Json -Compress) }
    Invoke-RestMethod @params
}

# Calls an endpoint that is expected to fail and returns the HTTP status and problem title.
function ApiError($method, $path, $body) {
    try {
        Api $method $path $body | Out-Null
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

# Windows PowerShell 5.1 emits a JSON array as one pipeline object, so @(...).Count is wrong there.
function Count($items) { return ($items | Measure-Object).Count }

function ExpectError($label, $expectedStatus, $result) {
    Check "$label -> $expectedStatus $($result.Title)" ($result.Status -eq $expectedStatus)
}

Step "Health"
Check "application is UP" ((Api Get "/actuator/health").status -eq "UP")

Step "1. Register a driver"
$alice = Api Post "/users" @{ email = "alice.$suffix@example.com"; displayName = "Alice $suffix"; phoneNumber = "+44 7700 900123" }
Write-Host "  user    $($alice.id)  $($alice.email)"
Check "user registered as DRIVER" ($alice.role -eq "DRIVER")
ExpectError "same email again" 409 (ApiError Post "/users" @{ email = $alice.email.ToUpper(); displayName = "Dup" })

Step "2. Register her EV"
$vin = ("5YJ3E1EA7" + $suffix).Substring(0, 17)
$car = Api Post "/vehicles" @{ ownerUserId = $alice.id; vin = $vin; make = "Tesla"; model = "Model 3" }
Write-Host "  vehicle $($car.id)  $($car.make) $($car.model)  VIN $($car.vin)"
Check "vehicle owned by Alice" ($car.ownerUserId -eq $alice.id)
Check "listed under Alice" ((Count (Api Get "/vehicles?ownerUserId=$($alice.id)")) -eq 1)
ExpectError "vehicle for unknown user" 404 (ApiError Post "/vehicles" @{ ownerUserId = [guid]::NewGuid(); vin = "WVWZZZE1ZMP0" + $suffix.Substring(0, 5); make = "VW"; model = "ID.3" })

Step "3. Operator sets up a station with connectors"
$station = Api Post "/stations" @{ externalReference = "ST-$suffix"; name = "Kings Cross Hub $suffix"; countryCode = "GB"; latitude = 51.5308; longitude = -0.1238 }
$ccs = Api Post "/stations/$($station.id)/connectors" @{ connectorType = "CCS2"; maxPowerKw = 150 }
$type2 = Api Post "/stations/$($station.id)/connectors" @{ connectorType = "TYPE2"; maxPowerKw = 22 }
Write-Host "  station $($station.id)  $($station.name)"
Write-Host "  CCS2    $($ccs.id)  $($ccs.maxPowerKw) kW"
Write-Host "  TYPE2   $($type2.id)  $($type2.maxPowerKw) kW"
Check "station lists 2 connectors" ((Count (Api Get "/stations/$($station.id)/connectors")) -eq 2)

Step "4. Alice reserves the CCS2 connector"
$reservation = Api Post "/reservations" @{ stationId = $station.id; chargerId = $ccs.id; userId = $alice.id; vehicleId = $car.id; startTime = (Get-Date).ToUniversalTime().AddMinutes(1).ToString("o") }
Write-Host "  reservation $($reservation.id)  $($reservation.status)"
Check "reservation CREATED" ($reservation.status -eq "CREATED")
ExpectError "overlapping reservation on same connector" 409 (ApiError Post "/reservations" @{ stationId = $station.id; chargerId = $ccs.id; userId = $alice.id; vehicleId = $car.id; startTime = (Get-Date).ToUniversalTime().AddMinutes(2).ToString("o") })

Step "5. Someone else cannot use Alice's car or her reserved connector"
$bob = Api Post "/users" @{ email = "bob.$suffix@example.com"; displayName = "Bob $suffix" }
ExpectError "Bob charging Alice's car" 409 (ApiError Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; userId = $bob.id; vehicleId = $car.id })
$bobVin = ("KMHK381GF" + $suffix).Substring(0, 17)
$bobCar = Api Post "/vehicles" @{ ownerUserId = $bob.id; vin = $bobVin; make = "Hyundai"; model = "Kona" }
# The station's reservation projection is fed by Kafka, so give the event a moment to land.
$held = $null
for ($i = 0; $i -lt 10; $i++) {
    $held = ApiError Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; userId = $bob.id; vehicleId = $bobCar.id }
    if ($held.Status -eq 409) { break }
    Start-Sleep -Milliseconds 500
}
ExpectError "Bob walking up to Alice's reserved connector" 409 $held
ExpectError "connector from a different station" 404 (ApiError Post "/charging/sessions" @{ stationId = [guid]::NewGuid(); connectorId = $type2.id; userId = $bob.id; vehicleId = $bobCar.id })

Step "6. Alice plugs in and starts charging against her reservation"
$session = Api Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; userId = $alice.id; vehicleId = $car.id; reservationId = $reservation.id }
Write-Host "  session $($session.id)  $($session.chargingSessionNumber)  $($session.status)"
Check "session ACTIVE" ($session.status -eq "ACTIVE")
Check "session linked to reservation" ($session.reservationId -eq $reservation.id)
ExpectError "same car starting a second session" 409 (ApiError Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; userId = $alice.id; vehicleId = $car.id })

Step "7. Bob charges on the free TYPE2 connector at the same time"
$bobSession = Api Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; userId = $bob.id; vehicleId = $bobCar.id }
Check "Bob's walk-up session ACTIVE" ($bobSession.status -eq "ACTIVE")
ExpectError "a third car on the busy TYPE2 connector" 409 (ApiError Post "/charging/sessions" @{ stationId = $station.id; connectorId = $type2.id; userId = $alice.id; vehicleId = $car.id })

Step "8. Alice unplugs"
$stopped = Api Patch "/charging/sessions/$($session.id)/stop" @{ totalEnergyKwh = 42.75 }
Write-Host "  session $($stopped.id)  $($stopped.status)  $($stopped.totalEnergyKwh) kWh"
Check "session STOPPED" ($stopped.status -eq "STOPPED")
Check "energy recorded" ([decimal]$stopped.totalEnergyKwh -eq [decimal]42.75)
$reread = Api Get "/charging/sessions/$($session.id)"
Check "GET shows STOPPED" ($reread.status -eq "STOPPED")
ExpectError "stopping twice" 409 (ApiError Patch "/charging/sessions/$($session.id)/stop" @{ totalEnergyKwh = 1 })
Api Patch "/charging/sessions/$($bobSession.id)/stop" @{ totalEnergyKwh = 7.5 } | Out-Null

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "All checks passed." -ForegroundColor Green
    Write-Host "Kafka events: docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic charging.events.v1 --from-beginning --timeout-ms 10000"
    exit 0
}
Write-Host "$($script:failures) check(s) failed." -ForegroundColor Red
exit 1
