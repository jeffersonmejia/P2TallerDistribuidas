# Propiedad de archivos para integración

Base común preparada por etapa 01. No se inician tareas 02/03 hasta que el usuario acepte esta puerta e integre esta base.

| Área | Propietario por defecto | Archivos compartidos con aprobación del coordinador |
| --- | --- | --- |
| Bootstrap, configuración backend | Integración / etapa 01 | `app/pom.xml`, `app/src/main/resources/application*.properties` |
| Base de datos | Integración / etapa 01 | `database/**`, Flyway |
| Contratos públicos | Integración / coordinador | `contracts.md`, DTO públicos de orders/payments |
| orders y UI de órdenes | etapa 02 | `app/.../features/orders/**`, `client/src/features/orders/**` |
| payments, audit, simulador y patrones | etapa 03 | `app/.../features/payments/**`, `audit/**`, `payment-simulator/**` |
| Shell de cliente | Integración / etapa 01 | `client/src/app/**`, `client/src/shared/**`, `client/package.json` |

