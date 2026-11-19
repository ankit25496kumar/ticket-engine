import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Alert, Card, Col, Form, Row, Table } from 'react-bootstrap';
import { api } from '../api';

export default function TicketList() {
  const [searchParams] = useSearchParams();
  const [tickets, setTickets] = useState([]);
  const [filters, setFilters] = useState({
    status: searchParams.get('status') || '',
    priority: searchParams.get('priority') || '',
  });
  const [error, setError] = useState('');

  useEffect(() => {
    const status = searchParams.get('status') || '';
    const priority = searchParams.get('priority') || '';
    setFilters({ status, priority });
  }, [searchParams]);

  useEffect(() => {
    const params = {};
    if (filters.status) params.status = filters.status;
    if (filters.priority) params.priority = filters.priority;
    api
      .listTickets(params)
      .then(setTickets)
      .catch((e) => setError(e.message));
  }, [filters]);

  return (
    <Card className="shadow-sm">
      <Card.Header className="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <h1 className="h4 mb-0">Tickets</h1>
        <Link to="/tickets/new" className="btn btn-sm btn-primary">
          New ticket
        </Link>
      </Card.Header>
      <Card.Body>
        {error && <Alert variant="danger">{error}</Alert>}
        <Row className="g-2 mb-3 sticky-top bg-white pt-1" style={{ top: '3.5rem', zIndex: 1 }}>
          <Col xs={12} sm={6} md={4}>
            <Form.Select
              value={filters.status}
              onChange={(e) => setFilters((f) => ({ ...f, status: e.target.value }))}
              aria-label="Filter by status"
            >
              <option value="">All statuses</option>
              {['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED'].map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Form.Select>
          </Col>
          <Col xs={12} sm={6} md={4}>
            <Form.Select
              value={filters.priority}
              onChange={(e) => setFilters((f) => ({ ...f, priority: e.target.value }))}
              aria-label="Filter by priority"
            >
              <option value="">All priorities</option>
              {['LOW', 'MEDIUM', 'HIGH'].map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </Form.Select>
          </Col>
        </Row>
        <div className="table-responsive">
          <Table striped bordered hover className="mb-0 align-middle">
            <thead className="table-dark">
              <tr>
                <th>ID</th>
                <th>Title</th>
                <th>Status</th>
                <th>Priority</th>
              </tr>
            </thead>
            <tbody>
              {tickets.length === 0 ? (
                <tr>
                  <td colSpan={4} className="text-center text-muted py-4">
                    No tickets match these filters.
                  </td>
                </tr>
              ) : (
                tickets.map((t) => (
                  <tr key={t.ticketId}>
                    <td>
                      <Link to={`/tickets/${t.ticketId}`}>{t.ticketId}</Link>
                    </td>
                    <td>{t.title}</td>
                    <td>
                      <span className="badge text-bg-secondary">{t.status}</span>
                    </td>
                    <td>{t.priority}</td>
                  </tr>
                ))
              )}
            </tbody>
          </Table>
        </div>
      </Card.Body>
    </Card>
  );
}