import { BrowserRouter, Routes, Route } from 'react-router-dom'
import LandingPage from './pages/LandingPage'
import WaitingConfirmPage from './pages/WaitingConfirmPage'
import WaitingStatusPage from './pages/WaitingStatusPage'
import CancelPage from './pages/CancelPage'
import NotFoundPage from './pages/NotFoundPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/wait" element={<LandingPage />} />
        <Route path="/waiting/:waitingId" element={<WaitingConfirmPage />} />
        <Route path="/waiting/:waitingId/status" element={<WaitingStatusPage />} />
        <Route path="/waiting/:waitingId/cancel" element={<CancelPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
