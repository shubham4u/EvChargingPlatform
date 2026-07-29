# API Smoke Test

Use these PowerShell commands to verify the EV Charging Platform is running correctly.

## Start the application

```powershell
mvn spring-boot:run
```

The app should start on `http://localhost:8080` without an `APPLICATION FAILED TO START` error.

## Check health

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected result:

```json
{
  "status": "UP"
}
```

## Create a station

```powershell
$station = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/stations `
  -ContentType "application/json" `
  -Body '{"externalRef":"ST-001","name":"Central Station","countryCode":"GB","latitude":51.5074,"longitude":-0.1278}'
```

## List stations

```powershell
Invoke-RestMethod http://localhost:8080/stations
```

## Start a charging session

```powershell
$session = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/charging/start `
  -ContentType "application/json" `
  -Body ("{`"stationId`":`"" + $station.stationId + "`",`"connectorId`":`"" + [guid]::NewGuid() + "`",`"userId`":`"" + [guid]::NewGuid() + "`"}")
```

## Get the session

```powershell
Invoke-RestMethod http://localhost:8080/sessions/$($session.sessionId)
```

## Stop the session

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/charging/stop `
  -ContentType "application/json" `
  -Body ("{`"sessionId`":`"" + $session.sessionId + "`",`"meterReadingKwh`":12.5}")
```

## What to confirm

- The application starts successfully.
- Health returns `UP`.
- Station creation succeeds.
- Station listing returns the created station.
- Session start succeeds.
- Session lookup returns the same session ID.
- Session stop changes the status to `STOPPED`.

## Note

The current implementation uses in-memory services, so data is cleared when the application restarts.
