import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertCircle, ArrowLeft, Check, ChevronLeft, ChevronRight, CirclePlus, CreditCard, LoaderCircle, Pencil, ReceiptText, Search, Trash2, X } from 'lucide-react';
import { requestJson } from '../../shared/api/apiClient.js';
import styles from './OrdersPage.module.css';

const PAGE_SIZE = 10;
const money = new Intl.NumberFormat('es-EC', { style: 'currency', currency: 'USD' });
const date = new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium' });
const statusLabels = { pending: 'Pendiente', 'in-progress': 'En preparación', 'almost-ready': 'Casi lista', completed: 'Completada', canceled: 'Cancelada' };

function OrderForm({ order, customers, menu, onCancel, onSave, busy }) {
  const [type, setType] = useState(order?.orderType ?? 'online');
  const [customerId, setCustomerId] = useState(order?.customerId ?? '');
  const [tableId, setTableId] = useState(order?.tableId ?? '');
  const [items, setItems] = useState(() => order?.items?.map(({ itemId, quantity }) => ({ itemId, quantity })) ?? []);
  const [formError, setFormError] = useState('');
  const total = useMemo(() => items.reduce((sum, line) => sum + (menu.find((item) => item.id === Number(line.itemId))?.price ?? 0) * Number(line.quantity), 0), [items, menu]);

  function setLine(index, key, value) {
    setItems((current) => current.map((line, i) => i === index ? { ...line, [key]: value } : line));
  }

  async function submit(event) {
    event.preventDefault();
    setFormError('');
    if (type === 'online' && !customerId) return setFormError('Selecciona un cliente para una orden en línea.');
    if (type === 'in-house' && !tableId) return setFormError('Indica el número de mesa.');
    const payloadItems = items.filter((line) => line.itemId && Number(line.quantity) > 0).map((line) => ({ itemId: Number(line.itemId), quantity: Number(line.quantity) }));
    if (payloadItems.length !== items.length) return setFormError('Completa el producto y una cantidad válida en cada fila.');
    try {
      await onSave({ customerId: type === 'online' ? Number(customerId) : null, tableId: type === 'in-house' ? Number(tableId) : null, orderType: type, items: payloadItems });
    } catch (error) { setFormError(error.message); }
  }

  return <form className={styles.form} onSubmit={submit}>
    <div className={styles.formTop}><div><p className={styles.eyebrow}>{order ? `Orden #${order.id}` : 'Nueva orden'}</p><h2 id="form-heading">{order ? 'Editar orden' : 'Crear orden'}</h2></div><button className={styles.iconButton} type="button" onClick={onCancel} aria-label="Cerrar formulario"><X size={20}/></button></div>
    {order ? <p className={styles.muted}>Actualiza los productos de {order.customerName ?? `la mesa ${order.tableId}`}.</p> : <>
      <label className={styles.field}>Tipo de orden<select value={type} onChange={(event) => setType(event.target.value)}><option value="online">En línea</option><option value="in-house">En mesa</option></select></label>
      {type === 'online' ? <label className={styles.field}>Cliente<select required value={customerId} onChange={(event) => setCustomerId(event.target.value)}><option value="">Seleccionar cliente</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name}</option>)}</select></label> : <label className={styles.field}>Mesa<input type="number" min="1" max="2147483647" required value={tableId} onChange={(event) => setTableId(event.target.value)} placeholder="Número de mesa"/></label>}
    </>}
    <div className={styles.itemsHeading}><h3>Productos</h3><button className={styles.textButton} type="button" onClick={() => setItems((current) => [...current, { itemId: '', quantity: 1 }])}><CirclePlus size={17}/> Añadir</button></div>
    <div className={styles.itemRows}>{items.map((line, index) => <div className={styles.itemRow} key={`${index}-${line.itemId}`}><label className={styles.srOnly} htmlFor={`product-${index}`}>Producto {index + 1}</label><select id={`product-${index}`} value={line.itemId} onChange={(event) => setLine(index, 'itemId', event.target.value)} required><option value="">Producto</option>{menu.map((item) => <option key={item.id} value={item.id}>{item.name} · {money.format(item.price)}</option>)}</select><label className={styles.srOnly} htmlFor={`quantity-${index}`}>Cantidad</label><input id={`quantity-${index}`} type="number" min="1" max="99" value={line.quantity} onChange={(event) => setLine(index, 'quantity', event.target.value)} required/><button className={styles.iconButton} type="button" onClick={() => setItems((current) => current.filter((_, i) => i !== index))} aria-label={`Quitar producto ${index + 1}`}><Trash2 size={17}/></button></div>)}</div>
    <p className={styles.totalLine}>Total estimado <strong>{money.format(total)}</strong><span>El importe final se calcula en el servidor.</span></p>
    {formError && <p className={styles.formError} role="alert"><AlertCircle size={17}/>{formError}</p>}
    <div className={styles.formActions}><button className={styles.secondaryButton} type="button" onClick={onCancel}>Volver</button><button className={styles.primaryButton} disabled={busy || items.length === 0} type="submit">{busy ? <LoaderCircle className={styles.spin} size={17}/> : <Check size={17}/>} {order ? 'Guardar cambios' : 'Crear orden'}</button></div>
  </form>;
}

function DetailPanel({ order, onClose, onEdit, onCancelOrder, onPay, busy }) {
  const payable = order.status === 'pending' && order.items.length > 0 && !order.paidAmount;
  return <div className={styles.overlay} role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}><section className={styles.drawer} role="dialog" aria-modal="true" aria-labelledby="detail-title"><div className={styles.drawerTop}><button className={styles.textButton} onClick={onClose}><ArrowLeft size={17}/> Órdenes</button><button className={styles.iconButton} aria-label="Cerrar detalle" onClick={onClose}><X size={19}/></button></div><p className={styles.eyebrow}>Detalle de orden</p><h2 id="detail-title">Orden #{order.id}</h2><div className={styles.detailMeta}><span>{date.format(new Date(`${order.orderDate}T12:00:00`))}</span><span className={`${styles.status} ${styles[order.status]}`}>{statusLabels[order.status] ?? order.status}</span></div><dl className={styles.detailList}><div><dt>Cliente</dt><dd>{order.customerName ?? `Mesa ${order.tableId}`}</dd></div><div><dt>Tipo</dt><dd>{order.orderType === 'online' ? 'En línea' : 'En mesa'}</dd></div>{order.paymentMethod && <div><dt>Pago</dt><dd>{order.paymentMethod} · {money.format(order.paidAmount)}</dd></div>}</dl><h3 className={styles.detailItemsTitle}>Productos</h3>{order.items.length ? <ul className={styles.detailItems}>{order.items.map((item) => <li key={item.itemId}><span>{item.quantity} × {item.name}</span><strong>{money.format(item.lineTotal)}</strong></li>)}</ul> : <p className={styles.muted}>Sin productos agregados.</p>}<div className={styles.detailTotal}><span>Total</span><strong>{money.format(order.total)}</strong></div><div className={styles.detailActions}>{order.status === 'pending' && <><button className={styles.secondaryButton} onClick={onEdit}><Pencil size={16}/> Editar</button><button className={styles.dangerButton} onClick={onCancelOrder}><Trash2 size={16}/> Cancelar</button>{payable && <button className={styles.primaryButton} disabled={busy} onClick={onPay}>{busy ? <LoaderCircle className={styles.spin} size={17}/> : <CreditCard size={17}/>} Confirmar pago</button>}</>}</div></section></div>;
}

export default function OrdersPage() {
  const [result, setResult] = useState({ content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
  const [customers, setCustomers] = useState([]);
  const [menu, setMenu] = useState([]);
  const [searchInput, setSearchInput] = useState('');
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(null);
  const [editing, setEditing] = useState(null);
  const [formOpen, setFormOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const loadOrders = useCallback(async (page = 0, term = search) => {
    setLoading(true); setError('');
    try { setResult(await requestJson(`/api/orders?page=${page}&size=${PAGE_SIZE}&search=${encodeURIComponent(term)}`)); }
    catch (requestError) { setError(requestError.message); }
    finally { setLoading(false); }
  }, [search]);

  useEffect(() => { loadOrders(0); }, [loadOrders]);
  useEffect(() => { Promise.all([requestJson('/api/customers'), requestJson('/api/catalog/menu-items')]).then(([customerData, menuData]) => { setCustomers(customerData); setMenu(menuData); }).catch((requestError) => setError(requestError.message)); }, []);

  async function openOrder(id) { setError(''); try { setSelected(await requestJson(`/api/orders/${id}`)); } catch (requestError) { setError(requestError.message); } }
  async function saveOrder(payload) {
    setBusy(true);
    try {
      const saved = editing ? await requestJson(`/api/orders/${editing.id}/items`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ items: payload.items }) }) : await requestJson('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      setFormOpen(false); setEditing(null); setNotice(editing ? 'Cambios guardados.' : `Orden #${saved.id} creada.`); setSelected(null); await loadOrders(result.page);
    } finally { setBusy(false); }
  }
  async function cancelOrder() {
    if (!selected || !window.confirm(`¿Cancelar la orden #${selected.id}? Esta acción no se puede deshacer.`)) return;
    setBusy(true); setError('');
    try { await requestJson(`/api/orders/${selected.id}`, { method: 'DELETE' }); setSelected(null); setNotice(`Orden #${selected.id} cancelada.`); await loadOrders(result.page); }
    catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }
  async function payOrder() {
    if (!selected || !window.confirm(`¿Confirmar el pago simulado de ${money.format(selected.total)} para la orden #${selected.id}?`)) return;
    setBusy(true); setError('');
    try { await requestJson(`/api/orders/${selected.id}/payments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ paymentMethod: 'credit', idempotencyKey: `web-order-${selected.id}` }) }); setNotice(`Pago simulado confirmado para la orden #${selected.id}.`); setSelected(null); await loadOrders(result.page); }
    catch (requestError) { setError(requestError.message); } finally { setBusy(false); }
  }
  function submitSearch(event) { event.preventDefault(); setSearch(searchInput.trim()); setNotice(''); }

  return <section className={styles.page} aria-labelledby="orders-title"><header className={styles.header}><div><h1 id="orders-title">Órdenes</h1><p>Consulta, edita y confirma las órdenes del restaurante.</p></div><button className={styles.primaryButton} onClick={() => { setEditing(null); setFormOpen(true); setSelected(null); setError(''); }}><CirclePlus size={18}/> Nueva orden</button></header>
    {notice && <div className={styles.notice} role="status"><Check size={17}/>{notice}<button aria-label="Cerrar aviso" onClick={() => setNotice('')}><X size={16}/></button></div>}
    {error && <div className={styles.errorBanner} role="alert"><AlertCircle size={18}/><span>{error}</span><button aria-label="Cerrar error" onClick={() => setError('')}><X size={16}/></button></div>}
    <div className={styles.toolbar}><form className={styles.search} onSubmit={submitSearch}><Search size={18}/><label className={styles.srOnly} htmlFor="order-search">Buscar órdenes</label><input id="order-search" value={searchInput} onChange={(event) => setSearchInput(event.target.value)} placeholder="Buscar por cliente, producto o estado"/><button type="submit">Buscar</button></form><span className={styles.count}>{result.totalElements} {result.totalElements === 1 ? 'orden' : 'órdenes'}</span></div>
    <div className={styles.tableWrap}><table><thead><tr><th scope="col">Orden</th><th scope="col">Cliente / mesa</th><th scope="col">Fecha</th><th scope="col">Estado</th><th scope="col">Total</th><th scope="col"><span className={styles.srOnly}>Acciones</span></th></tr></thead><tbody>{loading ? <tr><td colSpan="6" className={styles.tableMessage}><LoaderCircle className={styles.spin} size={20}/> Cargando órdenes…</td></tr> : result.content.length ? result.content.map((order) => <tr key={order.id}><td><button className={styles.orderLink} onClick={() => openOrder(order.id)}>#{order.id}</button></td><td>{order.customerName ?? `Mesa ${order.tableId}`}<small>{order.orderType === 'online' ? 'En línea' : 'En mesa'}</small></td><td>{date.format(new Date(`${order.orderDate}T12:00:00`))}</td><td><span className={`${styles.status} ${styles[order.status]}`}>{statusLabels[order.status] ?? order.status}</span></td><td>{money.format(order.total)}</td><td><button className={styles.iconButton} aria-label={`Ver orden ${order.id}`} onClick={() => openOrder(order.id)}><ReceiptText size={18}/></button></td></tr>) : <tr><td className={styles.tableMessage} colSpan="6">No encontramos órdenes{search ? ' con esos filtros.' : ' todavía.'}</td></tr>}</tbody></table></div>
    <footer className={styles.pagination}><span>Página {result.totalPages ? result.page + 1 : 0} de {result.totalPages}</span><div><button className={styles.iconButton} aria-label="Página anterior" disabled={loading || result.page <= 0} onClick={() => loadOrders(result.page - 1)}><ChevronLeft size={19}/></button><button className={styles.iconButton} aria-label="Página siguiente" disabled={loading || result.page + 1 >= result.totalPages} onClick={() => loadOrders(result.page + 1)}><ChevronRight size={19}/></button></div></footer>
    {formOpen && <div className={styles.overlay} role="presentation"><section className={`${styles.drawer} ${styles.formDrawer}`} role="dialog" aria-modal="true" aria-labelledby="form-heading"><OrderForm order={editing} customers={customers} menu={menu} busy={busy} onCancel={() => { setFormOpen(false); setEditing(null); }} onSave={saveOrder}/></section></div>}
    {selected && <DetailPanel order={selected} busy={busy} onClose={() => setSelected(null)} onEdit={() => { setEditing(selected); setSelected(null); setFormOpen(true); }} onCancelOrder={cancelOrder} onPay={payOrder}/>}
  </section>;
}
