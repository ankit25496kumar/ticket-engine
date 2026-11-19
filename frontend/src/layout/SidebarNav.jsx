import { Nav } from 'react-bootstrap';
import { NavLink } from 'react-router-dom';

const links = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/tickets', label: 'Tickets' },
  { to: '/tickets/new', label: 'Create ticket' },
  { to: '/ask', label: 'Ask AI' },
];

export default function SidebarNav({ onNavigate }) {
  return (
    <Nav as="nav" className="flex-column sidebar-nav gap-1">
      {links.map((l) => (
        <Nav.Link
          key={l.to}
          as={NavLink}
          to={l.to}
          end={l.end}
          onClick={onNavigate}
          className={({ isActive }) =>
            `sidebar-link w-100 text-start${isActive ? ' active' : ''}`
          }
        >
          {l.label}
        </Nav.Link>
      ))}
    </Nav>
  );
}