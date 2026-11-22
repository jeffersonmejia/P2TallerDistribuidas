# Informe de etapa 03 — Patrones y pagos simulados

## Resultado

La etapa incorpora Proxy, Observer post-commit, Adapter HTTP y Circuit Breaker sobre el flujo de pagos de la etapa 02. El proveedor de laboratorio es un proceso Node independiente en `127.0.0.1`; no procesa dinero real. No se identificaron requisitos faltantes en `docs/03_PATTERNS.md`.

## Principios y patrones con evidencia

| Principio/patrón | Implementación | Evidencia |
| --- | --- | --- |
| SRP / ISP | Puertos separados para pago, auditoría, notificación y lectura; controladores y adaptadores por responsabilidad | `ArchitectureDependencyTest`, `PaymentServiceProxyTest` |
| OCP / DIP | `PaymentGateway` con adaptador HTTP productivo y fake de perfil `test`; el caso de uso depende del contrato | `FakePaymentGatewayTest`, `HttpPaymentGatewayAdapterTest`, `ArchitectureDependencyTest` |
| LSP | Adaptadores de notificación de consola y memoria comparten el mismo contrato | `NotificationPortContractTest` ejecuta los mismos casos para ambos |
| Proxy | `OrdersController` → `PaymentServiceProxy` → `OrderApplicationService`; el proxy registra actor fijo, correlación y desenlace en auditoría independiente | `PaymentServiceProxyTest`, `OrderBusinessIntegrationTest` |
| Observer | Evento `OrderCompletedEvent` publicado en la transacción; `AuditObserver` y `NotificationObserver` reaccionan con `AFTER_COMMIT` | `OrderBusinessIntegrationTest` prueba ambos observadores y que rollback no los dispara |
| Adapter | `HttpPaymentGatewayAdapter` convierte `authorize(orderId, amount, key)` al contrato externo `value/code/operationKey` y traduce respuestas, HTTP, timeout y red | `HttpPaymentGatewayAdapterTest`, `PaymentFlowHttpIntegrationTest` |
| Circuit Breaker | Resilience4j configura ventana/umbral, espera OPEN y una prueba HALF_OPEN; OPEN bloquea llamadas externas | `HttpPaymentGatewayAdapterTest` verifica CLOSED → OPEN → HALF_OPEN → CLOSED y reapertura al fallar |

## Integridad y auditoría

- La migración Flyway `V3__payment_audit_events.sql` agrega `Audit_Events`; conserva las tablas originales. Intentos/rechazos y eventos de confirmación se distinguen por acción y resultado.
- Un rechazo del proveedor mantiene la orden pendiente y no inserta pago confirmado. Un reintento con la misma clave puede completar una única vez; el simulador es idempotente por clave.
- Las notificaciones ocurren después del commit. Un rollback no produce confirmación observada. La entrega post-commit no es garantía durable ante caída abrupta; una outbox sería una extensión fuera del MVP.
- No hay reintentos HTTP automáticos. El proveedor estático de laboratorio no captura cobros reales.

## Verificación

| Comando | Resultado |
| --- | --- |
| `mvn package` en `app/` | `BUILD SUCCESS`; 27 pruebas, 0 fallos, 0 errores; Testcontainers/PostgreSQL 16 e integración HTTP incluidos |
| `mvn clean compile "-Dmaven.compiler.compilerArgs=-Xlint:all" -DskipTests` | Exit 0; recompiló 86 fuentes Java de producción sin advertencias |
| Compilación Java del paquete | `-Xlint:unchecked` queda configurado en `app/pom.xml` para compilar producción y pruebas; DTOs HTTP de test usan JSON tipado |
| `npm test` en `payment-simulator/` | Exit 0; 4 pruebas, 0 fallos; idempotencia, controles de laboratorio, perfil normal y JSON inválido |
| `npm run lint` en `client/` | Exit 0 |
| `npm run build` en `client/` | Exit 0; Vite produjo build de producción |
| Verificación de proceso independiente | `GET /api/health` respondió UP; dos solicitudes con igual clave devolvieron la misma referencia simulada |

## Ejecución local

Seguir el orden del README: PostgreSQL, simulador con `PAYMENT_SIMULATOR_DEMO=true`, backend con perfil `demo` y frontend. Consultar `GET /api/audit/events`; el breaker se observa en `GET /api/demo/payments/circuit-breaker`. La inyección de fallos del simulador solo está disponible en el proceso local ejecutado en modo demo.

## Riesgos y límites

- El Observer post-commit no sustituye un outbox si se requiere entrega durable.
- Auditoría de intentos se confirma en transacción independiente para sobrevivir al rechazo o rollback del flujo de pago; no debe usarse como evidencia de atomicidad de ese flujo.
- Los endpoints de control del simulador solo están activos en modo demo y el proceso enlaza a loopback.

## Entrega

- Archivos principales: `app/` (incluye `pom.xml` y pruebas), `client/src/features/audit/`, `payment-simulator/`, `app/src/main/resources/db/migration/V3__payment_audit_events.sql`, `README.md`, `contracts.md` y este informe.
- Logs/evidencias candidatos: salida `PaymentServiceProxy delegating` con actor y correlación; notificación `OrderCompletedEvent` en la prueba de contrato; consultas SQL de auditoría y rollback en `OrderBusinessIntegrationTest`.
- Sin requisitos faltantes ni bloqueos funcionales identificados en el checkpoint de etapa 03.
- Mensaje sugerido: `feat: demonstrate payment patterns and audit events`.
- No se creó commit.
