import { Navigate, Route, Routes } from 'react-router-dom';
import AppShell from './AppShell.jsx';
import OrdersPage from '../features/orders/OrdersPage.jsx';
import AuditPage from '../features/audit/AuditPage.jsx';
import styles from './App.module.css';

function EmptyPage({ title, description }) {
  return <section className={styles.page} aria-labelledby="page-title"><h1 id="page-title">{title}</h1><p className={styles.emptyState}>{description}</p></section>;
}

export default function App() {
  return <AppShell><Routes>
    <Route path="/" element={<Navigate to="/orders" replace />} />
    <Route path="/orders" element={<OrdersPage />} />
    <Route path="/audit" element={<AuditPage />} />
    <Route path="/laboratory" element={<EmptyPage title="Laboratorio" description="Las demostraciones se habilitarán en la etapa de validación." />} />
    <Route path="*" element={<EmptyPage title="Página no encontrada" description="La dirección no corresponde a una sección disponible." />} />
  </Routes></AppShell>;
}
