import { NavLink } from 'react-router-dom';
import { ClipboardList, FlaskConical, ScrollText } from 'lucide-react';
import styles from './AppShell.module.css';

const links = [
  { to: '/orders', label: 'Órdenes', Icon: ClipboardList },
  { to: '/audit', label: 'Auditoría', Icon: ScrollText },
  { to: '/laboratory', label: 'Laboratorio', Icon: FlaskConical },
];

export default function AppShell({ children }) {
  return (
    <div className={styles.layout}>
      <a className={styles.skipLink} href="#main-content">Saltar al contenido</a>
      <aside className={styles.sidebar} aria-label="Navegación principal">
        <div className={styles.brand}>Restaurant DBMS</div>
        <nav className={styles.nav}>
          {links.map(({ to, label, Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}>
              <Icon aria-hidden="true" size={20} strokeWidth={1.8} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <p className={styles.labNote}>Entorno académico</p>
      </aside>
      <main id="main-content" className={styles.main} tabIndex="-1">{children}</main>
    </div>
  );
}

