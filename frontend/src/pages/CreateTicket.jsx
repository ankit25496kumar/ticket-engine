import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Col, Form, Row } from 'react-bootstrap';
import { api } from '../api';

export default function CreateTicket() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    title: '',
    description: '',
    priority: 'MEDIUM',
    assignee: '',
    category: '',
  });
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const ticket = await api.createTicket(form);
      navigate(`/tickets/${ticket.ticketId}`);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <Card className="shadow-sm">
      <Card.Header>
        <h1 className="h4 mb-0">Create ticket</h1>
      </Card.Header>
      <Card.Body>
        {error && <Alert variant="danger">{error}</Alert>}
        <Form onSubmit={submit}>
          <Row className="g-3">
            <Col xs={12}>
              <Form.Label>Title</Form.Label>
              <Form.Control
                required
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
              />
            </Col>
            <Col xs={12}>
              <Form.Label>Description</Form.Label>
              <Form.Control
                as="textarea"
                rows={4}
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </Col>
            <Col xs={12} md={6}>
              <Form.Label>Priority</Form.Label>
              <Form.Select
                value={form.priority}
                onChange={(e) => setForm({ ...form, priority: e.target.value })}
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
              </Form.Select>
            </Col>
            <Col xs={12} md={6}>
              <Form.Label>Assignee</Form.Label>
              <Form.Control
                value={form.assignee}
                onChange={(e) => setForm({ ...form, assignee: e.target.value })}
              />
            </Col>
            <Col xs={12} md={6}>
              <Form.Label>Category</Form.Label>
              <Form.Control
                value={form.category}
                onChange={(e) => setForm({ ...form, category: e.target.value })}
              />
            </Col>
            <Col xs={12}>
              <Button type="submit" variant="primary">
                Create
              </Button>
            </Col>
          </Row>
        </Form>
      </Card.Body>
    </Card>
  );
}