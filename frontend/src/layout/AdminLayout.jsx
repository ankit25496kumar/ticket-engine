import { useState } from 'react';
import { Container, Navbar, Offcanvas } from 'react-bootstrap';
import { Outlet, useLocation } from 'react-router-dom';
import SidebarNav from './SidebarNav';

const TITLES = {
  '/': 'Dashboard',
  '/tickets': 'Tickets',
  '/tickets/new': 'Create ticket',
  '/ask': 'Ask AI',
};

function resolveTitle(pathname) {
  if (pathname.startsWith('/tickets/') && pathname !== '/tickets/new') {
    return 'Ticket detail';
  }
  return TITLES[pathname] || 'Support Ticket AI';
}

export default function AdminLayout() {
  const [showOffcanvas, setShowOffcanvas] = useState(false);
  const { pathname } = useLocation();
  const title = resolveTitle(pathname);

  const closeDrawer = () => setShowOffcanvas(false);

  return (
    <div className="admin-app d-flex flex-column min-vh-100">
      <Navbar
        bg="dark"
        variant="dark"
        expand={false}
        className="border-bottom border-secondary shadow-sm py-2"
        sticky="top"
      >
        <Container fluid className="px-3 d-flex align-items-center">
          <Navbar.Toggle
            aria-controls="admin-sidebar"
            onClick={() => setShowOffcanvas(true)}
            className="d-md-none me-2"
          />
          <Navbar.Brand className="me-auto mb-0 fw-semibold">{title}</Navbar.Brand>
          <span className="navbar-text d-none d-md-inline text-secondary small mb-0">
            Support Ticket AI
          </span>
        </Container>
      </Navbar>

      <div className="container-fluid flex-grow-1 px-0">
        <div className="row g-0 admin-shell-row flex-nowrap">
          <aside className="col-auto d-none d-md-block admin-sidebar-col">
            <div className="sidebar-inner py-3 px-2">
              <p className="text-uppercase text-secondary small fw-semibold px-2 mb-2">Menu</p>
              <SidebarNav />
            </div>
          </aside>

          <main className="col admin-main-col py-3 px-3 px-lg-4 min-vh-100">
            <Outlet />
          </main>
        </div>
      </div>

      <Offcanvas
        show={showOffcanvas}
        onHide={closeDrawer}
        placement="start"
        className="admin-offcanvas text-bg-dark"
        id="admin-sidebar"
      >
        <Offcanvas.Header closeButton closeVariant="white">
          <Offcanvas.Title>Menu</Offcanvas.Title>
        </Offcanvas.Header>
        <Offcanvas.Body className="pt-0">
          <SidebarNav onNavigate={closeDrawer} />
        </Offcanvas.Body>
      </Offcanvas>
    </div>
  );
}