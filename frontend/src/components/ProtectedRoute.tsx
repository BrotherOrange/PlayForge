import { Navigate } from 'react-router-dom';
import { getAccessToken } from '../utils/token';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
  if (!getAccessToken()) {
    return <Navigate to="/login" replace />;
  }
  return children;
};

export default ProtectedRoute;
