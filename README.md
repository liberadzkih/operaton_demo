# operaton_demo

Demo aplikacji **Spring Boot + Camunda 7 (7.20.0)** prezentująca procesy BPMN
uruchamiane w pełni automatycznie oraz z zadaniami użytkownika (user tasks),
wystawione przez proste REST API.

## Wymagania

- Java 17
- Maven (dołączony wrapper `mvnw`)
- Docker (opcjonalnie — dla PostgreSQL / standalone Camunda)

## Stos technologiczny

- Spring Boot 3.1.x
- Camunda BPM Spring Boot Starter 7.20.0 (webapp + REST)
- PostgreSQL (runtime), H2 (testy)
- Spring Web, Spring Data JPA, SLF4J

## Uruchomienie

### 1. Baza danych

```bash
docker-compose up -d postgres
```

### 2. Aplikacja

```bash
./mvnw spring-boot:run
```

- Aplikacja: http://localhost:8080
- Camunda Cockpit: http://localhost:8080/camunda (login: `demo` / `demo`)
- REST API: `http://localhost:8080/api/process/...`

> Uwaga: `docker-compose.yml` uruchamia również **osobny** oficjalny obraz
> Camunda 7.20 na porcie 8080. Nie uruchamiaj go równocześnie z
> `mvnw spring-boot:run`, bo obie usługi konkurują o port 8080.

## Build i testy

```bash
./mvnw clean install
```

Testy używają bazy H2 w pamięci, więc nie wymagają działającego PostgreSQL.

## Procesy BPMN

| Klucz procesu | Plik | Opis |
|---|---|---|
| `Operation-Automatic` | `Operation-Automatic.bpmn20.xml` | Start → Validate → Process → Send Notification → End |
| `Operation-WithUserTasks` | `Operation-WithUserTasks.bpmn20.xml` | Start → Validate → User Task (Approve) → bramka `approved?` → Process / Reject → End |
| `Operation-CurrencyExchange` | `Operation-CurrencyExchange.bpmn20.xml` | Start → **Fetch Exchange Rate** (Frankfurter API) → User Task (Review) → bramka `approved?` → Confirm / Reject → End |
| `Operation-WeatherCheck` | `Operation-WeatherCheck.bpmn20.xml` | Start → **Fetch Weather** (Open-Meteo API) → bramka `< 5°C?` → Frost Warning / Log Pleasant Weather → End (automatyczny) |

### Integracje z zewnętrznymi API

Oba dodatkowe procesy integrują się z darmowymi, publicznymi API (bez klucza):

- **[Frankfurter](https://www.frankfurter.app/)** (`api.frankfurter.dev`) — kursy walut Europejskiego Banku Centralnego.
- **[Open-Meteo](https://open-meteo.com/)** (`api.open-meteo.com`) — bieżąca temperatura dla podanych współrzędnych.

## Delegaci

| Bean | Klasa | Działanie |
|---|---|---|
| `validateDataDelegate` | `ValidateDataDelegate` | Waliduje `data`, ustawia `validationResult`, rzuca `BpmnError` gdy niepoprawne |
| `processDataDelegate` | `ProcessDataDelegate` | Symuluje pracę (1s), ustawia `processed` i `processedAt` |
| `sendNotificationDelegate` | `SendNotificationDelegate` | Wysyła powiadomienie na `email`, ustawia `notificationSent` |
| `rejectDelegate` | `RejectDelegate` | Powiadomienie o odrzuceniu, ustawia `rejectionNotificationSent` |
| `fetchExchangeRateDelegate` | `FetchExchangeRateDelegate` | Pobiera kurs z Frankfurter API; czyta `amount`/`fromCurrency`/`toCurrency`, ustawia `exchangeRate`, `convertedAmount`, `rateDate` |
| `fetchWeatherDelegate` | `FetchWeatherDelegate` | Pobiera pogodę z Open-Meteo; czyta `latitude`/`longitude` (domyślnie Warszawa), ustawia `temperature`, `weatherFetchedAt` |

## REST API

### Procesy

| Metoda | Endpoint | Opis |
|---|---|---|
| `POST` | `/api/process/start/{processDefinitionKey}` | Uruchamia proces (opcjonalne zmienne w body) |
| `GET` | `/api/process/instance/{processInstanceId}` | Informacje o instancji procesu |

### User Tasks

| Metoda | Endpoint | Opis |
|---|---|---|
| `GET` | `/api/process/tasks?assignee=demo` | Lista zadań przypisanych do użytkownika |
| `GET` | `/api/process/task/{taskId}` | Szczegóły zadania wraz ze zmiennymi |
| `POST` | `/api/process/task/{taskId}/complete` | Kończy zadanie (opcjonalne zmienne w body) |
| `POST` | `/api/process/task/{taskId}/claim?userId=demo` | Przypisuje zadanie użytkownikowi |

### Historia

| Metoda | Endpoint | Opis |
|---|---|---|
| `GET` | `/api/process/history/{processInstanceId}` | Historia aktywności instancji procesu |

## Przykłady

Uruchomienie procesu automatycznego:

```bash
curl -X POST http://localhost:8080/api/process/start/Operation-Automatic \
  -H "Content-Type: application/json" \
  -d '{"data":"test","email":"user@example.com"}'
```

Uruchomienie procesu z zadaniem użytkownika:

```bash
curl -X POST http://localhost:8080/api/process/start/Operation-WithUserTasks \
  -H "Content-Type: application/json" \
  -d '{"data":"test","email":"user@example.com"}'
```

Pobranie zadań i zakończenie z decyzją:

```bash
curl "http://localhost:8080/api/process/tasks?assignee=demo"

curl -X POST http://localhost:8080/api/process/task/{taskId}/complete \
  -H "Content-Type: application/json" \
  -d '{"approved":true}'
```

Wymiana walut (Frankfurter API — zatrzyma się na zadaniu „Review Exchange"):

```bash
curl -X POST http://localhost:8080/api/process/start/Operation-CurrencyExchange \
  -H "Content-Type: application/json" \
  -d '{"amount":100,"fromCurrency":"USD","toCurrency":"EUR","email":"user@example.com"}'

# następnie: pobierz zadanie i zaakceptuj wymianę
curl "http://localhost:8080/api/process/tasks?assignee=demo"
curl -X POST http://localhost:8080/api/process/task/{taskId}/complete \
  -H "Content-Type: application/json" -d '{"approved":true}'
```

Sprawdzenie pogody (Open-Meteo API — proces automatyczny):

```bash
curl -X POST http://localhost:8080/api/process/start/Operation-WeatherCheck \
  -H "Content-Type: application/json" \
  -d '{"latitude":52.23,"longitude":21.01,"email":"user@example.com"}'
```

## Struktura projektu

```
src/main/java/com/devapo/operaton_demo
├── Application.java
├── controller/ProcessController.java
├── delegate/
│   ├── ValidateDataDelegate.java
│   ├── ProcessDataDelegate.java
│   ├── SendNotificationDelegate.java
│   ├── RejectDelegate.java
│   ├── FetchExchangeRateDelegate.java
│   └── FetchWeatherDelegate.java
└── config/CamundaConfig.java

src/main/resources/
├── application.yml
├── Operation-Automatic.bpmn20.xml
├── Operation-WithUserTasks.bpmn20.xml
├── Operation-CurrencyExchange.bpmn20.xml
└── Operation-WeatherCheck.bpmn20.xml

docker-compose.yml
pom.xml
```
