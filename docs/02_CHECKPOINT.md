# Checkpoint — Etapa 02

## Estado al 2026-11-22

La implementación y las verificaciones de etapa 02 están completas y listas para revisión de la puerta de integración. **No se ha avanzado a etapa 03 ni se han ejecutado commits.** El detalle de alcance y evidencias está en `docs/02_BUSINESS_REPORT.md`.

## Entregado

- UI de órdenes conectada a PostgreSQL: búsqueda, paginación, alta, detalle, edición de productos, cancelación lógica y pago simulado.
- Backend de órdenes, clientes y menú con validación, importes calculados en servidor, bloqueo pesimista y restricción única de pagos.
- Inyección de rollback posterior al `flush`, limitada a perfiles `demo`/`test`.
- Integraciones Testcontainers para CRUD, pago normal/duplicado, rechazo del gateway, rollback HTTP, orden vacía y concurrencia sincronizada, repetida tres veces.
- Manejo 400 localizado para parámetros inválidos; documentación de APIs y modo demo.

## Verificaciones observadas

| Comando | Resultado |
| --- | --- |
| `mvn package` | Exit 0; 16 pruebas, 0 fallos, 0 errores; PostgreSQL 16 con Testcontainers; `BUILD SUCCESS`, JAR ejecutable generado |
| `npm run lint` en `client/` | Exit 0 |
| `npm run build` en `client/` | Exit 0 |
| Análisis sintáctico de `scripts/start-backend.ps1` | Correcto |

## Corrección significativa

La prueba de edición descubrió que las filas de `Order_Items` reinsertadas con IDs compuestos se trataban como existentes en el contexto JPA tras un borrado masivo. El adaptador ahora inserta entidades nuevas de forma explícita y el repositorio limpia el contexto tras el `DELETE`. El caso CRUD pasó después de la corrección.

## Puerta y siguiente acción

La etapa 02 fue tomada como base para continuar con la instrucción del usuario de culminar la etapa 03. Su alcance funcional y sus pruebas están descritos arriba; la etapa 03 amplía pagos con simulador, auditoría y patrones sin cambiar los contratos congelados de órdenes.

## Archivos de referencia

- Requisitos: `docs/02_BUSINESS.md`
- Informe de etapa: `docs/02_BUSINESS_REPORT.md`
- Contratos congelados: `contracts.md`
- UI: `client/src/features/orders/OrdersPage.jsx`
- Backend: `app/src/main/java/edu/restaurant/features/orders/`
