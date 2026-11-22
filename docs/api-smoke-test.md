# API Smoke Test

How to check the platform end to end: a driver registers, adds their EV, finds a connector, reserves it, charges, and stops.

## 1. Start the stack

```powershell
docker compose up --build -d
Invoke-RestMethod http://localhost:8080/actuator/health   # status: UP
```

Kafka publishing is enabled in `docker-compose.yml` (`KAFKA_ENABLED=true`). Data lives in the Postgres container, so `docker compose down` clears it.

## 2. Run the scripted journey

```powershell
.\scripts\demo-flow.ps1
```

If PowerShell blocks the script, run `powershell -ExecutionPolicy Bypass -File .\scripts\demo-flow.ps1`.

The script creates fresh users, vehicles and a station on every run, prints each id, and makes 21 checks: the happy path and the rules that should refuse a request. It exits `0` when everything passes. Expected tail:

```
== 8. Alice unplugs
  session ...  STOPPED  42.75 kWh
  [PASS] session STOPPED
  [PASS] energy recorded
  [PASS] GET shows STOPPED
  [PASS] stopping twice -> 409 Invalid state transition

All checks passed.
```

## 3. Or step through it by hand

```powershell
$api = "http://localhost:8080"
function Post($path, $body) { Invoke-RestMethod -Method Post -Uri "$api$path" -ContentType "application/json" -Body ($body | ConvertTo-Json) }

# Driver and EV
$alice = Post "/users" @{ email = "alice@example.com"; displayName = "Alice" }
$car   = Post "/vehicles" @{ ownerUserId = $alice.id; vin = "5YJ3E1EA7KF317000"; make = "Tesla"; model = "Model 3" }

# Station with a connector
$station = Post "/stations" @{ externalReference = "ST-001"; name = "Kings Cross Hub"; countryCode = "GB"; latitude = 51.5308; longitude = -0.1238 }
$ccs     = Post "/stations/$($station.id)/connectors" @{ connectorType = "CCS2"; maxPowerKw = 150 }

# Charge (walk-up, no reservation)
$session = Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; userId = $alice.id; vehicleId = $car.id }
$session.status        # ACTIVE

Invoke-RestMethod -Method Patch -Uri "$api/charging/sessions/$($session.id)/stop" -ContentType "application/json" -Body '{"totalEnergyKwh": 42.75}'
Invoke-RestMethod "$api/charging/sessions/$($session.id)"   # status STOPPED
```

Each email, VIN and `externalReference` must be unique, so change them if you run this twice.

To reserve first, create a reservation and pass its id when starting:

```powershell
$res = Post "/reservations" @{ stationId = $station.id; chargerId = $ccs.id; userId = $alice.id; vehicleId = $car.id; startTime = (Get-Date).ToUniversalTime().AddMinutes(1).ToString("o") }
$session = Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; userId = $alice.id; vehicleId = $car.id; reservationId = $res.id }
```

### Seeing error responses

Errors are problem-detail JSON, for example `{"title":"Connector in use","status":409,"detail":"..."}`. Windows PowerShell 5.1 does not put the body in `$_.ErrorDetails`, so use `curl.exe` to see it:

```powershell
curl.exe -s -X POST "$api/charging/sessions" -H "Content-Type: application/json" -d "{\`"stationId\`":\`"$($station.id)\`",\`"connectorId\`":\`"$($ccs.id)\`",\`"userId\`":\`"$($alice.id)\`",\`"vehicleId\`":\`"$($car.id)\`"}"
```

The full list of rules and status codes is in [api-design.md](api-design.md).

## 4. Check the events

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic charging.events.v1 --from-beginning --timeout-ms 10000 --property print.key=true
```

Topics: `user.events.v1`, `vehicle.events.v1`, `station.events.v1`, `reservation.events.v1`, `charging.events.v1`. The `TimeoutException` printed at the end is how `--timeout-ms` exits.

## 5. Check the database

```powershell
docker compose exec postgres psql -U ev_charging -d ev_charging -c "SELECT u.email, v.vin, s.status, s.total_energy_kwh FROM charging_sessions s JOIN users u ON u.id = s.user_id JOIN vehicles v ON v.id = s.vehicle_id ORDER BY s.created_at;"
```
