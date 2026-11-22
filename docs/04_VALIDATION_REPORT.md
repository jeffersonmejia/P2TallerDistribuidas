# Informe de cierre — Etapa 04

Fecha de validación: 22 de noviembre de 2026. Entorno: Windows, Java 25.0.5, Spring Boot 3.5.6, PostgreSQL 16.15 en Docker, Node.js 22, npm, React 19 y Vite 7. La API, el simulador y la interfaz se ejecutaron en loopback. El proveedor autorizó operaciones simuladas; no hubo cobros reales.

Repositorio: [https://github.com/jeffersonmejia/P2TallerDistribuidas](https://github.com/jeffersonmejia/P2TallerDistribuidas).

## Matriz de aceptación

| Caso | Evidencia observada | Resultado |
| --- | --- | --- |
| AT-01 CRUD | El panel creó, consultó, editó y canceló una orden; SQL mostró el estado `canceled` y conservó sus filas. | Aprobado |
| AT-02 COMMIT | Orden `#20`: estado `completed`, un pago y un evento `ORDER_COMPLETED`; respuesta del pago HTTP 201. | Aprobado |
| AT-03 ROLLBACK | Orden `#19`: respuesta de pago HTTP 500 después del `flush`; SQL dejó estado `pending`, 1 detalle, 0 pagos y 0 eventos de completado. Captura `docs/evidence/AT03-rollback.png`. | Aprobado |
| AT-04 Consistencia | HTTP 400 para cantidad cero, HTTP 404 para una orden inexistente y conflicto de importe autorizado; no se guardó pago. | Aprobado |
| AT-05 Concurrencia | Dos clientes HTTP sincronizados sobre una misma orden: una respuesta 201, otra 409; PostgreSQL conservó exactamente un pago. La prueba de integración repite la carrera tres veces. | Aprobado |
| AT-06 Durabilidad | Se guardó la orden demo `#21`, se reinició backend y PostgreSQL con volumen conservado; endpoint de verificación y SQL posterior al reinicio confirmaron `completed`, 1 pago y 1 evento. | Aprobado |
| AT-07 Proxy | `PaymentServiceProxy` registró el actor `DEMO_OPERATOR`, clave idempotente y correlación; el pago terminó confirmado. | Aprobado |
| AT-08 Observer | `AuditObserver` persistió `ORDER_COMPLETED` después del commit y `NotificationObserver` escribió la notificación. La suite verifica ambos y que rollback no publique notificaciones. | Aprobado |
| AT-09 Adapter | El adaptador tradujo `authorize(orderId, amount, idempotencyKey)` al contrato externo `value/code/operationKey`; el simulador respondió con referencia y no se insertó pago. | Aprobado |
| AT-10 Breaker OPEN | Fallos HTTP del simulador llevaron el breaker de CLOSED a OPEN; la llamada bloqueada incrementó `notPermittedCalls` y no aumentó `callCount` externo. | Aprobado |
| AT-11 Recuperación | Tras la espera real, el breaker pasó por HALF_OPEN y cerró con una autorización simulada exitosa. | Aprobado |
| AT-12 Reapertura | El fallo HTTP de la sonda HALF_OPEN hizo regresar el breaker a OPEN. | Aprobado |
| AT-13 Restauración | `pg_dump -Fc` se restauró en `acceptance_restore`, una base nueva; se verificaron 12 tablas, 42 restricciones, 22 órdenes, 20 detalles, 15 pagos y 23 eventos. | Aprobado |
| AT-14 Reconstrucción | ZIP extraído en una carpeta temporal nueva; 175 archivos, 0 rutas excluidas. `npm ci`, lint/build del frontend, 4 pruebas del simulador y `mvn test` (27) pasaron desde la extracción. | Aprobado |

Las órdenes `LAB04` se asocian al cliente idempotente `LABORATORIO DEMO` (`lab04@restaurant.invalid`). Al regenerar el dump, PostgreSQL registró 6 órdenes con esa etiqueta. El usuario de auditoría `DEMO_OPERATOR` es un actor académico fijo, no una cuenta autenticada.

## Comprobaciones ejecutadas

| Comando | Resultado |
| --- | --- |
| `mvn -q test` en `app/` | Exit 0; 27 pruebas, 0 fallos, 0 errores; Testcontainers con PostgreSQL 16. |
| `npm run lint` en `client/` | Exit 0. |
| `npm run build` en `client/` | Exit 0; Vite generó el build de producción. |
| `npm test` en `payment-simulator/` | Exit 0; 4 pruebas, 0 fallos. |
| `mvn -q -DskipTests compile` en `app/` | Exit 0; Java 25 compiló las fuentes del laboratorio. |
| `docker compose restart postgres` + reinicio backend | Flyway validó las tres migraciones sin resembrar ni recrear el esquema; `GET /api/demo/laboratory/durability/21` respondió `VERIFIED`. |
| Perfil `local` | `/api/health` respondió UP, `/api/orders` respondió 200 y `/api/demo/laboratory/scenarios` respondió 404 sin stack trace. |
| `pg_dump -Fc` y `pg_restore` | Dump custom legible y restaurado desde stdin a una base vacía, con conteos SQL anteriores. |

## Laboratorio y seguridad

La pantalla única es `client/src/features/demonstrations/LaboratoryPage.jsx`. Cada resultado incluye concepto, timestamp, correlación, estado, respuestas HTTP, conteos antes/después y una lectura SQL de la orden. La simultaneidad se dispara en backend desde dos solicitudes HTTP coordinadas. El breaker usa el objeto real de Resilience4j; no se simulan sus estados en la interfaz.

El controlador `/api/demo/laboratory` solo se registra bajo perfil `demo` y valida que el origen sea loopback. El simulador también enlaza a `127.0.0.1`; sus controles requieren `PAYMENT_SIMULATOR_DEMO=true`. Los errores HTTP al cliente no incluyen stack trace ni SQL. Los detalles de excepciones del servidor quedan en logs para diagnóstico.

## Comandos para reconstrucción desde el ZIP

```powershell
Expand-Archive Proyecto_MejiaChavezJeffersonPaul.zip .\ProyectoReconstruido
Copy-Item .\ProyectoReconstruido\.env.example .\ProyectoReconstruido\.env
docker compose -f .\ProyectoReconstruido\compose.yaml up -d postgres
npm ci --prefix .\ProyectoReconstruido\client
npm --prefix .\ProyectoReconstruido\payment-simulator test
npm --prefix .\ProyectoReconstruido\client run lint
npm --prefix .\ProyectoReconstruido\client run build
mvn -f .\ProyectoReconstruido\app\pom.xml test
```

Asigna una clave local a `.env` antes de iniciar el backend. Para reproducir el reinicio no ejecutes `docker compose down -v`; el volumen persistente es la evidencia de durabilidad. La restauración se documenta en `database/backup/README.md`.

## Límites

- El `Observer` notifica después del commit; una outbox durable queda fuera del alcance académico.
- El simulador HTTP es determinista y local; no representa un adquirente ni realiza movimientos de dinero.
- Los comandos de reinicio y restauración se ejecutaron en este workspace local; Moodle y permisos institucionales no forman parte de la automatización.

## Archivos de entrega

- PDF: `output/pdf/Taller_Consistencia_MejiaChavezJeffersonPaul.pdf`.
- ZIP: `output/zip/Proyecto_MejiaChavezJeffersonPaul.zip`.
- Backup incluido en el ZIP: `database/backup/BD_MejiaChavezJeffersonPaul.backup`.
- Mensajes de commit sugeridos: `feat: add local academic demonstration laboratory`, `test: verify stage four acceptance and durability`, `docs: complete stage four validation and delivery`, `build: package academic report and project archive`.
