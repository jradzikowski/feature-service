# feature-service

Serwis feature flag dla aplikacji AwesomeSoft: włączanie/wyłączanie funkcjonalności per workgroup
i globalnie, dla wielu aplikacji (pierwsza: `audit`). Pełna dokumentacja projektowa:
`audit/docs/featureservice/` (architektura, model danych, kontrakt `openapi.yaml`, integracja).

## Stack

Spring Boot 4.0.3 / Java 21 / Maven, PostgreSQL (dev: H2 w trybie PostgreSQL — te same migracje
Flyway wszędzie, `ddl-auto=none`), panel admina React+Vite w `ui/` budowany do `/static/admin`
przez frontend-maven-plugin.

## Szybki start (dev, H2 w pamięci)

```bash
./mvnw spring-boot:run          # backend + panel: http://localhost:11230/admin/
# login: admin / admin-change-me (features.admin.initial-*)
```

na prodzie UI i backend działają na porcie 8080

Swagger: `http://localhost:11230/swagger-ui.html`. Actuator (health/prometheus): port `9091`.

Praca nad panelem z hot-reloadem:

```bash
cd ui && npm install && npm run dev   # http://localhost:5173/admin/, proxy /features-api -> :11230
```

Szybkie iteracje backendu bez budowania UI: `./mvnw -Dskip.ui=true test`.

## Lokalny Postgres

```bash
docker compose up -d postgres         # port 11231, baza featuredb
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Budowa obrazu Docker

```bash
./mvnw package                        # JAR z wbudowanym panelem (target/feature-service-*.jar)
docker build -t feature-service:latest .
docker run -p 11230:11230 feature-service:latest
```

## Konfiguracja (env)

| Zmienna | Opis |
|---|---|
| `FEATURES_ADMIN_USERNAME` / `FEATURES_ADMIN_PASSWORD` | bootstrap pierwszego konta ADMIN (tylko gdy tabela `admin_users` pusta) |
| `FEATURES_BOOTSTRAP_TOKEN` / `FEATURES_BOOTSTRAP_APPLICATION` | awaryjny token Evaluation API zanim wygenerujesz tokeny w panelu (puste = wyłączone) |
| `K_DB_URL` / `K_DB_USERNAME` / `K_DB_PASSWORD` | Postgres na profilu `kubernetes` |

## API w skrócie

- `POST /features-api/v1/evaluate` — wyliczone flagi dla kontekstu (`Bearer` token per aplikacja, ETag/304)
- `POST /features-api/v1/registrations` — samorejestracja katalogu: aplikacja na własnym tokenie zakłada zadeklarowane w kodzie flagi, których serwis nie ma (tylko tworzy, idempotentne)
- `GET /features-api/v1/health` — health bez auth
- `/features-api/v1/admin/**` — Admin API (sesja `FEATURES_SESSION`; rola ADMIN mutuje, VIEWER czyta)
- `/admin/` — panel administracyjny (SPA)

Rozstrzyganie wartości: `locked (kill switch) > override per workgroup > default flagi > default w kodzie konsumenta`.

## Konwencje / odstępstwa od audytu

Warstwy `domain/application/infrastructure/rest`, `ErrorResponse` i profile jak w audycie.
Świadome odstępstwa: DTO jako rekordy; repozytoria Spring Data bez pary port/adapter (brak
alternatywnych implementacji); Flyway uruchamiany też na dev H2 (MODE=PostgreSQL) zamiast
`ddl-auto=create-drop` — jedna definicja schematu; obraz z Dockerfile (temurin JRE) zamiast
buildpacków (można wrócić do `spring-boot:build-image` w CI bez zmian w kodzie).

## Testy

```bash
./mvnw -Dskip.ui=true test
```

22 testy: rozstrzyganie wartości i typów, Evaluation API (tokeny, izolacja aplikacji, ETag/304,
locked/archived), Admin API (sesja, role, walidacja, audit log, config_version), rate limit
logowania, migracje na prawdziwym Postgresie (Testcontainers).
