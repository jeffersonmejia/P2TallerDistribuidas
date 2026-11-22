# Auditoría del esquema de referencia

Inspección de `001_init.sql`, `002_seed_data.sql` y `003_queries_examples.sql` en la fuente indicada en `original/SOURCE.md`.

## Identificadores y estructura

PostgreSQL conserva mayúsculas/minúsculas de nombres de tablas y columnas citados; los mapeos JPA deben usar los nombres exactos. Tablas: `Customers`, `Order_Status`, `Guest_Tables`, `Orders`, `Menu_Items`, `Payments`, `Reservations`, `Order_Items`, `Menu_Categories`, `Reservation_Status`.

Claves identity `INT`: `Customers.customer_id`, `Order_Status.status_id`, `Guest_Tables.table_id`, `Orders.order_id`, `Menu_Items.item_id`, `Payments.payment_id`, `Reservations.reservation_id`, `Menu_Categories.category_id`, `Reservation_Status.reservation_status_id`. `Order_Items` tiene PK compuesta `(order_id, item_id)`.

Tipos monetarios: `Menu_Items.price DECIMAL(5,2)` y `Payments.amount DECIMAL(6,2)`. Fechas: `Orders.order_date DATE`, `Payments.payment_date DATE`, `Reservations.reservation_time TIMESTAMP`.

## Relaciones y restricciones

- `Orders.customer_id → Customers`, `Orders.status_id → Order_Status`, `Orders.table_id → Guest_Tables`; check exige cliente o mesa.
- `Order_Items.order_id → Orders`, `Order_Items.item_id → Menu_Items`; check `quantity > 0`.
- `Payments.order_id → Orders` es `UNIQUE NOT NULL`; check `amount > 0`.
- `Menu_Items.category_id → Menu_Categories`; check `price > 0`; `name` único y no nulo.
- `Customers.email` y `phone_number` son únicos; check requiere al menos uno.
- `Guest_Tables.capacity > 0`; `table_status` acepta `available`, `occupied`, `reserved`.
- Reservas tienen FK a clientes, mesas y `Reservation_Status`; `party_size > 0`.
- Las FKs son `DEFERRABLE INITIALLY IMMEDIATE`; el DDL crea índices explícitos para las FKs indicadas.
- Los estados seed de órdenes son `pending`, `in-progress`, `almost-ready`, `completed`, `canceled`. La aplicación resolverá IDs por nombre, no supondrá valores numéricos.

## Seeds y consultas de ejemplo

Los seeds insertan 5 clientes, 6 mesas, 5 categorías, 10 productos, 5 estados de reserva, 5 reservas, 7 órdenes, 5 detalles y 7 pagos. Hay datos históricos y pagos para órdenes seed; no deben presentarse como órdenes demo pendientes ni alterarse en el bootstrap. Los INSERT no son idempotentes por sí solos, por lo que Flyway los aplica una sola vez.

`003_queries_examples.sql` define función/trigger y vista, además de SELECT, UPDATE y DELETE. Se archiva pero no se ejecuta automáticamente: varios DELETE borran órdenes, pagos, detalles y menú.

## Decisiones etapa 01 y extensiones

Se copia el DDL original a `V1` y los seeds a `V2`, sin mutar el esquema legado. La etapa 01 no agregó tabla de auditoría. La etapa 03 incorpora `V3__payment_audit_events.sql` con `Audit_Events` para registrar intentos/resultados y eventos confirmados post-commit, sin alterar las tablas originales ni hacer que el audit trail participe en la transacción del pago. La cancelación mapea a `canceled`; `Payments.order_id` ya impone unicidad. Hibernate valida sin modificar (`ddl-auto=validate`).

