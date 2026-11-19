import { Link } from 'react-router-dom';
import { Alert, Button, Card, Col, Placeholder, Row, Spinner, Table } from 'react-bootstrap';
import {
  Bar,
  BarChart,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useDashboard } from '../hooks/useDashboard';

const STATUS_COLORS = {
  OPEN: '#0d6efd',
  IN_PROGRESS: '#fd7e14',
  RESOLVED: '#198754',
  CLOSED: '#6c757d',
  CANCELLED: '#dc3545',
};

function formatAssignee(assignee) {
  return assignee == null || assignee === '' ? 'Unassigned' : assignee;
}

function KpiCard({ label, value, linkTo, linkLabel }) {
  return (
    <Card className="h-100 shadow-sm">
      <Card.Body>
        <Card.Subtitle className="text-muted mb-1">{label}</Card.Subtitle>
        <Card.Title className="display-6 mb-2">{value}</Card.Title>
        {linkTo && (
          <Link to={linkTo} className="small">
            {linkLabel}
          </Link>
        )}
      </Card.Body>
    </Card>
  );
}

export default function Dashboard() {
  const { data, loading, error, reload } = useDashboard();

  if (loading) {
    return (
      <>
        <h1 className="h3 mb-4">Dashboard</h1>
        <Row xs={1} sm={2} xl={4} className="g-3 mb-4">
          {[1, 2, 3, 4].map((i) => (
            <Col key={i}>
              <Card>
                <Card.Body>
                  <Placeholder as={Card.Title} animation="glow">
                    <Placeholder xs={6} />
                  </Placeholder>
                </Card.Body>
              </Card>
            </Col>
          ))}
        </Row>
        <div className="text-center text-muted">
          <Spinner animation="border" size="sm" className="me-2" />
          Loading metrics…
        </div>
      </>
    );
  }

  if (error) {
    return (
      <>
        <h1 className="h3 mb-3">Dashboard</h1>
        <Alert variant="danger">{error}</Alert>
        <Button variant="primary" onClick={reload}>
          Retry
        </Button>
      </>
    );
  }

  const byStatus = data.byStatus.map((s) => ({
    name: s.status,
    count: s.count,
    fill: STATUS_COLORS[s.status] || '#adb5bd',
  }));

  const byAssignee = data.byAssignee.map((a) => ({
    name: formatAssignee(a.assignee),
    count: a.count,
  }));

  const open = data.byStatus.find((s) => s.status === 'OPEN')?.count ?? 0;
  const inProg = data.byStatus.find((s) => s.status === 'IN_PROGRESS')?.count ?? 0;
  const resolved = data.byStatus.find((s) => s.status === 'RESOLVED')?.count ?? 0;

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-4">
        <h1 className="h3 mb-0">Dashboard</h1>
        <Button variant="outline-primary" size="sm" onClick={reload}>
          Refresh
        </Button>
      </div>

      <Row xs={1} sm={2} xl={4} className="g-3 mb-4">
        <Col>
          <KpiCard label="Total tickets" value={data.totalTickets} />
        </Col>
        <Col>
          <KpiCard
            label="Open"
            value={open}
            linkTo="/tickets?status=OPEN"
            linkLabel="View queue"
          />
        </Col>
        <Col>
          <KpiCard
            label="In progress"
            value={inProg}
            linkTo="/tickets?status=IN_PROGRESS"
            linkLabel="View active"
          />
        </Col>
        <Col>
          <KpiCard
            label="Resolved"
            value={resolved}
            linkTo="/tickets?status=RESOLVED"
            linkLabel="View resolved"
          />
        </Col>
      </Row>

      <Row className="g-3 mb-4">
        <Col xs={12} lg={6}>
          <Card className="shadow-sm h-100">
            <Card.Header className="fw-semibold">Tickets by status</Card.Header>
            <Card.Body style={{ height: 280 }}>
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={byStatus}
                    dataKey="count"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={85}
                  >
                    {byStatus.map((entry) => (
                      <Cell key={entry.name} fill={entry.fill} />
                    ))}
                  </Pie>
                  <Tooltip />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            </Card.Body>
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card className="shadow-sm h-100">
            <Card.Header className="fw-semibold">Tickets by assignee</Card.Header>
            <Card.Body style={{ height: 280 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={byAssignee} layout="vertical" margin={{ left: 4, right: 8 }}>
                  <XAxis type="number" allowDecimals={false} />
                  <YAxis type="category" dataKey="name" width={110} tick={{ fontSize: 11 }} />
                  <Tooltip />
                  <Bar dataKey="count" fill="#0d6efd" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </Card.Body>
          </Card>
        </Col>
      </Row>

      <Card className="shadow-sm">
        <Card.Header className="fw-semibold">Recent tickets</Card.Header>
        <Card.Body className="p-0">
          {data.recentTickets.length === 0 ? (
            <p className="p-3 mb-0">
              No tickets yet. <Link to="/tickets/new">Create the first ticket</Link>
            </p>
          ) : (
            <>
              <div className="d-md-none">
                {data.recentTickets.map((t) => (
                  <div key={t.ticketId} className="border-bottom p-3">
                    <Link to={`/tickets/${t.ticketId}`} className="fw-semibold">
                      {t.ticketId}
                    </Link>
                    <div className="small">{t.title}</div>
                    <div className="text-muted small">
                      {t.status} · {t.priority} · {new Date(t.createdAt).toLocaleString()}
                    </div>
                  </div>
                ))}
              </div>
              <div className="table-responsive d-none d-md-block">
                <Table hover className="mb-0 align-middle">
                  <thead className="table-light">
                    <tr>
                      <th>ID</th>
                      <th>Title</th>
                      <th>Status</th>
                      <th>Priority</th>
                      <th>Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentTickets.map((t) => (
                      <tr key={t.ticketId}>
                        <td>
                          <Link to={`/tickets/${t.ticketId}`}>{t.ticketId}</Link>
                        </td>
                        <td>{t.title}</td>
                        <td>
                          <span className="badge text-bg-secondary">{t.status}</span>
                        </td>
                        <td>{t.priority}</td>
                        <td className="small">{new Date(t.createdAt).toLocaleString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </div>
            </>
          )}
        </Card.Body>
      </Card>
    </>
  );
}