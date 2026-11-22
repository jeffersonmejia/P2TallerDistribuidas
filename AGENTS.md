# AGENTS.md — Restaurant DBMS

## Misión y autoridad
Implementar una aplicación académica para demostrar SOLID, Proxy, Observer, Adapter, Circuit Breaker, ACID, transacciones, concurrencia y manejo de fallos con PostgreSQL. Este documento aplica a todos los agentes y worktrees. Las instrucciones específicas de `01_FOUNDATION.md` a `04_VALIDATION.md` detallan las entregas; no reemplazan estas reglas.

## Alcance invariable
- Backend: Java 25, Spring Boot, Spring Data JPA/Hibernate, PostgreSQL, Flyway.
- Frontend: React + Vite, Material Design 3, CSS Modules; interfaz en español y código en inglés.
- Arquitectura: **features primero**, y dentro de cada feature `domain/`, `application/`, `infrastructure/`, `presentation/` según necesidad; hexagonal mediante puertos y adaptadores.
- Docker Compose ejecuta **solo PostgreSQL**. Backend, frontend y simulador HTTP de pagos se ejecutan localmente, con instrucciones reproducibles.
- No hay login; se usa identificador fijo `DEMO_OPERATOR` solo para auditoría académica. No atribuirle propiedades de autenticación.
- CRUD completo de órdenes, consultas de clientes y menú, operación pendiente → edición → pago → completada; cancelación lógica previa al pago.
- Proveedor HTTP simulado sin cobros reales, auditoría persistente; panel de pruebas individuales y suite automatizada.
- Scripts SQL originales conservados sin cambios; adaptaciones en migraciones adicionales y documentadas.
- Sin módulos adicionales de inventario, reservas, contabilidad, JWT, mensajería externa o infraestructura no requerida.

## Contratos funcionales
- Confirmar pago solo para orden pendiente, con al menos un ítem y total recalculado en backend usando `BigDecimal`.
- Un pago confirmado por orden. La concurrencia debe impedir doble confirmación mediante bloqueo transaccional sobre la misma orden y restricción única persistente.
- La cancelación no borra filas: cambia el estado a cancelada y bloquea pagos o modificaciones posteriores. No se altera una orden completada.
- La autorización HTTP simulada no equivale a un pago real. Debe ser idempotente por clave de operación. No sostener una transacción SQL abierta durante llamadas HTTP de larga duración; validar nuevamente bajo bloqueo antes de guardar.
- Error inyectado **después de escribir orden y detalle/pago y antes del commit** para evidenciar ROLLBACK de una transacción real; no confundir con una validación previa sin escrituras.
- Eventos de orden completada se emiten **después del commit**. El audit trail de intentos/rechazos se trata de forma separada y no se usa como evidencia de atomicidad de la operación.
- Un fallo externo deja la orden pendiente y no inserta pago confirmado. Circuit Breaker evidencia CLOSED → OPEN → HALF_OPEN → CLOSED (y vuelta a OPEN si falla la prueba).

## Límites arquitectónicos
- `domain/`: modelos, invariantes, lógica pura y contratos del dominio; sin imports de Spring/JPA/HTTP.
- `application/`: casos de uso, orquestación, puertos, límites transaccionales y eventos de aplicación.
- `infrastructure/`: entidades JPA/mapeadores, repositorios de persistencia, clientes HTTP, resiliencia, adaptadores y configuración técnica.
- `presentation/`: REST controllers, request/response DTOs, validación de entrada y mapeo de errores HTTP.
- Features: `orders`, `payments`, `customers`, `catalog`, `audit`, `demonstrations`; no compartir entidades JPA ni acceder a repositorios de otra feature directamente. Integración mediante interfaces públicas de aplicación.
- `shared/` solo para código transversal real (errores globales, configuración, correlación, utilidades inequívocamente compartidas). Prohibido convertirlo en depósito de lógica de negocio.
- Inyección de dependencias por constructor, contratos pequeños, sin dependencias circulares. No agregar capas o interfaces triviales sin beneficio verificable.

## SOLID y patrones verificables
- SRP: controllers, casos de uso, puertos/adaptadores, auditoría separados.
- OCP: capacidad de agregar un proveedor/forma de pago cambiando la implementación, no el caso de uso.
- LSP: dos implementaciones de un mismo contrato de notificación con pruebas que demuestren sustitución.
- ISP: puertos de propósito único, no interfaces CRUD gigantes.
- DIP: dominio/casos de uso dependen de contratos, infraestructura implementa contratos.
- Proxy: componente explícito antes del servicio importante; identificar cliente, proxy y servicio real; no confundir un interceptor genérico con una prueba completa.
- Observer: al menos dos suscriptores independientes a un evento; demostrar ambos, sin enviar confirmaciones de operaciones revertidas.
- Adapter: transformar contrato interno a API HTTP incompatible del simulador.
- Circuit Breaker: umbrales/tiempos pequeños y parametrizados para demostración, sin falsificar estados; comprobar que OPEN corta solicitudes al simulador.

## Base de datos y migraciones
- Primero inspeccionar `001_init.sql`, `002_seed_data.sql` y `003_queries_examples.sql` del repositorio de referencia, incluidos identificadores entre comillas, columnas, defaults, PK/FK, restricciones y seeds. **No asumir** nombres no verificados.
- Mantener originales en `database/original/` con atribución y URL; no editar; documentar hash o commit origen si está disponible.
- Definir un único camino reproducible de bootstrap: ejecutar SQL original de estructura y seeds una vez mediante migraciones Flyway de línea base, y migraciones versionadas posteriores; no ejecutar el script de ejemplos destructivos en bootstrap. Evitar doble inicialización manual + Flyway.
- JPA: `spring.jpa.hibernate.ddl-auto=validate`. Hibernate no crea ni corrige el esquema.
- No reescribir tablas originales para ajustarlas a las entidades. Preferir mapeos explícitos `@Table`, `@Column`, claves compuestas verificadas, y migraciones pequeñas justificadas.
- Datos de demostración distinguibles, repetibles, sin duplicados ni violaciones a restricciones.
- Backup `BD_MejiaChavezJeffersonPaul.backup` con `pg_dump -F c`, verificable con `pg_restore` en BD limpia. Nunca incluir secretos reales.

## Coordinación de agentes y worktrees
- **Etapas son puertas de integración, no cuatro ramas que deban fusionarse sin coordinación**: 01 establece contratos/esqueleto; 02 y partes desacopladas de 03 pueden desarrollarse en paralelo solo tras integrar 01; 04 depende de 02 y 03 integradas.
- El coordinador publica una versión base común antes de iniciar tareas paralelas y asigna un worktree y un dueño explícito por carpeta/archivo. Cada agente consulta `git status`, rama/base y tareas asignadas antes de modificar.
- Propiedad predeterminada: 01 `app` bootstrap, `database/`, configuración y diseño base `client`; 02 `orders`, `customers`, `catalog` y UI de órdenes; 03 `payments` infraestructura externa, `audit` y patrones; 04 `demonstrations`, pruebas integradas, evidencias, README y empaquetado. **Las fronteras `orders`/`payments` se definen y congelan al terminar 01**, con integración coordinada en 02/03.
- Ningún agente renombra, mueve o refactoriza masivamente carpetas compartidas sin permiso del coordinador. Cambios en `pom.xml`, `package.json`, Flyway, DTOs públicos o archivos globales se solicitan al dueño de integración. Registrar conflicto y proponer parche, no sobreescribir.
- Los agentes **no ejecutan `git commit`, `push`, `merge`, `rebase`, `reset --hard` ni eliminan ramas/worktrees**. El usuario realiza commits y fusiones. Se pueden sugerir mensajes Conventional Commits en el informe final de cada tarea.
- Entregas por agente: archivos tocados, contratos nuevos o alterados, pruebas ejecutadas con resultados, bloqueos, riesgos y mensaje sugerido de commit. No declarar que algo está probado si no se ejecutó.
- No avanzar a la siguiente etapa sin aceptación de la puerta de calidad de la etapa anterior.

## Calidad, errores, seguridad
- Para inspeccionar archivos y buscar contenido, preferir `rg`; para ediciones pequeñas, usar `apply_patch` con parches breves.
- Compilación + lint + pruebas de la etapa obligatorias antes de reportar lista. Preferir tests deterministas (JUnit 5, Mockito, Testcontainers con PostgreSQL) y tests de integración HTTP para el simulador.
- Manejo centralizado de errores con `@RestControllerAdvice`: responder mensajes claros en español y, si se implementa, un identificador de correlación; no exponer códigos internos, stack traces ni errores SQL al cliente. Registrar en logs del servidor el detalle técnico necesario para diagnosticar (incluida la excepción), con niveles configurables y sin datos sensibles.
- No hardcodear credenciales, tokens, contraseñas, cadenas de conexión reales. `.env.example` sí, `.env` no. Añadir `.gitignore` para `target/`, `node_modules/`, `dist/`, logs y reportes.
- El panel de demostración es **solo de laboratorio**: endpoints de inyección de fallos y estados deberán estar deshabilitados en perfil normal y activados por `demo` local, con señalización visible.
- Docker Compose debe declarar límites razonables de CPU/memoria y endurecer PostgreSQL con privilegio mínimo; publicar servicios solo en loopback cuando una app local necesite acceder, y definir una red dedicada con acceso mínimo. No poner secretos en el YAML.
- Mantener accesibilidad básica: teclado, foco, labels, estados y contraste. No estilos inline generalizados ni CSS global por feature. No generar informes PDF ni ZIP hasta la etapa 04; no implementar el sistema en la etapa de planificación.
- En el frontend, no agregar un encabezado o título secundario encima del título principal de una vista, ni repetir el título de forma redundante. Evitar encabezados genéricos que hagan que la interfaz parezca generada automáticamente.
- Cuando se implemente frontend, usar iconos de estilo iOS y mantener un conjunto visual coherente; elegir una biblioteca compatible con React/web si SF Symbols no está disponible directamente.

## Definición universal de terminado
- Código compila, lint pasa, tests ejecutados y evidencias obtenidas; consultas de PostgreSQL verifican resultados reales.
- Funcionalidad demostrable vía UI o comando documentado, sin preparación oculta.
- README contiene arranque, variables, restauración, escenarios de rollback, concurrencia y breaker.
- No hay dependencias binarias ni secretos en el entregable; el backup restaura íntegramente.
