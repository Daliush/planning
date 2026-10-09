import React from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { api } from "../../api/client";
import { workforceApi } from "../../api/workforce";
import type { SolveJob } from "../../api/types";
import type {
  Absence,
  Employee,
  Shift,
  WorkforceOverview,
  WorkforcePolicy,
  WorkforceProfile,
  Workplace,
} from "./types";
import {
  ABSENCE_LABELS,
  dateLabel,
  duration,
  employeeMinutes,
  humanError,
} from "./format";
import {
  Avatar,
  EmptyPanel,
  ErrorNotice,
  FormField,
  Icon,
  Status,
  WButton,
} from "./components";
import {
  AbsenceEditor,
  DeleteConfirmation,
  EmployeeEditor,
  ShiftEditor,
  WorkplaceEditor,
} from "./Editors";
import { PlanningView, ResultNotes } from "./PlanningViews";
import "./workforce.css";

const tabs = [
  { slug: "", label: "Planning", icon: "calendar" },
  { slug: "equipe", label: "Équipe", icon: "people" },
  { slug: "absences", label: "Absences", icon: "absence" },
  { slug: "organisation", label: "Organisation", icon: "settings" },
] as const;
const activeJob = (job?: SolveJob | null) =>
  job?.status === "QUEUED" || job?.status === "RUNNING";
type Editor =
  | { kind: "employee"; employee?: Employee }
  | { kind: "shift"; shift?: Shift }
  | { kind: "absence" }
  | { kind: "workplace" }
  | { kind: "deleteEmployee"; employee: Employee }
  | { kind: "deleteShift"; shift: Shift }
  | { kind: "deleteAbsence"; absence: Absence };

function Logo() {
  return (
    <span className="wf-logo">
      <svg
        width="29"
        height="29"
        viewBox="0 0 29 29"
        fill="none"
        aria-hidden="true"
      >
        <rect
          x="2"
          y="2"
          width="25"
          height="25"
          rx="7"
          stroke="currentColor"
          strokeWidth="1.6"
        />
        <path
          d="M8 8h13M8 14h8M8 20h11"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
      </svg>
      <strong>Planning</strong>
    </span>
  );
}

export function WorkplaceChooser() {
  return (
    <div className="workforce workforce--chooser">
      <header className="wf-choice-header">
        <Logo />
        <Link to="/expert/referentiel" className="wf-school-link">
          <Icon name="school" />
          Espace scolaire
        </Link>
      </header>
      <main className="wf-choice-main">
        <div className="wf-choice-intro">
          <h1>
            Quel planning
            <br />
            préparez-vous ?
          </h1>
          <p>
            Votre équipe, ses disponibilités, vos besoins.
            <br />
            Un espace adapté à votre façon de travailler.
          </p>
        </div>
        <div className="wf-context-options">
          <Link
            to="/restaurant"
            className="wf-context-option wf-context-option--restaurant"
          >
            <div className="wf-context-symbol">
              <Icon name="restaurant" />
            </div>
            <div>
              <h2>Restaurant</h2>
              <p>
                Préparez vos services.
                <br />
                Couvrez chaque poste en cuisine et en salle.
              </p>
              <span>
                Ouvrir mon espace <Icon name="chevron" />
              </span>
            </div>
            <svg
              className="wf-context-scene"
              viewBox="0 0 190 140"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M15 30h160M15 65h160M15 100h160"
                stroke="currentColor"
                opacity=".2"
              />
              <rect
                x="24"
                y="20"
                width="88"
                height="20"
                rx="5"
                fill="currentColor"
                opacity=".25"
              />
              <rect
                x="84"
                y="55"
                width="91"
                height="20"
                rx="5"
                fill="currentColor"
                opacity=".5"
              />
              <rect
                x="24"
                y="90"
                width="118"
                height="20"
                rx="5"
                fill="currentColor"
                opacity=".8"
              />
              <circle
                cx="156"
                cy="100"
                r="10"
                fill="currentColor"
                opacity=".15"
              />
              <path
                d="m151 100 3 3 5-6"
                stroke="currentColor"
                strokeWidth="2"
              />
            </svg>
          </Link>
          <Link
            to="/entreprise"
            className="wf-context-option wf-context-option--company"
          >
            <div className="wf-context-symbol">
              <Icon name="company" />
            </div>
            <div>
              <h2>Entreprise</h2>
              <p>
                Organisez les horaires de vos équipes.
                <br />
                Gardez une vue claire sur la charge et les absences.
              </p>
              <span>
                Ouvrir mon espace <Icon name="chevron" />
              </span>
            </div>
            <svg
              className="wf-context-scene"
              viewBox="0 0 190 140"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M44 15v110M90 15v110M136 15v110M15 35h160M15 75h160M15 115h160"
                stroke="currentColor"
                opacity=".18"
              />
              <rect
                x="50"
                y="20"
                width="77"
                height="24"
                rx="5"
                fill="currentColor"
                opacity=".6"
              />
              <rect
                x="19"
                y="60"
                width="62"
                height="24"
                rx="5"
                fill="currentColor"
                opacity=".3"
              />
              <rect
                x="96"
                y="100"
                width="78"
                height="24"
                rx="5"
                fill="currentColor"
                opacity=".8"
              />
            </svg>
          </Link>
        </div>
        <footer className="wf-choice-footer">
          <Icon name="check" />
          <p>Commencez avec vos données ou explorez une démonstration.</p>
        </footer>
      </main>
    </div>
  );
}

export function WorkforceApp({ profile }: { profile: WorkforceProfile }) {
  const restaurant = profile === "RESTAURANT";
  const basePath = restaurant ? "/restaurant" : "/entreprise";
  const location = useLocation();
  const slug =
    location.pathname.slice(basePath.length).split("/").filter(Boolean)[0] ??
    "";
  const tab = tabs.find((value) => value.slug === slug) ?? tabs[0];
  const [workplaces, setWorkplaces] = React.useState<Workplace[]>([]);
  const [scenarioId, setScenarioId] = React.useState<string>();
  const currentScenarioId = React.useRef(scenarioId);
  currentScenarioId.current = scenarioId;
  const [overview, setOverview] = React.useState<WorkforceOverview>();
  const [loading, setLoading] = React.useState(true);
  const [loadingList, setLoadingList] = React.useState(true);
  const [error, setError] = React.useState<string>();
  const [editor, setEditor] = React.useState<Editor>();
  const [selectedDate, setSelectedDate] = React.useState("");
  const [pendingJob, setPendingJob] = React.useState<SolveJob>();
  const [submitting, setSubmitting] = React.useState(false);
  const [notice, setNotice] = React.useState("");
  const storageKey = `planning-workplace-${profile}`;
  const refreshList = React.useCallback(
    async (preferred?: string) => {
      setLoadingList(true);
      try {
        const loaded = await workforceApi.workspaces(profile);
        setWorkplaces(loaded);
        let stored: string | null = null;
        try {
          stored = window.localStorage.getItem(storageKey);
        } catch {
          /* Stockage facultatif. */
        }
        setScenarioId(
          (current) =>
            [preferred, current, stored].find((id) =>
              loaded.some((workplace) => workplace.scenarioId === id),
            ) ?? loaded[0]?.scenarioId,
        );
        setError(undefined);
      } catch (cause) {
        setError(humanError(cause));
      } finally {
        setLoadingList(false);
      }
    },
    [profile, storageKey],
  );
  React.useEffect(() => {
    void refreshList();
  }, [refreshList]);
  React.useEffect(() => {
    if (!scenarioId) {
      setOverview(undefined);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setOverview(undefined);
    setLoading(true);
    setPendingJob(undefined);
    setNotice("");
    setError(undefined);
    try {
      window.localStorage.setItem(storageKey, scenarioId);
    } catch {
      /* Stockage facultatif. */
    }
    workforceApi
      .overview(scenarioId)
      .then((loaded) => {
        if (!cancelled) {
          setOverview(loaded);
          setSelectedDate(loaded.period.from);
        }
      })
      .catch((cause) => {
        if (!cancelled) setError(humanError(cause));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [scenarioId, storageKey]);
  const reload = React.useCallback(async () => {
    if (!scenarioId) return;
    const loaded = await workforceApi.overview(scenarioId);
    if (currentScenarioId.current !== scenarioId) return;
    setOverview(loaded);
    setError(undefined);
  }, [scenarioId]);
  async function saved() {
    await reload();
    setNotice("Enregistré. Le planning peut maintenant être recalculé.");
  }
  const job = pendingJob ?? overview?.latestJob;
  const running = activeJob(job);
  React.useEffect(() => {
    if (!job || !activeJob(job)) return;
    let cancelled = false;
    let timer: number | undefined;
    async function poll() {
      try {
        const updated = await api.solveJob(job!.id);
        if (cancelled) return;
        if (activeJob(updated)) {
          setPendingJob(updated);
          timer = window.setTimeout(poll, 1300);
        } else {
          await reload();
          if (cancelled) return;
          if (updated.status === "FAILED")
            setError(
              updated.errorDetail ??
                "Le planning n’a pas pu être généré. Vérifiez les données puis réessayez.",
            );
          else if (updated.status === "DONE")
            setNotice(
              "Votre planning est prêt. Consultez les affectations et les points à vérifier.",
            );
          setPendingJob(updated);
        }
      } catch (cause) {
        if (!cancelled) {
          setError(humanError(cause));
          timer = window.setTimeout(poll, 3000);
        }
      }
    }
    timer = window.setTimeout(poll, 900);
    return () => {
      cancelled = true;
      if (timer) window.clearTimeout(timer);
    };
  }, [job?.id, job?.status, reload]);
  async function solve() {
    if (!scenarioId) return;
    setSubmitting(true);
    setError(undefined);
    setNotice("");
    try {
      setPendingJob(await api.submitSolve(scenarioId, 30));
    } catch (cause) {
      setError(humanError(cause));
    } finally {
      setSubmitting(false);
    }
  }
  async function remove(action: () => Promise<void>) {
    await action();
    await saved();
  }
  const solved = job?.status === "DONE" && !overview?.planOutdated;
  const needsData = !overview?.employees.length || !overview?.shifts.length;
  return (
    <div
      className={`workforce workforce--${restaurant ? "restaurant" : "company"}`}
    >
      <aside className="wf-sidebar">
        <Link to="/" className="wf-brand-link" aria-label="Choisir un espace">
          <Logo />
        </Link>
        <div className="wf-sector">
          <Icon name={restaurant ? "restaurant" : "company"} />
          <span>{restaurant ? "Restaurant" : "Entreprise"}</span>
        </div>
        <nav aria-label="Navigation principale">
          {tabs.map((value) => (
            <NavLink
              key={value.slug}
              end={!value.slug}
              to={value.slug ? `${basePath}/${value.slug}` : basePath}
              className={({ isActive }) =>
                `wf-nav-link ${isActive ? "is-active" : ""}`
              }
            >
              <Icon name={value.icon} />
              <span>{value.label}</span>
              {value.slug === "absences" && !!overview?.absences.length && (
                <small>{overview.absences.length}</small>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="wf-sidebar-bottom">
          <div className="wf-sidebar-note">
            <Icon name="clock" />
            <p>
              {restaurant
                ? "Les bons services commencent par une équipe bien organisée."
                : "Des horaires lisibles pour toute votre équipe."}
            </p>
          </div>
          <Link to="/" className="wf-switch-link">
            <Icon name="back" />
            Changer de métier
          </Link>
          <Link to="/expert/referentiel" className="wf-switch-link">
            <Icon name="school" />
            Espace scolaire
          </Link>
        </div>
      </aside>
      <div className="wf-main">
        <header className="wf-topbar">
          <Link
            to="/"
            className="wf-mobile-home"
            aria-label="Choisir un autre métier"
          >
            <Icon name="back" />
          </Link>
          <div className="wf-workplace-picker">
            <label htmlFor="wf-workplace">
              {restaurant ? "Votre restaurant" : "Votre entreprise"}
            </label>
            {workplaces.length ? (
              <select
                id="wf-workplace"
                value={scenarioId ?? ""}
                onChange={(event) => setScenarioId(event.target.value)}
              >
                {workplaces.map((workplace) => (
                  <option
                    key={workplace.scenarioId}
                    value={workplace.scenarioId}
                  >
                    {workplace.label} (
                    {dateLabel(workplace.from, {
                      day: "numeric",
                      month: "short",
                    })}{" "}
                    –{" "}
                    {dateLabel(workplace.to, {
                      day: "numeric",
                      month: "short",
                    })}
                    )
                  </option>
                ))}
              </select>
            ) : (
              <strong>Votre prochain planning</strong>
            )}
          </div>
          <WButton
            variant="quiet"
            icon="plus"
            onClick={() => setEditor({ kind: "workplace" })}
          >
            Nouvel espace
          </WButton>
        </header>
        <main className="wf-content">
          <div className="wf-page-heading">
            <div>
              <h1>
                {tab.label === "Planning"
                  ? restaurant
                    ? "Les services de votre équipe"
                    : "La semaine de votre équipe"
                  : tab.label === "Équipe"
                    ? "Les personnes qui font l’équipe"
                    : tab.label === "Absences"
                      ? "Anticiper les absences"
                      : "Vos règles d’organisation"}
              </h1>
              <p>
                {tab.slug === ""
                  ? overview
                    ? `Du ${dateLabel(overview.period.from)} au ${dateLabel(overview.period.to, { day: "numeric", month: "long", year: "numeric" })}`
                    : "Créez votre espace pour préparer les prochains jours."
                  : tab.slug === "equipe"
                    ? "Des compétences et des horaires de référence pour chaque personne."
                    : tab.slug === "absences"
                      ? "Congés, maladie et indisponibilités sont pris en compte dans le planning."
                      : "Des repères communs pour construire un planning équilibré."}
              </p>
            </div>
            {overview && (
              <div className="wf-heading-actions">
                {tab.slug === "" ? (
                  <>
                    <WButton
                      variant="quiet"
                      icon="print"
                      onClick={() => window.print()}
                      className="wf-print-button"
                    >
                      Imprimer
                    </WButton>
                    <WButton
                      variant="secondary"
                      icon="plus"
                      onClick={() => setEditor({ kind: "shift" })}
                    >
                      {restaurant ? "Ajouter un service" : "Ajouter une plage"}
                    </WButton>
                  </>
                ) : tab.slug === "equipe" ? (
                  <WButton
                    icon="plus"
                    onClick={() => setEditor({ kind: "employee" })}
                  >
                    Ajouter une personne
                  </WButton>
                ) : tab.slug === "absences" ? (
                  <WButton
                    icon="plus"
                    disabled={!overview.employees.length}
                    onClick={() => setEditor({ kind: "absence" })}
                  >
                    Ajouter une absence
                  </WButton>
                ) : null}
              </div>
            )}
          </div>
          {error && (
            <ErrorNotice
              message={error}
              retry={() => {
                if (scenarioId)
                  void reload().catch((cause) => setError(humanError(cause)));
                else void refreshList();
              }}
            />
          )}
          {notice && (
            <div className="wf-notice wf-notice--success" role="status">
              <Icon name="check" />
              <p>{notice}</p>
              <button
                className="wf-icon-button"
                aria-label="Masquer le message"
                onClick={() => setNotice("")}
              >
                <Icon name="close" />
              </button>
            </div>
          )}
          {loading || loadingList ? (
            <div className="wf-loading" role="status">
              <span />
              Chargement de votre espace…
            </div>
          ) : !overview ? (
            !error && (
              <EmptyPanel
                icon={restaurant ? "restaurant" : "company"}
                title={
                  restaurant
                    ? "Un planning à votre image"
                    : "Votre équipe, dans un même planning"
                }
                description="Créez votre espace, ajoutez les personnes et vos besoins. Vous pouvez aussi commencer par une démonstration pour découvrir le fonctionnement."
                action={
                  <WButton
                    icon="plus"
                    onClick={() => setEditor({ kind: "workplace" })}
                  >
                    Créer mon espace
                  </WButton>
                }
              />
            )
          ) : (
            <>
              {tab.slug === "" && (
                <>
                  <div
                    className={`wf-planning-summary ${running ? "is-working" : ""}`}
                  >
                    <div>
                      <Icon
                        name={running ? "spark" : solved ? "check" : "calendar"}
                      />
                      <div>
                        <strong>
                          {running
                            ? "Votre planning se prépare…"
                            : overview.planOutdated
                              ? "Le planning est à recalculer"
                              : solved
                                ? `${overview.kpis.assignedPositions} postes couverts sur ${overview.kpis.requiredPositions}`
                                : "Prêt à organiser votre équipe"}
                        </strong>
                        <p>
                          {running
                            ? "Nous cherchons les affectations qui respectent vos besoins et vos règles."
                            : needsData
                              ? !overview.employees.length
                                ? "Ajoutez votre équipe pour pouvoir attribuer les postes."
                                : "Ajoutez vos services ou plages de travail pour définir les postes."
                              : overview.planOutdated
                                ? "Vos dernières modifications seront prises en compte au prochain calcul."
                                : solved
                                  ? overview.kpis.unassignedPositions
                                    ? `${overview.kpis.unassignedPositions} poste${overview.kpis.unassignedPositions > 1 ? "s restent" : " reste"} à couvrir. Consultez les explications sous le planning.`
                                    : "Les affectations sont visibles ci-dessous."
                                  : `${overview.employees.length} personnes, ${overview.kpis.requiredPositions} postes à couvrir. Les absences seront prises en compte.`}
                        </p>
                      </div>
                    </div>
                    <WButton
                      icon="spark"
                      disabled={submitting || running || needsData}
                      onClick={solve}
                    >
                      {running
                        ? "Génération en cours"
                        : submitting
                          ? "Lancement…"
                          : solved || overview.planOutdated
                            ? "Recalculer le planning"
                            : "Générer le planning"}
                    </WButton>
                  </div>
                  <PlanningView
                    overview={overview}
                    selectedDate={selectedDate || overview.period.from}
                    onDate={setSelectedDate}
                    onAddShift={() => setEditor({ kind: "shift" })}
                    onEditShift={(shift) => setEditor({ kind: "shift", shift })}
                    onDeleteShift={(shift) =>
                      setEditor({ kind: "deleteShift", shift })
                    }
                  />
                  <ResultNotes overview={overview} />
                </>
              )}
              {tab.slug === "equipe" && (
                <Team
                  overview={overview}
                  onAdd={() => setEditor({ kind: "employee" })}
                  onEdit={(employee) =>
                    setEditor({ kind: "employee", employee })
                  }
                  onDelete={(employee) =>
                    setEditor({ kind: "deleteEmployee", employee })
                  }
                />
              )}
              {tab.slug === "absences" && (
                <Absences
                  overview={overview}
                  onAdd={() => setEditor({ kind: "absence" })}
                  onDelete={(absence) =>
                    setEditor({ kind: "deleteAbsence", absence })
                  }
                />
              )}
              {tab.slug === "organisation" && (
                <Organisation
                  key={overview.scenario.id}
                  overview={overview}
                  onSaved={saved}
                />
              )}
            </>
          )}
        </main>
      </div>
      {editor?.kind === "workplace" && (
        <WorkplaceEditor
          profile={profile}
          onClose={() => setEditor(undefined)}
          onCreated={(id) => refreshList(id)}
        />
      )}
      {overview && editor?.kind === "employee" && (
        <EmployeeEditor
          overview={overview}
          employee={editor.employee}
          onClose={() => setEditor(undefined)}
          onSaved={saved}
        />
      )}
      {overview && editor?.kind === "shift" && (
        <ShiftEditor
          overview={overview}
          shift={editor.shift}
          selectedDate={selectedDate}
          onClose={() => setEditor(undefined)}
          onSaved={saved}
        />
      )}
      {overview && editor?.kind === "absence" && (
        <AbsenceEditor
          overview={overview}
          onClose={() => setEditor(undefined)}
          onSaved={saved}
        />
      )}
      {overview && editor?.kind === "deleteEmployee" && (
        <DeleteConfirmation
          title={`Retirer ${editor.employee.label} ?`}
          detail="Cette personne et ses absences seront retirées de cet espace. Le planning devra être recalculé."
          onClose={() => setEditor(undefined)}
          onDelete={() =>
            remove(() =>
              workforceApi.deleteEmployee(
                overview.scenario.id,
                editor.employee.id,
              ),
            )
          }
        />
      )}
      {overview && editor?.kind === "deleteShift" && (
        <DeleteConfirmation
          title={`Supprimer ${editor.shift.label} ?`}
          detail="Les besoins de personnel de cette plage seront supprimés. Le planning devra être recalculé."
          onClose={() => setEditor(undefined)}
          onDelete={() =>
            remove(() =>
              workforceApi.deleteShift(overview.scenario.id, editor.shift.id),
            )
          }
        />
      )}
      {overview && editor?.kind === "deleteAbsence" && (
        <DeleteConfirmation
          title="Supprimer cette absence ?"
          detail="La personne sera de nouveau disponible pour ces dates. Le planning devra être recalculé."
          onClose={() => setEditor(undefined)}
          onDelete={() =>
            remove(() =>
              workforceApi.deleteAbsence(
                overview.scenario.id,
                editor.absence.id,
              ),
            )
          }
        />
      )}
    </div>
  );
}

function Team({
  overview,
  onAdd,
  onEdit,
  onDelete,
}: {
  overview: WorkforceOverview;
  onAdd: () => void;
  onEdit: (employee: Employee) => void;
  onDelete: (employee: Employee) => void;
}) {
  const [search, setSearch] = React.useState("");
  const employees = overview.employees.filter((employee) =>
    `${employee.label} ${employee.skills.join(" ")}`
      .toLocaleLowerCase("fr")
      .includes(search.toLocaleLowerCase("fr")),
  );
  if (!overview.employees.length)
    return (
      <EmptyPanel
        icon="people"
        title="Faisons connaissance avec votre équipe"
        description="Ajoutez chaque personne, ses compétences et ses heures au contrat. Ces informations guideront les affectations."
        action={
          <WButton icon="plus" onClick={onAdd}>
            Ajouter une personne
          </WButton>
        }
      />
    );
  return (
    <>
      <div className="wf-list-toolbar">
        <p>
          <strong>{overview.employees.length} personnes</strong>
          <span>
            {duration(
              overview.employees.reduce(
                (sum, employee) => sum + employee.contractWeeklyMinutes,
                0,
              ),
            )}{" "}
            de contrats par semaine
          </span>
        </p>
        <label className="wf-filter">
          <span className="wf-sr-only">
            Rechercher une personne ou compétence
          </span>
          <input
            type="search"
            placeholder="Rechercher une personne…"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
      </div>
      <div className="wf-people-list">
        {employees.map((employee, index) => (
          <article className="wf-person-row" key={employee.id}>
            <Avatar label={employee.label} index={index} />
            <div className="wf-person-name">
              <h2>{employee.label}</h2>
              <div>
                {employee.skills.map((skill) => (
                  <span className="wf-skill" key={skill}>
                    {skill}
                  </span>
                ))}
              </div>
            </div>
            <div className="wf-person-hours">
              <strong>{duration(employee.contractWeeklyMinutes)}</strong>
              <span>au contrat / semaine</span>
            </div>
            <div className="wf-person-hours">
              <strong>
                {duration(employeeMinutes(overview, employee.id))}
              </strong>
              <span>attribuées sur la période</span>
            </div>
            <div className="wf-row-actions">
              <WButton
                variant="quiet"
                icon="edit"
                onClick={() => onEdit(employee)}
              >
                Modifier
              </WButton>
              <button
                className="wf-icon-button"
                aria-label={`Retirer ${employee.label}`}
                onClick={() => onDelete(employee)}
              >
                <Icon name="trash" />
              </button>
            </div>
          </article>
        ))}
      </div>
      {!employees.length && (
        <p className="wf-table-empty">
          Aucune personne ne correspond à votre recherche.
        </p>
      )}
    </>
  );
}

function Absences({
  overview,
  onAdd,
  onDelete,
}: {
  overview: WorkforceOverview;
  onAdd: () => void;
  onDelete: (absence: Absence) => void;
}) {
  if (!overview.absences.length)
    return (
      <EmptyPanel
        icon="absence"
        title="Aucune absence sur cette période"
        description={
          overview.employees.length
            ? "Enregistrez un congé ou une indisponibilité pour que le planning respecte les journées d’absence."
            : "Ajoutez d’abord une personne dans Équipe, puis renseignez ses absences."
        }
        action={
          overview.employees.length ? (
            <WButton icon="plus" onClick={onAdd}>
              Ajouter une absence
            </WButton>
          ) : (
            <Link
              className="wf-button wf-button--primary"
              to={
                overview.tenant.profile === "RESTAURANT"
                  ? "/restaurant/equipe"
                  : "/entreprise/equipe"
              }
            >
              Ajouter mon équipe
            </Link>
          )
        }
      />
    );
  return (
    <div className="wf-absences-list">
      {overview.absences.map((absence) => {
        const employee = overview.employees.find(
          (value) => value.id === absence.resourceId,
        );
        return (
          <article className="wf-absence-row" key={absence.id}>
            <Avatar label={employee?.label ?? "?"} />
            <div>
              <h2>{employee?.label ?? "Personne retirée"}</h2>
              <p>
                {absence.from === absence.to
                  ? dateLabel(absence.from, {
                      weekday: "long",
                      day: "numeric",
                      month: "long",
                    })
                  : `${dateLabel(absence.from)} – ${dateLabel(absence.to)}`}
              </p>
              {absence.note && <small>{absence.note}</small>}
            </div>
            <Status>{ABSENCE_LABELS[absence.reason]}</Status>
            <button
              className="wf-icon-button"
              onClick={() => onDelete(absence)}
              aria-label={`Supprimer l’absence de ${employee?.label ?? "cette personne"}`}
            >
              <Icon name="trash" />
            </button>
          </article>
        );
      })}
    </div>
  );
}

function Organisation({
  overview,
  onSaved,
}: {
  overview: WorkforceOverview;
  onSaved: () => Promise<void>;
}) {
  const [policy, setPolicy] = React.useState<WorkforcePolicy>(overview.policy);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();
  const [success, setSuccess] = React.useState(false);
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(undefined);
    setSuccess(false);
    try {
      await workforceApi.savePolicy(overview.scenario.id, policy);
      await onSaved();
      setSuccess(true);
    } catch (cause) {
      setError(humanError(cause));
    } finally {
      setSaving(false);
    }
  }
  return (
    <div className="wf-organisation">
      <form className="wf-policy-form" onSubmit={save}>
        <h2>Temps de travail et repos</h2>
        <p>
          Ces limites s’appliquent à toute l’équipe. Les heures au contrat se
          règlent pour chaque personne.
        </p>
        {error && <ErrorNotice message={error} />}
        {success && (
          <p className="wf-policy-saved" role="status">
            <Icon name="check" />
            Règles enregistrées
          </p>
        )}
        <FormField
          label="Repos minimum entre deux plages"
          hint="Le temps entre la fin d’une plage et le début de la suivante."
        >
          <div className="wf-input-unit">
            <input
              type="number"
              min={0}
              max={48}
              step={0.5}
              value={policy.minRestMinutes / 60}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  minRestMinutes: Number(event.target.value) * 60,
                }))
              }
              required
            />
            <span>heures</span>
          </div>
        </FormField>
        <FormField label="Temps de travail maximum par jour">
          <div className="wf-input-unit">
            <input
              type="number"
              min={0.5}
              max={24}
              step={0.5}
              value={policy.maximumDailyMinutes / 60}
              onChange={(event) =>
                setPolicy((current) => ({
                  ...current,
                  maximumDailyMinutes: Number(event.target.value) * 60,
                }))
              }
              required
            />
            <span>heures</span>
          </div>
        </FormField>
        <label className="wf-policy-check">
          <input
            type="checkbox"
            checked={policy.balanceWorkload}
            onChange={(event) =>
              setPolicy((current) => ({
                ...current,
                balanceWorkload: event.target.checked,
              }))
            }
          />
          <span>
            <strong>Équilibrer la répartition du travail</strong>
            <small>
              Favoriser une charge totale proche entre les personnes
              disponibles.
            </small>
          </span>
        </label>
        <WButton type="submit" disabled={saving}>
          {saving ? "Enregistrement…" : "Enregistrer les règles"}
        </WButton>
      </form>
      <aside className="wf-policy-explanation">
        <h2>Le planning prend aussi en compte</h2>
        <ul>
          <li>
            <Icon name="check" />
            <span>
              <strong>Les compétences nécessaires</strong>
              <small>
                Chaque poste revient à une personne possédant la compétence
                demandée.
              </small>
            </span>
          </li>
          <li>
            <Icon name="check" />
            <span>
              <strong>Les absences et disponibilités</strong>
              <small>
                Une personne absente n’est pas affectée sur les journées
                concernées.
              </small>
            </span>
          </li>
          <li>
            <Icon name="check" />
            <span>
              <strong>Les heures au contrat</strong>
              <small>
                Le planning cherche à atteindre le volume hebdomadaire de chaque
                personne.
              </small>
            </span>
          </li>
          <li>
            <Icon name="check" />
            <span>
              <strong>Les jours travaillés</strong>
              <small>
                Six jours travaillés consécutifs au maximum, avec une
                répartition des week-ends.
              </small>
            </span>
          </li>
        </ul>
        <p>
          Après une modification, recalculez le planning pour appliquer vos
          nouveaux repères.
        </p>
      </aside>
    </div>
  );
}
