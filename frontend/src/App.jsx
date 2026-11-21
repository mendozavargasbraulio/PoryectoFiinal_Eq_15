import { Navigate, Route, Routes } from 'react-router-dom';

import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import NotFound from './pages/NotFound.jsx';

import ProtectedRoute from './routes/ProtectedRoute.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />

      <Route path="/login" element={<Login />} />
      <Route path="/registro" element={<Register />} />
      <Route path="/recuperar" element={<ForgotPassword />} />
      <Route path="/restablecer" element={<ResetPassword />} />

      <Route
        path="/admin/*"
        element={
          <ProtectedRoute allow={['ADMIN']}>
            <div>Panel de administrador en construcción</div>
          </ProtectedRoute>
        }
      />

      <Route
        path="/profesor/*"
        element={
          <ProtectedRoute allow={['TEACHER']}>
            <div>Panel de profesor en construcción</div>
          </ProtectedRoute>
        }
      />

      <Route
        path="/estudiante/*"
        element={
          <ProtectedRoute allow={['STUDENT']}>
            <div>Panel de estudiante en construcción</div>
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}