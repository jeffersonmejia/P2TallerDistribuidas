# Restaurant DBMS

Sistema académico de gestión de órdenes y pagos simulados para demostrar transacciones PostgreSQL, SOLID, Proxy, Observer, Adapter y Circuit Breaker.

## Requisitos

- Java 25 y Maven 3.9+
- Node.js 20.19+ y npm
- Docker Desktop / Docker Engine con Compose v2
- PostgreSQL 16 se ejecuta en Docker; backend, frontend y simulador se ejecutarán localmente.

## Configuración segura y arranque

1. Copia `.env.example` como `.env` y asigna un secreto local fuerte a `POSTGRES_PASSWORD`; pon el mismo valor en `DB_PASSWORD`. No compartas ni subas `.env`.
2. Ejecuta `docker compose up -d postgres`. Compose publica PostgreSQL solo en `127.0.0.1:5432`, para que el backend local acceda sin exponerlo a la LAN. El contenedor tiene límites de 1 CPU/768 MiB y usa una red bridge dedicada. El backend y Vite también escuchan solo en loopback.
3. En una terminal de PowerShell inicia el simulador independiente: `$env:PAYMENT_SIMULATOR_DEMO='true'; npm --prefix payment-simulator start`. Escucha en `127.0.0.1:8090`; los controles de laboratorio quedan habilitados solo en ese proceso local.
4. En otra terminal inicia el backend con `./scripts/start-backend.ps1 -Profile demo`. Flyway crea esquema y seeds al primer arranque. Salud: `http://localhost:8080/api/health`.
5. En `client/`, ejecuta `npm ci` y `npm run dev`; Vite informa la URL local.

Para uso normal local sin controles de laboratorio, arranca el backend con `./scripts/start-backend.ps1 -Profile local`; el simulador puede ejecutarse con controles apagados. La autorización es simulada: nunca realiza cargos reales.

El `.env` real de este workspace contiene credenciales generadas localmente y está excluido por `.gitignore`. `.env.example` es una plantilla, no contiene el secreto.

Para parar PostgreSQL: `docker compose down`. Para borrar deliberadamente todos los datos del volumen local: `docker compose down -v`.

## Migraciones y esquema original

Los scripts fuente y sus hashes están en `database/original/`; el análisis está en `database/migration-notes.md`. Flyway ejecuta `V1` (DDL original), `V2` (seeds) y `V3` (`Audit_Events`). No ejecuta `003_queries_examples.sql`, que contiene UPDATE/DELETE. Hibernate usa `ddl-auto=validate`; no crea ni corrige tablas. No ejecutes los originales manualmente encima de una BD ya migrada.

## Contratos y etapas

Los contratos de órdenes/pagos/simulador están en [`contracts.md`](contracts.md). La etapa 02 incorpora la gestión web de órdenes. La etapa 03 añade el simulador HTTP, la auditoría persistente, un Proxy explícito y dos observadores post-commit. La etapa 04 añade el Laboratorio local con escenarios independientes y evidencia SQL. La matriz de aceptación y resultados está en [`docs/04_VALIDATION_REPORT.md`](docs/04_VALIDATION_REPORT.md).

## Órdenes y API

La vista Órdenes permite filtrar por número, cliente, producto o estado; paginar resultados; crear órdenes en línea o en mesa; consultar detalles; reemplazar productos; cancelar órdenes pendientes y confirmar un pago simulado. Los totales que aparecen mientras se edita son estimados: el backend vuelve a calcular el importe con los precios actuales del catálogo.

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/orders?page=0&size=20&search=` | Listar y filtrar órdenes paginadas |
| `GET` | `/api/orders/{id}` | Consultar detalle y pago |
| `POST` | `/api/orders` | Crear orden pendiente |
| `PUT` | `/api/orders/{id}/items` | Reemplazar productos de una orden pendiente |
| `DELETE` | `/api/orders/{id}` | Cancelar lógicamente una orden pendiente |
| `POST` | `/api/orders/{id}/payments` | Autorizar con el simulador HTTP y confirmar el pago |
| `GET` | `/api/customers?search=` | Consultar clientes para órdenes en línea |
| `GET` | `/api/catalog/menu-items?search=` | Consultar productos y precios actuales |
| `GET` | `/api/audit/events?limit=100` | Consultar los últimos resultados auditados y confirmaciones post-commit |

La aplicación convierte `authorize(orderId, amount, idempotencyKey)` al contrato incompatible del simulador `executeTransaction(value, code, operationKey)`. El Circuit Breaker se configura con `PAYMENT_BREAKER_WINDOW_SIZE`, `PAYMENT_BREAKER_MINIMUM_CALLS`, `PAYMENT_BREAKER_FAILURE_THRESHOLD`, `PAYMENT_BREAKER_OPEN_WAIT_MS` y `PAYMENT_BREAKER_HALF_OPEN_CALLS`; sus valores iniciales son 4, 2, 50%, 2000 ms y 1.

Con el simulador en demo, `POST http://127.0.0.1:8090/api/control/failures` con `{"count":2}` hace fallar las próximas dos llamadas. Revisa `GET http://127.0.0.1:8080/api/demo/payments/circuit-breaker` para el estado real; OPEN rechaza antes de enviar una solicitud al simulador. Para recuperar, deja disponible el simulador (`PUT /api/control/availability` con `{"available":true}`), espera el periodo OPEN y realiza otra autorización para probar HALF_OPEN. Sin reintentos automáticos.

### Laboratorio y pruebas académicas

Abre `http://127.0.0.1:5173/laboratory` con el backend en perfil `demo` y el simulador local con controles demo. Cada escenario genera un `LAB04` con su correlación, respuesta HTTP y lectura directa de filas PostgreSQL. Las órdenes del panel usan al cliente persistente `LABORATORIO DEMO` (`lab04@restaurant.invalid`) para distinguirlas de las órdenes ordinarias. El panel ejecuta CRUD lógico, COMMIT, ROLLBACK posterior a escrituras, consistencia, concurrencia real por HTTP, Proxy, Observer, Adapter y los estados del breaker.

Endpoints locales del laboratorio:

| Método | Ruta | Uso |
| --- | --- | --- |
| `GET` | `/api/demo/laboratory/scenarios` | Escenarios disponibles en perfil `demo` |
| `GET` | `/api/demo/laboratory/state` | Estado del simulador, breaker y conteos SQL |
| `POST` | `/api/demo/laboratory/runs/{scenario}` | Ejecutar un escenario (`crud`, `commit`, `rollback`, `consistency`, `concurrency`, `durability`, `proxy`, `observer`, `adapter`, `breaker-open`, `breaker-recovery`, `breaker-reopen`) |
| `POST` | `/api/demo/laboratory/durability/{orderId}` | Verificar una orden después de reiniciar backend y PostgreSQL |

El controlador del laboratorio solo se registra bajo perfil `demo` y además rechaza solicitudes cuyo origen no sea loopback. La orden `durability` entrega un ID; reinicia el backend y ejecuta `docker compose restart postgres` conservando el volumen. Espera a que el contenedor quede `healthy` y verifica ese ID desde el mismo panel. No uses `docker compose down -v` durante la prueba.

Para simular un error SQL después de insertar el pago y cambiar el estado, llama a `POST /api/orders/{id}/payments?simulateFailure=true`. Solo está habilitado en perfiles `demo` y `test`; el pago y el estado se revierten y no se publica evento `ORDER_COMPLETED`.

El proxy registra cada intento por separado del commit, con actor `DEMO_OPERATOR` y correlación. Tras commit, `AuditObserver` persiste `ORDER_COMPLETED` y `NotificationObserver` informa por consola (en memoria bajo tests). Errores de notificación no revierten pagos.

## Pruebas y comandos

- Backend: `mvn -f app/pom.xml test` y `mvn -f app/pom.xml package` (las pruebas de integración usan Testcontainers/PostgreSQL aislado).
- Frontend: `npm --prefix client run lint` y `npm --prefix client run build`.
- Simulador: `npm --prefix payment-simulator test`.
- Ver configuración de Docker: `docker compose config`.

La evidencia de aceptación del entorno validado se guarda en `docs/04_VALIDATION_REPORT.md`; el backup se restaura como se explica en `database/backup/README.md`.

## Variables

Ver `.env.example`: `POSTGRES_*` configura el contenedor; `DB_*`, `SERVER_PORT`, `SERVER_ADDRESS` y `SPRING_PROFILES_ACTIVE` configuran el backend. El simulador usa `PAYMENT_SIMULATOR_PORT` y `PAYMENT_SIMULATOR_DEMO`; el backend acepta la URL y los tiempos HTTP/breaker vía variables `PAYMENT_*`. Perfiles: `local` predeterminado, `test` y `demo`. No hay autenticación; `DEMO_OPERATOR` es únicamente el actor de auditoría académica.

