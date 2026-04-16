import {useEffect, useState} from 'react'
import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom'
import LandingPage from './pages/LandingPage'
import WaitingConfirmPage from './pages/WaitingConfirmPage'
import WaitingStatusPage from './pages/WaitingStatusPage'
import CancelPage from './pages/CancelPage'
import NotFoundPage from './pages/NotFoundPage'
import OwnerLoginPage from './pages/OwnerLoginPage'
import OwnerSignupPage from './pages/OwnerSignupPage'
import OnboardingPage from './pages/OnboardingPage'
import DashboardPage from './pages/DashboardPage'
import StoreSettingsPage from './pages/StoreSettingsPage'
import QrPrintPage from './pages/QrPrintPage'
import HistoryPage from './pages/HistoryPage'
import PrivateRoute from './components/PrivateRoute'
import useOwnerStore from './store/ownerStore'
import {refreshToken} from './api/owner'

function RootRedirect() {
  const accessToken = useOwnerStore((s) => s.accessToken)
  return <Navigate to={accessToken ? '/owner/dashboard' : '/owner/login'} replace/>
}

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
        {/* 손님 라우트 */}
        <Route path="/wait" element={<LandingPage />} />
        <Route path="/waiting/:waitingId" element={<WaitingConfirmPage />} />
        <Route path="/waiting/:waitingId/status" element={<WaitingStatusPage />} />
        <Route path="/waiting/:waitingId/cancel" element={<CancelPage />} />

        {/* 점주 라우트 */}
        <Route path="/" element={<RootRedirect/>}/>
        <Route path="/owner/login" element={<OwnerLoginPage/>}/>
        <Route path="/owner/signup" element={<OwnerSignupPage/>}/>
        <Route path="/owner/onboarding" element={<PrivateRoute><OnboardingPage/></PrivateRoute>}/>
        <Route path="/owner/dashboard" element={<PrivateRoute><DashboardPage/></PrivateRoute>}/>
        <Route path="/owner/settings" element={<PrivateRoute><StoreSettingsPage/></PrivateRoute>}/>
        <Route path="/owner/qr-print" element={<PrivateRoute><QrPrintPage/></PrivateRoute>}/>
        <Route path="/owner/history" element={<PrivateRoute><HistoryPage/></PrivateRoute>}/>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
