# Testing and Fixes

This note records how we checked whether the application was working, what failed during testing, and how we resolved each issue.

## What we tested

We verified the application in three layers:

1. Build and startup with Spring Boot.
2. Runtime API checks against the local service.
3. Infrastructure checks for Docker Compose and database connectivity.

### 1. Application startup

We first tried to run the app directly from the IDE and from Maven.

Expected command:

```powershell
mvn spring-boot:run
```

We confirmed the app should start on `http://localhost:8080` and expose the Actuator health endpoint.

### 2. API smoke test

We used PowerShell requests to check the core endpoints:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/stations
Invoke-RestMethod -Method Post -Uri http://localhost:8080/stations -ContentType "application/json" -Body '{"externalReference":"LON-001","name":"London Central","countryCode":"GB","latitude":51.5074,"longitude":-0.1278}'
Invoke-RestMethod http://localhost:8080/stations/{id}
```

### 3. Docker Compose

We also verified the full local stack with:

```powershell
docker compose up --build
```

This checked PostgreSQL, Redis, Kafka, Jaeger, and the Spring Boot service together.

## What failed

### Maven was not available at first

The shell returned:

```powershell
mvn : The term 'mvn' is not recognized as the name of a cmdlet
```

This meant Maven was not on the PATH in that session.

### Docker Desktop engine was not running

The first Docker Compose attempt failed with a pipe/engine connection error because the Docker Desktop Linux engine was not available yet.

### Kafka image reference was invalid

Docker Compose later failed because the Kafka image tag `bitnami/kafka:3.7` could not be resolved.

### Station lookup returned a 500 error

After creating a station successfully, `GET /stations/{id}` initially returned a `500 Internal Server Error`.

In one case, the connection was also closed unexpectedly during the lookup request.

## How we resolved it

### Maven

We checked the installed versions with:

```powershell
java -version
mvn -version
```

The environment already had Maven 3.9.16 installed, so the fix was to use a shell session where `mvn` was available.

### Docker

We started Docker Desktop and waited until the Linux engine was ready. After that, `docker compose up --build` could pull and run the services.

### Kafka

We replaced the broken Kafka image reference with a valid image tag and adjusted the Compose configuration so Kafka could start correctly in the local stack.

### Station lookup

The `500` on `GET /stations/{id}` was caused by Redis caching a type that was not safely serializable for the cache path we were using.

We fixed this by making the station domain object serializable, then retested the same lookup request. After that, the endpoint returned the station successfully.

## Final verification

Once the fixes were applied, the following checks passed:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/stations
Invoke-RestMethod http://localhost:8080/stations/{id}
docker compose up --build
```

At that point, the application, API, and local supporting services were all working together.

