import {Navigate} from 'react-router-dom'
import useOwnerStore from '../store/ownerStore'

interface PrivateRouteProps {
  children: React.ReactNode
}

function PrivateRoute({children}: PrivateRouteProps) {
  const accessToken = useOwnerStore((s) => s.accessToken)
  if (!accessToken) return <Navigate to="/owner/login" replace/>
  return <>{children}</>
}

export default PrivateRoute
