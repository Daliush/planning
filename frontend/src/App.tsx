import { Link, NavLink, Navigate, Route, Routes } from "react-router-dom";
import { PageShell } from "./ui/PageShell";
import { ThemeToggle } from "./ui/theme/ThemeToggle";
import { ErrorBanner, Loading } from "./ui/Feedback";
import {
  WorkspaceProvider,
  useWorkspace,
} from "./features/scenario/WorkspaceContext";
import { ScenarioPicker } from "./features/scenario/ScenarioPicker";
import { ResourcesPage } from "./features/resources/ResourcesPage";
import { DemandsPage } from "./features/demands/DemandsPage";
import { ConstraintsPage } from "./features/constraints/ConstraintsPage";
import { SolvePage } from "./features/solve/SolvePage";
import {
  WorkforceApp,
  WorkplaceChooser,
} from "./features/workforce/WorkforceApp";

const TABS = [
  { to: "/expert/referentiel", label: "Référentiel" },
  { to: "/expert/demandes", label: "Demandes" },
  { to: "/expert/regles", label: "Règles" },
  { to: "/expert/resolution", label: "Résolution" },
];

function Workspace() {
  const { scenario, loading, error } = useWorkspace();

  return (
    <PageShell
      title="Planification"
      subtitle={
        scenario
          ? `${scenario.label} · ${scenario.timeModelLabel ?? "modèle temporel"} · ${scenario.status}`
          : "Moteur de planification paramétrable"
      }
      right={
        <div className="flex items-center gap-2">
          <Link to="/" className="app-muted text-sm">
            Choisir un métier
          </Link>
          <ScenarioPicker />
          <ThemeToggle />
        </div>
      }
    >
      <nav className="app-surface flex flex-wrap gap-1 rounded-xl border p-1.5">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) =>
              [
                "rounded-lg px-4 py-2 text-sm font-semibold transition",
                isActive ? "text-white" : "app-muted hover:opacity-70",
              ].join(" ")
            }
            style={({ isActive }) =>
              isActive ? { background: "var(--app-accent)" } : undefined
            }
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>

      {error ? <ErrorBanner message={error} /> : null}
      {loading ? (
        <Loading />
      ) : !scenario ? (
        <ErrorBanner message="Aucun scénario disponible. Créez-en un pour commencer." />
      ) : (
        <Routes>
          <Route
            path="referentiel"
            element={<ResourcesPage scenarioId={scenario.id} />}
          />
          <Route
            path="demandes"
            element={<DemandsPage scenarioId={scenario.id} />}
          />
          <Route
            path="regles"
            element={<ConstraintsPage scenarioId={scenario.id} />}
          />
          <Route
            path="resolution"
            element={<SolvePage scenarioId={scenario.id} />}
          />
          <Route
            path="*"
            element={<Navigate to="/expert/referentiel" replace />}
          />
        </Routes>
      )}
    </PageShell>
  );
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<WorkplaceChooser />} />
      <Route
        path="/restaurant/*"
        element={<WorkforceApp key="RESTAURANT" profile="RESTAURANT" />}
      />
      <Route
        path="/entreprise/*"
        element={<WorkforceApp key="COMPANY" profile="COMPANY" />}
      />
      <Route
        path="/expert/*"
        element={
          <WorkspaceProvider>
            <Workspace />
          </WorkspaceProvider>
        }
      />
      <Route
        path="/referentiel"
        element={<Navigate to="/expert/referentiel" replace />}
      />
      <Route
        path="/demandes"
        element={<Navigate to="/expert/demandes" replace />}
      />
      <Route
        path="/regles"
        element={<Navigate to="/expert/regles" replace />}
      />
      <Route
        path="/resolution"
        element={<Navigate to="/expert/resolution" replace />}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
