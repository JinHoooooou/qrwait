import {useEffect, useState} from 'react'
import {BrowserRouter, Route, Routes} from 'react-router-dom'
import LandingPage from './pages/LandingPage'
import WaitingConfirmPage from './pages/WaitingConfirmPage'
import WaitingStatusPage from './pages/WaitingStatusPage'
import CancelPage from './pages/CancelPage'
import NotFoundPage from './pages/NotFoundPage'
import useOwnerStore from './store/ownerStore'
import {refreshToken} from './api/owner'

function App() {
  const [authInitialized, setAuthInitialized] = useState(false)
  const setAuth = useOwnerStore((s) => s.setAuth)

  useEffect(() => {
    refreshToken()
        .then(({accessToken, ownerId, storeId}) => {
          setAuth({accessToken, ownerId, storeId})
        })
        .catch(() => {
          // 세션 없음 — 비로그인 상태로 진행
        })
        .finally(() => {
          setAuthInitialized(true)
        })
  }, [setAuth])

  if (!authInitialized) return null

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
