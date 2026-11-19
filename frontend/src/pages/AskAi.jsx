import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Card, Form, ListGroup } from 'react-bootstrap';
import { api } from '../api';

export default function AskAi() {
  const [query, setQuery] = useState('');
  const [response, setResponse] = useState(null);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setError('');
    setResponse(null);
    try {
      const data = await api.ragQuery(query.trim());
      setResponse(data);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <Card className="shadow-sm">
      <Card.Header>
        <h1 className="h4 mb-0">Ask AI</h1>
      </Card.Header>
      <Card.Body>
        <Form onSubmit={submit} className="mb-3">
          <Form.Label>Question</Form.Label>
          <Form.Control
            as="textarea"
            rows={3}
            required
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Ask about past tickets…"
          />
          <Button type="submit" variant="primary" className="mt-2">
            Submit
          </Button>
        </Form>
        {error && <Alert variant="danger">{error}</Alert>}
        {response?.outcome === 'ANSWER' && (
          <div>
            <p>{response.answer?.text}</p>
            <h3 className="h6">Citations</h3>
            <ListGroup variant="flush">
              {response.citations?.map((c) => (
                <ListGroup.Item key={c.ticketId}>
                  <Link to={`/tickets/${c.ticketId}`}>{c.ticketId}</Link> — {c.title}
                </ListGroup.Item>
              ))}
            </ListGroup>
          </div>
        )}
        {response?.outcome === 'NO_MATCH' && (
          <Alert variant="warning">
            {response.noMatch?.message || 'No similar tickets were found.'}
            {response.escalation?.recommended && (
              <p className="mb-0 mt-2">
                <em>Consider creating a new ticket or escalating.</em>
              </p>
            )}
          </Alert>
        )}
        {response?.outcome === 'ERROR' && (
          <Alert variant="danger">{response.error?.message}</Alert>
        )}
      </Card.Body>
    </Card>
  );
}