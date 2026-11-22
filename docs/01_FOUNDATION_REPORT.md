# Informe de entrega — Etapa 01

## Entrega

- Monorepo inicial: `app/`, `client/`, `payment-simulator/`, `database/`, `docs/`, `scripts/` y `contracts.md`.
- Backend Spring Boot 3.5.6 / Java 25, perfiles `local`, `test`, `demo`, endpoint `GET /api/health`, errores JSON en español con correlación y mapeo JPA explícito de `Customers`, `Orders`, `Order_Status`, `Menu_Items` y `Payments`.
- Flyway aplica el DDL fuente en V1 y seeds en V2. `ddl-auto=validate`; `PhysicalNamingStrategyStandardImpl` conserva nombres citados del legado.
- React 19 + Vite, React Router, shell Órdenes/Auditoría/Laboratorio, 404, tokens CSS MD3, módulos CSS, cliente HTTP común y navegación accesible. Iconos web Lucide de trazo sencillo, según la preferencia de estilo iOS.
- Compose solo inicia PostgreSQL 16: volumen persistente, CPU límite 1, memoria límite 768 MiB, filesystem raíz de solo lectura, `no-new-privileges`, red bridge dedicada y publicación de DB limitada a `127.0.0.1`. Backend y Vite también enlazan a loopback; no escuchan en interfaces de red externas.
- `.env` local creado con credencial generada y excluido por `.gitignore`; no registrar su valor en documentación ni consola. `.env.example` contiene solo una plantilla.
- Contrato de órdenes/pagos/eventos/simulador congelado en `contracts.md`; ownership documentado en `docs/OWNERSHIP.md`.

## Auditoría de SQL

Se archivaron los tres originales del repositorio de referencia y se conservaron idénticos a los recursos Flyway V1/V2 cuando corresponde. Los SHA-256 se documentan en `database/original/SOURCE.md`. V3 de ejemplos destructivos no se ejecuta en bootstrap. Detalles y decisiones están en `database/migration-notes.md`.

## Verificación ejecutada

| Comando / comprobación | Resultado |
| --- | --- |
| `docker compose config --quiet` | Correcto |
| `docker compose up -d postgres` | PostgreSQL inició y healthcheck pasó |
| `mvn -f app/pom.xml clean test` | Exit 0; 2 pruebas, 0 fallas, 0 omitidas; compilación con release 25 |
| `mvn -f app/pom.xml -Dtest=RestaurantApplicationTest test` con Compose detenido | Exit 0; el fallback inició PostgreSQL con Testcontainers 2.0.5, Flyway migró desde cero y Hibernate validó el esquema |
| `mvn -f app/pom.xml package -q` | Exit 0 |
| Arranque local vía `scripts/start-backend.ps1` | Flyway aplicó V1/V2; Hibernate `validate` pasó; health respondió `UP` |
| Consultas PostgreSQL tras reinicio de contenedor | 2 migraciones exitosas, 7 órdenes y 7 pagos; no se duplicaron seeds |
| `npm run lint` | Correcto |
| `npm run build` | Correcto |
| Revisión `.env` | `POSTGRES_PASSWORD` coincide con `DB_PASSWORD`; `.gitignore` contiene la exclusión local; configuración no contiene contraseñas |

## Pendiente de puerta de calidad

`RestaurantApplicationTest` usa el PostgreSQL local de Compose cuando está disponible y recurre a Testcontainers 2.0.5 para iniciar uno limpio si no encuentra una BD local. Se verificaron ambos caminos: con Compose activo y con Compose detenido; en el segundo caso Testcontainers levantó PostgreSQL y pasó la migración Flyway y la validación Hibernate. La BD persistente de Compose también se creó desde volumen vacío: Flyway aplicó V1/V2 y las consultas confirmaron los datos iniciales. La decisión del proyecto se actualizó a Java 25, JDK instalado en este equipo.

## Integración

El repositorio está inicializado en `main`; no se ejecutaron commits. La etapa 01 queda lista para revisión e integración.

Mensaje sugerido cuando se integre: `feat(foundation): establish restaurant app contracts and bootstrap`.

