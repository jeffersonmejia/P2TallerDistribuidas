# Backup de entrega

`BD_MejiaChavezJeffersonPaul.backup` se genera en formato custom de PostgreSQL con `pg_dump -Fc`. Incluye esquema, datos originales y evidencia del laboratorio. La clave no está almacenada en el backup; `.env` no forma parte de la entrega.

## Generar dentro de Docker

```powershell
docker compose exec -T postgres pg_dump -Fc -U restaurant_app -d restaurant_db > database/backup/BD_MejiaChavezJeffersonPaul.backup
```

El segundo comando sirve para listar un archivo local si `pg_restore` está instalado en el host:

```powershell
pg_restore --list database/backup/BD_MejiaChavezJeffersonPaul.backup
```

En Docker Desktop, para restaurar el archivo local cuando el contenedor no tiene un volumen de archivos compartido, redirige bytes por stdin:

```powershell
docker compose exec -T postgres psql -U restaurant_app -d postgres -c "CREATE DATABASE restaurant_restore"
cmd.exe /d /c "docker exec -i restaurant-postgres pg_restore --no-owner --no-privileges -U restaurant_app -d restaurant_restore < database\backup\BD_MejiaChavezJeffersonPaul.backup"
docker compose exec -T postgres psql -U restaurant_app -d restaurant_restore -c 'SELECT count(*) FROM "Orders";'
```

Fuera de Docker, con cliente PostgreSQL instalado, restaura en una base nueva y vacía con `createdb restaurant_restore` y `pg_restore --no-owner --no-privileges -d restaurant_restore database/backup/BD_MejiaChavezJeffersonPaul.backup`. Emplea un servidor PostgreSQL 16, el mismo major version del entorno de origen. Revisa que `Customers`, `Orders`, `Order_Items`, `Payments`, `Audit_Events`, claves foráneas y restricciones CHECK existan. No restaures encima de una base de trabajo.
