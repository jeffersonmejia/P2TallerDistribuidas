import { useCallback, useEffect, useState } from 'react';
import { Activity, AlertCircle, Check, Clock3, LoaderCircle, Play, RefreshCw, ShieldAlert } from 'lucide-react';
import { requestJson } from '../../shared/api/apiClient.js';
import styles from './LaboratoryPage.module.css';

const time = new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium', timeStyle: 'medium' });
const groups = [
  ['Órdenes y transacciones', ['crud', 'commit', 'rollback', 'consistency', 'concurrency', 'durability']],
  ['Patrones y resiliencia', ['proxy', 'observer', 'adapter', 'breaker-open', 'breaker-recovery', 'breaker-reopen']],
];

export default function LaboratoryPage() {
  const [scenarios, setScenarios] = useState([]);
  const [status, setStatus] = useState(null);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [orderId, setOrderId] = useState('');

  const refresh = useCallback(async () => {
    setError('');
    try {
      const [availableScenarios, currentStatus] = await Promise.all([
        requestJson('/api/demo/laboratory/scenarios'), requestJson('/api/demo/laboratory/state'),
      ]);
      setScenarios(availableScenarios);
      setStatus(currentStatus);
    } catch (requestError) { setError(requestError.message); }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  async function run(id, body) {
    setBusy(id);
    setError('');
    try {
      const response = await requestJson(`/api/demo/laboratory/runs/${id}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body ?? {}),
      });
      setResult(response);
      if (response.parameters?.orderId) setOrderId(String(response.parameters.orderId));
      await refresh();
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  }

  async function verifyDurability(event) {
    event.preventDefault();
    if (!/^\d+$/.test(orderId)) { setError('Ingresa el ID numérico de la orden.'); return; }
    setBusy('verify-durability');
    setError('');
    try {
      setResult(await requestJson(`/api/demo/laboratory/durability/${orderId}`, { method: 'POST' }));
      await refresh();
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(''); }
  }

  const byId = Object.fromEntries(scenarios.map((scenario) => [scenario.id, scenario]));
  return <section className={styles.page} aria-labelledby="lab-title">
    <header className={styles.header}>
      <div><h1 id="lab-title">Laboratorio</h1><p>Escenarios académicos con datos consultados directamente en PostgreSQL.</p></div>
      <button className={styles.refresh} type="button" onClick={refresh} disabled={Boolean(busy)}><RefreshCw size={16}/> Actualizar estado</button>
    </header>
    <div className={styles.demoBanner}><ShieldAlert size={20}/><div><strong>Modo demo local · sin cobros reales</strong><span>Los escenarios escriben órdenes de laboratorio y usan el simulador HTTP local. Actor de auditoría: DEMO_OPERATOR.</span></div></div>
    {error && <div className={styles.error} role="alert"><AlertCircle size={18}/>{error}</div>}
    <section className={styles.stateBar} aria-label="Estado de servicios">
      <div><span>Simulador</span><strong>{status ? (status.simulator.available ? 'Disponible' : 'No disponible') : 'Conectando…'}</strong><small>{status ? `${status.simulator.callCount} llamadas · ${status.simulator.failuresRemaining} fallos inyectados` : ''}</small></div>
      <div><span>Circuit Breaker</span><strong className={styles[`state${status?.circuitBreaker.state}`] ?? ''}>{status?.circuitBreaker.state ?? '—'}</strong><small>{status ? `${status.circuitBreaker.notPermittedCalls} solicitudes bloqueadas` : ''}</small></div>
      <div><span>PostgreSQL</span><strong>{status ? 'Conectado' : '—'}</strong><small>{status ? `${status.database.laboratoryOrders} órdenes LAB04 · ${status.database.payments} pagos totales` : ''}</small></div>
    </section>
    {groups.map(([title, ids]) => <section className={styles.group} key={title} aria-label={title}>
      <h2>{title}</h2><div className={styles.grid}>{ids.map((id) => {
        const scenario = byId[id];
        if (!scenario) return null;
        return <article className={styles.card} key={id}><span className={styles.concept}>{scenario.concept}</span><h3>{scenario.name}</h3><p>{scenario.description}</p>
          <button type="button" className={styles.run} disabled={Boolean(busy)} onClick={() => run(id)}>{busy === id ? <LoaderCircle className={styles.spin} size={17}/> : <Play size={16}/>} Ejecutar escenario</button>
        </article>;
      })}</div>
    </section>)}
    <form className={styles.durability} onSubmit={verifyDurability}><div><h2>Verificar durabilidad después del reinicio</h2><p>Reinicia backend y PostgreSQL conservando el volumen; luego consulta aquí el ID guardado.</p></div>
      <label htmlFor="durability-order">ID de orden</label><input id="durability-order" inputMode="numeric" value={orderId} onChange={(event) => setOrderId(event.target.value)} required/>
      <button className={styles.run} disabled={Boolean(busy)}>{busy === 'verify-durability' ? <LoaderCircle className={styles.spin} size={17}/> : <Check size={16}/>} Verificar</button>
    </form>
    {result && <section className={styles.result} aria-live="polite" aria-labelledby="result-title">
      <div className={styles.resultHead}><div><span className={`${styles.badge} ${result.state === 'VERIFIED' ? styles.verified : styles.failed}`}>{result.state}</span><h2 id="result-title">{result.name}</h2><p>{result.message}</p></div><time><Clock3 size={15}/>{time.format(new Date(result.completedAt))}</time></div>
      <dl className={styles.meta}><div><dt>Concepto</dt><dd>{result.concept}</dd></div><div><dt>Correlación</dt><dd>{result.correlationId}</dd></div><div><dt>Respuesta HTTP</dt><dd>{result.httpStatus}</dd></div><div><dt>Orden</dt><dd>{result.orderEvidence?.orderId ? `#${result.orderEvidence.orderId} · ${result.orderEvidence.status}` : '—'}</dd></div></dl>
      <div className={styles.snapshots}><Snapshot title="PostgreSQL antes" value={result.before}/><Snapshot title="PostgreSQL después" value={result.after}/></div>
      {result.orderEvidence && <div className={styles.evidence}><strong>Consulta SQL de la orden #{result.orderEvidence.orderId}</strong><span>Estado: {result.orderEvidence.status ?? 'no existe'} · Detalles: {result.orderEvidence.itemCount} · Unidades: {result.orderEvidence.quantity} · Pagos: {result.orderEvidence.paymentCount} · Eventos de commit: {result.orderEvidence.completedEventCount}</span><span>Actor: {result.orderEvidence.lastAttemptActor ?? '—'} · Clave: {result.orderEvidence.lastAttemptOperationKey ?? '—'}</span></div>}
      <div className={styles.calls}><h3><Activity size={17}/> Solicitudes HTTP</h3>{result.httpCalls.length ? result.httpCalls.map((call, index) => <details key={`${call.correlationId}-${index}`}><summary><code>{call.method} {call.path}</code><span>HTTP {call.status}</span><small>{call.correlationId}</small></summary><pre>{call.body || '(sin cuerpo)'}</pre></details>) : <p>Escenario ejecutado directamente a través del puerto de pago; revisa el estado del simulador.</p>}</div>
      <div className={styles.states}><span>Simulador: {result.simulatorBefore.callCount} → {result.simulatorAfter.callCount} llamadas</span><span>Breaker: {result.breakerBefore.state} → {result.breakerAfter.state}</span></div>
    </section>}
  </section>;
}

function Snapshot({ title, value }) {
  return <div className={styles.snapshot}><h3>{title}</h3><dl>{Object.entries(value).map(([key, count]) => <div key={key}><dt>{key}</dt><dd>{count}</dd></div>)}</dl></div>;
}
