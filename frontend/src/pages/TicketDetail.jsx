import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Alert,
  Badge,
  Button,
  Card,
  Col,
  Form,
  ListGroup,
  Row,
  Spinner,
} from 'react-bootstrap';
import { api } from '../api';

const TRANSITIONS = {
  OPEN: [
    { label: 'Start progress', status: 'IN_PROGRESS' },
    { label: 'Cancel', status: 'CANCELLED' },
  ],
  IN_PROGRESS: [
    { label: 'Resolve', status: 'RESOLVED' },
    { label: 'Cancel', status: 'CANCELLED' },
  ],
  RESOLVED: [{ label: 'Close', status: 'CLOSED' }],
  CLOSED: [],
  CANCELLED: [],
};

export default function TicketDetail() {
  const { ticketId } = useParams();
  const [ticket, setTicket] = useState(null);
  const [comments, setComments] = useState([]);
  const [error, setError] = useState('');
  const [commentForm, setCommentForm] = useState({ author: '', content: '' });

  const load = () => {
    Promise.all([api.getTicket(ticketId), api.listComments(ticketId)])
      .then(([t, c]) => {
        setTicket(t);
        setComments(c);
      })
      .catch((e) => setError(e.message));
  };

  useEffect(load, [ticketId]);

  const changeStatus = async (status) => {
    setError('');
    try {
      await api.changeStatus(ticketId, status);
      load();
    } catch (e) {
      setError(e.message);
      load();
    }
  };

  const addComment = async (e) => {
    e.preventDefault();
    try {
      await api.addComment(ticketId, commentForm);
      setCommentForm({ author: '', content: '' });
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  if (!ticket) {
    return (
      <Card className="shadow-sm">
        <Card.Body>
          <Spinner animation="border" size="sm" className="me-2" />
          Loading…
        </Card.Body>
      </Card>
    );
  }

  const actions = TRANSITIONS[ticket.status] || [];

  return (
  <Row className="g-3">
    <Col xs={12}>
      <Card className="shadow-sm">
        <Card.Header>
          <h1 className="h4 mb-0">
            {ticket.ticketId}: {ticket.title}
          </h1>
        </Card.Header>
        <Card.Body>
          {error && <Alert variant="danger">{error}</Alert>}
          <p className="mb-2">
            <Badge bg="secondary" className="me-2">
              {ticket.status}
            </Badge>
            <Badge bg="info">{ticket.priority}</Badge>
          </p>
          <p className="text-muted">{ticket.description}</p>
          <div className="d-flex flex-wrap gap-2 my-3">
            {actions.map((a) => (
              <Button key={a.status} variant="outline-primary" size="sm" onClick={() => changeStatus(a.status)}>
                {a.label}
              </Button>
            ))}
          </div>
        </Card.Body>
      </Card>
    </Col>
    <Col xs={12}>
      <Card className="shadow-sm">
        <Card.Header>
          <h2 className="h5 mb-0">Comments</h2>
        </Card.Header>
        <ListGroup variant="flush">
          {comments.length === 0 ? (
            <ListGroup.Item className="text-muted">No comments yet.</ListGroup.Item>
          ) : (
            comments.map((c) => (
              <ListGroup.Item key={c.id}>
                <small className="text-muted">{c.createdAt}</small>
                <div>
                  <strong>{c.author}:</strong> {c.content}
                </div>
              </ListGroup.Item>
            ))
          )}
        </ListGroup>
        <Card.Body>
          <Form onSubmit={addComment}>
            <Row className="g-2">
              <Col xs={12} md={4}>
                <Form.Control
                  placeholder="Author"
                  value={commentForm.author}
                  onChange={(e) => setCommentForm({ ...commentForm, author: e.target.value })}
                />
              </Col>
              <Col xs={12} md={8}>
                <Form.Control
                  as="textarea"
                  rows={2}
                  required
                  placeholder="Comment"
                  value={commentForm.content}
                  onChange={(e) => setCommentForm({ ...commentForm, content: e.target.value })}
                />
              </Col>
              <Col xs={12}>
                <Button type="submit" variant="primary" size="sm">
                  Add comment
                </Button>
              </Col>
            </Row>
          </Form>
        </Card.Body>
      </Card>
    </Col>
  </Row>
  );
}