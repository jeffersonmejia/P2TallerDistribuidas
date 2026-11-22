import { useCallback, useEffect, useState } from 'react';
import { AlertCircle, Check, Clock3, LoaderCircle, RefreshCw, ShieldCheck } from 'lucide-react';
import { requestJson } from '../../shared/api/apiClient.js';
import styles from './AuditPage.module.css';

const dateTime = new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium', timeStyle: 'short' });
const actions = { PAYMENT_ATTEMPT: 'Intento de pago', ORDER_COMPLETED: 'Orden completada' };
const results = { CONFIRMED: 'Confirmado', REJECTED: 'Rechazado', FAILED: 'Fallido', COMMITTED: 'Confirmado en base de datos' };

export default function AuditPage() {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setEvents(await requestJson('/api/audit/events?limit=100'));
      setUpdatedAt(new Date());
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  return <section className={styles.page} aria-labelledby="audit-title">
    <header className={styles.header}>
      <div><h1 id="audit-title">Auditoría</h1><p>Intentos de pago y confirmaciones que quedaron registradas en PostgreSQL.</p></div>
      <button className={styles.refresh} onClick={refresh} disabled={loading}><RefreshCw size={17} className={loading ? styles.spin : ''}/> Actualizar</button>
    </header>
    {error && <div className={styles.error} role="alert"><AlertCircle size={18}/>{error}</div>}
    {updatedAt && !error && <p className={styles.updated}><Clock3 size={15}/> Actualizado {dateTime.format(updatedAt)}</p>}
    <div className={styles.summary}><ShieldCheck size={20}/><span>El resultado del intento se registra aparte de la transacción del pago. Solo “Orden completada” representa un pago confirmado tras commit.</span></div>
    <div className={styles.tableWrap}>
      <table><thead><tr><th scope="col">Fecha y hora</th><th scope="col">Evento</th><th scope="col">Resultado</th><th scope="col">Orden</th><th scope="col">Actor</th><th scope="col">Correlación</th></tr></thead>
        <tbody>{loading ? <tr><td colSpan="6" className={styles.message}><LoaderCircle className={styles.spin} size={20}/> Cargando auditoría…</td></tr> : events.length ? events.map((event) => <tr key={event.id}>
          <td className={styles.date}>{dateTime.format(new Date(event.occurredAt))}</td>
          <td>{actions[event.action] ?? event.action}{event.amount && <small>{new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' }).format(event.amount)}</small>}</td>
          <td><span className={`${styles.result} ${event.result === 'CONFIRMED' || event.result === 'COMMITTED' ? styles.success : event.result === 'REJECTED' ? styles.rejected : styles.failed}`}>{event.result === 'COMMITTED' && <Check size={14}/>} {results[event.result] ?? event.result}</span></td>
          <td>{event.orderId ? `#${event.orderId}` : '—'}</td><td>{event.actor}</td><td className={styles.correlation}>{event.correlationId}</td>
        </tr>) : <tr><td colSpan="6" className={styles.message}>Todavía no hay eventos de auditoría.</td></tr>}</tbody>
      </table>
    </div>
  </section>;
}
