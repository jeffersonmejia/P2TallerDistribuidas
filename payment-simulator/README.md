# Simulador HTTP de pagos

Servicio Node.js independiente, sin dependencias de terceros y sin cargos reales. Requiere Node.js 20 o superior.

## Arranque

Desde `payment-simulator/`:

```powershell
npm start
```

El servicio escucha solo en `127.0.0.1:8090`. Se puede cambiar el puerto con `PAYMENT_SIMULATOR_PORT`. Para habilitar controles del laboratorio, establece `PAYMENT_SIMULATOR_DEMO=true` antes de arrancar; el valor predeterminado es `false`.

## Contrato interno del simulador

`POST /api/transactions` recibe `{ "value": 25.98, "code": "ORDER-1", "operationKey": "..." }` y responde `APPROVED` con referencia determinista. Repetir la misma clave y los mismos datos devuelve el mismo resultado; usar la clave con otro importe o código se rechaza con 409.

`GET /api/health` informa disponibilidad del proceso.

Con modo demo:

- `GET /api/control/state` muestra disponibilidad, fallos pendientes, llamadas recibidas y operaciones idempotentes.
- `POST /api/control/failures` acepta `{ "count": 2 }` para devolver 503 en las próximas autorizaciones.
- `PUT /api/control/availability` acepta `{ "available": false }` para simular una interrupción hasta restaurar `true`.

Los controles se limitan a conexiones loopback y devuelven 404 si no está activado modo demo.

## Pruebas

```powershell
npm test
```

Las pruebas usan el servidor HTTP local y `node:test`; no requieren Docker ni instalar dependencias.
