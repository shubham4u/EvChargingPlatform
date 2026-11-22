# API Smoke Test

How to check the platform end to end, with real logins: drivers sign up and create a profile, add their EV, reserve a connector, charge, and stop, while an operator manages the station.

## 1. One-time setup

Copy `.env.example` to `.env` if you haven't, and set both values to local-only passwords:

```
POSTGRES_PASSWORD=...
KEYCLOAK_ADMIN_PASSWORD=...
```

## 2. Start the stack

```powershell
docker compose up --build -d
Invoke-RestMethod http://localhost:8080/actuator/health   # status: UP
```

This starts the API (8080), Keycloak (8081), Postgres, Redis, Kafka and Jaeger. The API waits for Keycloak to be healthy, which takes about 30 seconds on first start. `docker compose down` clears all data, including Keycloak accounts.

## 3. Run the scripted journey

```powershell
.\scripts\demo-flow.ps1
```

If PowerShell blocks scripts, run `powershell -ExecutionPolicy Bypass -File .\scripts\demo-flow.ps1`.

Each run creates three fresh Keycloak accounts (drivers Alice and Bob, operator Olivia) with random passwords, signs them in, and makes 31 checks. It covers the happy path, every rule that should refuse a request, and who may do what: no token → 401, Bob touching Alice's things → 403, a driver creating a station → 403. It exits `0` when everything passes. Expected tail:

```
== 10. The operator can see and act on everything; drivers can't
  [PASS] Olivia sees both sessions at her new station
  [PASS] Olivia stops Bob's session
  [PASS] Olivia can list users
  [PASS] Alice listing all users -> 403 Forbidden

All 31 checks passed.
```

## 4. Or step through it by hand

Create accounts and get tokens. Each command returns `Id`, `Username`, `Password` and `Token`:

```powershell
$alice  = .\scripts\new-user.ps1 -Name Alice
$olivia = .\scripts\new-user.ps1 -Name Olivia -Operator

$api = "http://localhost:8080"
function Call($who, $method, $path, $body) {
    $p = @{ Method = $method; Uri = "$api$path"; Headers = @{ Authorization = "Bearer $($who.Token)" }; ContentType = "application/json" }
    if ($body) { $p.Body = $body | ConvertTo-Json }
    Invoke-RestMethod @p
}
```

Tokens last 15 minutes. To refresh one: `$alice.Token = .\scripts\get-token.ps1 -Username $alice.Username -Password $alice.Password`.

Operator sets up a station:

```powershell
$station = Call $olivia Post "/stations" @{ externalReference = "ST-001"; name = "Kings Cross Hub"; countryCode = "GB"; latitude = 51.5308; longitude = -0.1238 }
$ccs     = Call $olivia Post "/stations/$($station.id)/connectors" @{ connectorType = "CCS2"; maxPowerKw = 150 }
```

Alice creates her profile, adds her car, and charges. There is no `userId` anywhere: the API takes it from her token.

```powershell
Call $alice Post "/users/me" @{ phoneNumber = "+44 7700 900123" }
$car     = Call $alice Post "/vehicles" @{ vin = "5YJ3E1EA7KF317000"; make = "Tesla"; model = "Model 3" }
$session = Call $alice Post "/charging/sessions" @{ stationId = $station.id; connectorId = $ccs.id; vehicleId = $car.id }
$session.status                                                     # ACTIVE
Call $alice Patch "/charging/sessions/$($session.id)/stop" @{ totalEnergyKwh = 42.75 }
Call $alice Get "/charging/sessions"                                # only Alice's sessions
```

Each VIN and `externalReference` must be unique, so change them if you run this twice. To reserve first, `Call $alice Post "/reservations" @{ stationId = ...; chargerId = $ccs.id; vehicleId = $car.id; startTime = (Get-Date).ToUniversalTime().AddMinutes(1).ToString("o") }`, then pass `reservationId` when starting.

Check the refusals:

```powershell
curl.exe -s -i "$api/users/me" | Select-Object -First 1                                    # HTTP/1.1 401 (no token)
curl.exe -s "$api/stations" -X POST -H "Authorization: Bearer $($alice.Token)" -H "Content-Type: application/json" -d "{}"   # 403 Forbidden: drivers can't create stations
```

Errors are problem-detail JSON, for example `{"title":"Forbidden","status":403,...}`. Windows PowerShell 5.1 doesn't put that body in `$_.ErrorDetails`, which is why the examples above use `curl.exe` to show it.

### Looking inside a token

```powershell
.\scripts\get-token.ps1 -Username $alice.Username -Password $alice.Password -Decode | Select-Object iss, aud, sub, email, @{n='roles';e={$_.realm_access.roles}}
```

`sub` is Alice's user id everywhere in the API. `aud` must include `ev-platform-api`, and `roles` includes `driver` (plus `operator` for Olivia).

### Using Keycloak directly

The admin console is at http://localhost:8081 (user `admin`, password from `.env`), under realm **ev-charging**. Anyone can also sign up themselves at http://localhost:8081/realms/ev-charging/account. To make an account an operator, open Users → the user → Role mapping → Assign role → `operator`. Then get a token with `.\scripts\get-token.ps1`.

## 5. Check the events

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic charging.events.v1 --from-beginning --timeout-ms 10000 --property print.key=true
```

Topics: `user.events.v1`, `vehicle.events.v1`, `station.events.v1`, `reservation.events.v1`, `charging.events.v1`. The `TimeoutException` printed at the end is how `--timeout-ms` exits.

## 6. Check the database

```powershell
docker compose exec postgres psql -U ev_charging -d ev_charging -c "SELECT u.email, v.vin, s.status, s.total_energy_kwh FROM charging_sessions s JOIN users u ON u.id = s.user_id JOIN vehicles v ON v.id = s.vehicle_id ORDER BY s.created_at;"
```
