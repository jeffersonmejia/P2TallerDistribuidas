# Contratos de integración entre órdenes, pagos y simulador

Estado: contratos base de etapa 01 implementados y ampliados en etapa 03. Cambios posteriores requieren coordinación del dueño de integración.

## Estados de órdenes

- Persistencia conserva el catálogo original `Order_Status`: `pending`, `in-progress`, `almost-ready`, `completed`, `canceled`.
- Para el flujo del taller, el único estado editable/pagable inicial es `pending`; el pago confirmado lo cambia a `completed`; cancelación lógica usa `canceled`.
- Las órdenes originales con otros estados no se transforman durante bootstrap. La aplicación resuelve `status_id` consultando `status_name`, sin IDs hardcodeados.
- `completed` y `canceled` son terminales. Solo `pending` permite editar o cancelar. Confirmar exige `pending` y al menos un ítem.

## API HTTP prevista

Base `/api`. Todos los importes son decimal JSON y se procesan con `BigDecimal`; el backend recalcula el total con precios actuales del catálogo y cantidades enteras positivas.

- `GET /api/health` retorna `{ "status": "UP", "service": "restaurant-app", "timestamp": "..." }`.
- `GET /api/orders?page=0&size=20&search=` lista órdenes paginadas; límites y filtros se implementan en etapa 02.
- `POST /api/orders` acepta `{ "customerId": 1, "tableId": null, "orderType": "online", "items": [{ "itemId": 1, "quantity": 2 }] }` y crea pendiente. Cliente o mesa debe satisfacer el check original.
- `GET /api/orders/{orderId}` obtiene detalle, líneas, total calculado y estado.
- `PUT /api/orders/{orderId}/items` recibe `{ "items": [{ "itemId": 1, "quantity": 2 }] }`; reemplaza líneas solo en pendiente.
- `DELETE /api/orders/{orderId}` cancela lógicamente; no elimina filas. Completada no se altera.
- `POST /api/orders/{orderId}/payments` recibe `{ "paymentMethod": "credit", "idempotencyKey": "..." }`. Importe no se acepta del cliente; respuesta `201` `{ "paymentId": 10, "orderId": 1, "amount": 25.98, "status": "completed" }`.

Errores tienen forma `{ "timestamp": "...", "status": 409, "code": "ORDER_STATE_CONFLICT", "message": "...", "correlationId": "..." }`. Códigos previstos: `VALIDATION_ERROR` (400), `NOT_FOUND` (404), `ORDER_STATE_CONFLICT` (409), `PAYMENT_ALREADY_CONFIRMED` (409), `PAYMENT_PROVIDER_UNAVAILABLE` (503), `INTERNAL_ERROR` (500). Mensajes al usuario van en español; nunca incluir SQL, stack trace ni detalles del proveedor.

## Interfaces de aplicación

- `PaymentGateway.authorize(orderId: int, amount: BigDecimal, idempotencyKey: String): PaymentAuthorization` retorna `authorized`, `reference`, `authorizedAt`. No crea una fila de `Payments` ni cambia la orden. En etapa 03 lo implementan `HttpPaymentGatewayAdapter` y el fake del perfil test.
- `PaymentAuthorization` es un resultado de autorización externa, no recibo/cobro real. Gateway debe ser idempotente por `idempotencyKey`.
- La coordinación del pago vive en aplicación de órdenes/pagos: autorización externa antes de la transacción SQL larga; después volver a cargar/bloquear y validar `pending`, sin pago; persistir pago y `completed` en la misma transacción.
- La tabla original `Payments` impone `UNIQUE NOT NULL` sobre `order_id`. `OrderEntity` y `PaymentEntity` pertenecen a sus respectivas features; ninguna feature usa repositorios JPA de otra directamente.
- Error de demostración se inyecta después de escribir/flush de orden y detalle/pago dentro de la transacción y antes del commit, solo en `demo`/`test`.

## Evento y auditoría

- Aplicación publica `OrderCompletedEvent(orderId, paymentId, amount, occurredAt, correlationId)` dentro de la transacción; los listeners solo la procesan con `AFTER_COMMIT`.
- `AuditObserver` y `NotificationObserver` son suscriptores independientes; ninguno procesa eventos de transacciones revertidas. Fallos de notificación se registran sin revertir el pago.
- El proxy persiste cada intento en una transacción `REQUIRES_NEW`, separado de los eventos de orden completada y del pago.
- `GET /api/audit/events?limit=100` devuelve eventos persistidos; límite admitido entre 1 y 200.
- Actor de auditoría: constante `DEMO_OPERATOR`, no es usuario autenticado.

## Simulador externo

Servicio independiente local en `http://localhost:8090`, contrato HTTP deliberadamente distinto del puerto interno:

- `POST /api/transactions` request `{ "value": 25.98, "code": "ORDER-1", "operationKey": "..." }`.
- Respuesta autorizada `{ "result": "APPROVED", "reference": "SIM-..." }`; rechazada `{ "result": "DECLINED", "message": "..." }`.
- `operationKey` hace idempotente la simulación. No realiza cargos reales ni requiere credenciales.
- `GET /api/health` reporta disponibilidad.
- Con `PAYMENT_SIMULATOR_DEMO=true`, los controles loopback son `GET /api/control/state`, `POST /api/control/failures` (`{"count":2}`) y `PUT /api/control/availability` (`{"available":false}`). En perfiles normales devuelven 404.
- `GET /api/demo/payments/circuit-breaker` expone estado y métricas únicamente en perfil backend `demo` y solicitudes loopback.

