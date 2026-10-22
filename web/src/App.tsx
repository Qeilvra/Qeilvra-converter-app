import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { Home } from './screens/Home';
import { InfoPage } from './screens/InfoPage';
import { Recent } from './screens/Recent';
import { SettingsPage } from './screens/SettingsPage';
import { ToolRouter } from './screens/tools/ToolRouter';
import { ToolsPage } from './screens/ToolsPage';
import { NotFound } from './screens/NotFound';
import { LoginPage } from './screens/LoginPage';
import { AuthProvider } from './auth/AuthContext';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/tools" element={<ToolsPage />} />
          <Route path="/tools/:toolId" element={<ToolRouter />} />
          <Route path="/recent" element={<Recent />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/info/:page" element={<InfoPage />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </AuthProvider>
  );
}
