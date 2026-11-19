import { Route, Routes } from 'react-router-dom';
import AdminLayout from './layout/AdminLayout';
import AskAi from './pages/AskAi';
import CreateTicket from './pages/CreateTicket';
import Dashboard from './pages/Dashboard';
import TicketDetail from './pages/TicketDetail';
import TicketList from './pages/TicketList';

export default function App() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/tickets" element={<TicketList />} />
        <Route path="/tickets/new" element={<CreateTicket />} />
        <Route path="/tickets/:ticketId" element={<TicketDetail />} />
        <Route path="/ask" element={<AskAi />} />
      </Route>
    </Routes>
  );
}