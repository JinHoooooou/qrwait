import {Navigate} from 'react-router-dom'
import useOwnerStore from '../store/ownerStore'

interface GuestRouteProps {
  children: React.ReactNode
}

/** 이미 로그인한 점주가 로그인/회원가입 화면에 들어오면 대시보드로 보낸다. */
function GuestRoute({children}: GuestRouteProps) {
  const accessToken = useOwnerStore((s) => s.accessToken)
  if (accessToken) return <Navigate to="/owner/dashboard" replace/>
  return <>{children}</>
}

export default GuestRoute
