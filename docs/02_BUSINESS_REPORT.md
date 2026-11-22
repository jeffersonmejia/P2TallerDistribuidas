# Informe de validación — Etapa 02

**Fecha:** 2026-11-22  
**Estado:** implementación y verificaciones de la etapa 02 terminadas; pendiente revisión de la puerta de integración. No se inició la etapa 03.

## Funcionalidad entregada

- La vista Órdenes consume la API para buscar y paginar, mostrar estados y totales, crear órdenes en línea o en mesa, abrir detalle, editar sus productos, cancelar lógicamente y confirmar un pago simulado.
- Los productos y clientes de los formularios se consultan al backend. El backend valida identificadores, cantidades y estados, y calcula el total con precios de catálogo y `BigDecimal`.
- El pago usa `PaymentGateway`; la autorización fake es idempotente por clave y sucede antes de la transacción que bloquea la orden. El finalizador vuelve a cargar la orden con `PESSIMISTIC_WRITE`, comprueba estado e importe, inserta el pago con `flush` y cambia la orden a completada. `Payments.order_id` ya tiene restricción única en el esquema original.
- El hook de fallo está habilitado solo con `demo` o `test`. Se ejecuta después de escribir y hacer `flush` del pago y estado. En el perfil normal no se expone la inyección.
- La edición de productos elimina e inserta filas con clave compuesta en una sola transacción. Se corrigió la limpieza del contexto JPA después del `DELETE` masivo para permitir reinsertar la misma clave.
- Los parámetros inválidos del controlador responden 400 con mensaje localizado; el error inyectado se registra con detalle en el servidor y la respuesta no incluye excepción, SQL ni stack trace.

## Endpoints de negocio

| Método | Ruta | Resultado |
| --- | --- | --- |
| `GET` | `/api/orders?page=0&size=20&search=` | Página estable de órdenes filtrada por número, cliente, estado o producto |
| `GET` | `/api/orders/{id}` | Detalle, productos, total y datos de pago |
| `POST` | `/api/orders` | Orden pendiente con productos opcionales |
| `PUT` | `/api/orders/{id}/items` | Reemplazo de productos solo en orden pendiente |
| `DELETE` | `/api/orders/{id}` | Cancelación lógica de orden pendiente |
| `POST` | `/api/orders/{id}/payments` | Autorización fake y confirmación transaccional |
| `GET` | `/api/customers?search=` | Clientes desde PostgreSQL |
| `GET` | `/api/catalog/menu-items?search=` | Menú y precios desde PostgreSQL |

## Evidencia ejecutada

### Backend

Comando: `mvn package` en `app/` — **exit 0**. Compiló, ejecutó pruebas, creó el JAR ejecutable `target/restaurant-app-0.1.0-SNAPSHOT.jar` y terminó `BUILD SUCCESS`.

| Suite | Pruebas | Fallos / errores |
| --- | ---: | ---: |
| Dominio de órdenes | 3 | 0 |
| Fake de pagos | 2 | 0 |
| Integración de negocio con PostgreSQL 16 Testcontainers | 9 | 0 |
| Arranque, Flyway, Hibernate `validate` y health | 1 | 0 |
| Contrato de error API | 1 | 0 |
| **Total** | **16** | **0** |

Las pruebas de integración corrieron sobre una instancia PostgreSQL 16 limpia, migrada desde V1/V2 por Flyway. Consultaron persistencia mediante `JdbcTemplate` después de los casos de uso o la llamada HTTP, fuera del contexto transaccional de escritura.

Escenarios cubiertos:

1. Crear una orden de cliente con producto sembrado; confirmar estado pendiente y total tomado del precio de PostgreSQL.
2. Reemplazar la cantidad del producto y confirmar la fila resultante; cancelar y comprobar que la orden permanece en la tabla y que ya no se puede editar.
3. Confirmar pago, verificar una fila en `Payments` y estado `completed`; un segundo intento se rechaza sin crear otra fila.
4. Provocar `simulateFailure=true` por HTTP en perfil `test`; la API devuelve error interno genérico, y consultas SQL independientes confirman cero pagos y estado `pending` tras el rollback.
5. Rechazar autorización del proveedor fake y confirmar que no hay pago y la orden queda pendiente.
6. Lanzar dos autorizaciones sincronizadas para la misma orden, con dos hilos y transacciones; repetir el escenario **tres veces**. Cada repetición produjo un pago y un resultado de conflicto controlado.
7. Rechazar el pago de una orden vacía sin insertar pago y responder 400 localizado para un parámetro de página inválido.
8. Verificar idempotencia del gateway fake para la misma clave y rechazo de reutilización para otra orden.
9. Ejercitar por HTTP `POST`, `GET`, `PUT` y `DELETE`: comprobar códigos, JSON serializado, total, productos y cancelación lógica durable.
10. Consultar por HTTP clientes y menú sembrados en PostgreSQL.

Los reportes individuales están en `app/target/surefire-reports/` (artefactos locales generados, no se incluyen como código fuente).

### Frontend y arranque

- `npm run lint` en `client/` — **exit 0**.
- `npm run build` en `client/` — **exit 0**, Vite generó el bundle de producción.
- El archivo `scripts/start-backend.ps1` pasó el análisis sintáctico de PowerShell y ahora acepta `-Profile local` o `-Profile demo`; no se inició desde este informe.
- No existe runner de pruebas de componentes frontend configurado. La verificación frontend de esta etapa cubre lint y build; no se registra una sesión manual de navegador como prueba.

## Incidencias corregidas durante la validación

- ESLint identificó un `catch` redundante en el guardado del formulario; se eliminó.
- PostgreSQL reprodujo el fallo al reinsertar un detalle con clave compuesta después de un `DELETE` JPQL masivo. El adaptador ahora persiste explícitamente entidades nuevas y el repositorio limpia el contexto de persistencia después del borrado. La prueba CRUD volvió a pasar.
- La validación de parámetros `@Validated` podía caer en el handler genérico 500; ahora devuelve 400 con un texto en español y la prueba HTTP lo comprueba.

## Limitaciones y frontera de etapa

- El pago de etapa 02 usa deliberadamente el fake. El simulador HTTP, Adapter, Proxy, Observer, Circuit Breaker y panel de patrones pertenecen a la etapa 03.
- La pantalla Auditoría continúa como placeholder hasta su etapa correspondiente.
- El informe verifica las APIs y la construcción del frontend; no certifica aún la demostración visual completa de laboratorio ni los escenarios de etapa 03/04.

## Archivos principales

- `client/src/features/orders/OrdersPage.jsx` y `OrdersPage.module.css`
- `app/src/main/java/edu/restaurant/features/orders/infrastructure/persistence/OrderPersistenceAdapter.java`
- `app/src/main/java/edu/restaurant/features/orders/infrastructure/persistence/OrderItemJpaRepository.java`
- `app/src/main/java/edu/restaurant/shared/presentation/GlobalExceptionHandler.java`
- `app/src/test/java/edu/restaurant/features/orders/`
- `README.md` y `scripts/start-backend.ps1`

No se ejecutaron comandos Git de escritura. Mensaje sugerido: `feat(orders): complete order management and transactional payment checks`.
