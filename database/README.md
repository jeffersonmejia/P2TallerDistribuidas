# Base de datos y migraciones

La fuente original es [`ericsolor/restaurant-dbms-postgresql`](https://github.com/ericsolor/restaurant-dbms-postgresql), rama `main`. Los archivos en [`original/`](original/) se descargaron sin cambios desde `migrations/`; los SHA-256 están en [`original/SOURCE.md`](original/SOURCE.md).

Flyway ejecutará `V1__original_schema.sql` y `V2__original_seed_data.sql`, copias del DDL y seeds. `003_queries_examples.sql` se archiva, pero no forma parte del bootstrap porque contiene UPDATE y DELETE. En un volumen nuevo Flyway aplica cada versión una vez; al reiniciar usa su historial y no repite los seeds.

No se reescriben tablas ni se añaden estados/campos en la etapa 01. `Payments.order_id` ya es `UNIQUE NOT NULL`; los seeds incluyen órdenes pagadas/completadas y deben preservarse. Una migración de auditoría o extensión del esquema requiere contrato y versión posterior.

Para reiniciar desde cero en desarrollo: `docker compose down -v` elimina el volumen local completo. Después `docker compose up -d` y arranca el backend para que Flyway inicialice el esquema. No ejecutar manualmente los scripts originales sobre una BD migrada.

