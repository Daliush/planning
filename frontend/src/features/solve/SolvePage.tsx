import React from "react";
import { api } from "../../api/client";
import type { ScheduleView, SolveJob, SolveReportEnvelope } from "../../api/types";
import { Button } from "../../ui/Button";
import { Field } from "../../ui/Field";
import { EmptyState, ErrorBanner, Loading } from "../../ui/Feedback";
import { Card, Chip, Section } from "../../ui/Section";
import { describeError, useAsync } from "../../hooks/useAsync";
import { scoreLabel } from "../../utils/format";
import { ScheduleGrid } from "./ScheduleGrid";
import { ReportPanel } from "./ReportPanel";

const ACTIVE_STATUSES = ["QUEUED", "RUNNING"];

export function SolvePage({ scenarioId }: { scenarioId: string }) {
  const jobs = useAsync(() => api.solveJobs(scenarioId), [scenarioId]);
  const [timeLimit, setTimeLimit] = React.useState("30");
  const [selectedJobId, setSelectedJobId] = React.useState<string>();
  const [error, setError] = React.useState<string>();
  const [submitting, setSubmitting] = React.useState(false);

  const list = jobs.data ?? [];
  const running = list.some((job) => ACTIVE_STATUSES.includes(job.status));

  // La résolution vit hors du cycle HTTP : l'API rend un 202 et le worker
  // travaille en fond. On suit la progression par interrogation régulière.
  React.useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(() => jobs.reload(), 1500);
    return () => window.clearInterval(timer);
  }, [running, jobs.reload]);

  React.useEffect(() => {
    setSelectedJobId(undefined);
    setError(undefined);
  }, [scenarioId]);

  React.useEffect(() => {
    if (selectedJobId && list.some((job) => job.id === selectedJobId)) return;
    const done = list.find((job) => job.status === "DONE");
    if (done) setSelectedJobId(done.id);
  }, [list, selectedJobId]);

  async function launch() {
    setSubmitting(true);
    setError(undefined);
    try {
      const job = await api.submitSolve(scenarioId, Number(timeLimit));
      setSelectedJobId(job.id);
      jobs.reload();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setSubmitting(false);
    }
  }

  if (jobs.loading && list.length === 0) return <Loading />;
  if (jobs.error) return <ErrorBanner message={jobs.error} />;

  return (
    <div className="space-y-4">
      <Section
        title="Lancer une résolution"
        description="Quelques secondes à plusieurs minutes. Aucune garantie d'optimalité : c'est le meilleur planning trouvé dans le temps donné."
        right={
          <div className="flex items-end gap-2">
            <Field
              label="Budget (s)"
              type="number"
              value={timeLimit}
              onChange={setTimeLimit}
              className="w-28"
            />
            <Button onClick={launch} disabled={submitting || running}>
              {running ? "Résolution en cours…" : "Résoudre"}
            </Button>
          </div>
        }
      >
        {error ? <ErrorBanner message={error} /> : null}

        {list.length === 0 ? (
          <EmptyState title="Aucune résolution lancée" />
        ) : (
          <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
            {list.slice(0, 9).map((job) => (
              <JobCard
                key={job.id}
                job={job}
                selected={job.id === selectedJobId}
                onSelect={() => setSelectedJobId(job.id)}
              />
            ))}
          </div>
        )}
      </Section>

      {selectedJobId ? <JobDetail key={`${scenarioId}-${selectedJobId}`} jobId={selectedJobId} status={list.find((job) => job.id === selectedJobId)?.status} /> : null}
    </div>
  );
}

function JobCard(props: { job: SolveJob; selected: boolean; onSelect: () => void }) {
  const { job } = props;
  const tone =
    job.status === "FAILED"
      ? "danger"
      : job.status === "DONE" && job.feasible
        ? "accent"
        : undefined;

  return (
    <button type="button" onClick={props.onSelect} className="text-left">
      <Card
        className={props.selected ? "ring-2" : ""}
      >
        <div className="flex items-center justify-between gap-2">
          <Chip tone={tone}>{job.status}</Chip>
          <span className="app-muted text-xs">
            {new Date(job.createdAt).toLocaleString("fr-FR")}
          </span>
        </div>
        <p className="mt-2 font-mono text-sm">{scoreLabel(job.hardScore, job.softScore)}</p>
        {job.errorDetail ? (
          <p className="mt-1 text-xs text-red-600">{job.errorDetail}</p>
        ) : (
          <p className="app-muted mt-1 text-xs">budget {job.timeLimitSeconds} s</p>
        )}
      </Card>
    </button>
  );
}

function JobDetail({ jobId, status }: { jobId: string; status: SolveJob["status"] | undefined }) {
  const [tab, setTab] = React.useState<"planning" | "rapport">("planning");
  const schedule = useAsync<ScheduleView | undefined>(
    () => status === "DONE" ? api.schedule(jobId) : Promise.resolve(undefined),
    [jobId, status]
  );
  const report = useAsync<SolveReportEnvelope | undefined>(
    () => status === "DONE" ? api.report(jobId) : Promise.resolve(undefined),
    [jobId, status]
  );

  if (status && ACTIVE_STATUSES.includes(status)) return <Loading label="Le planning est en cours de préparation…" />;
  if (schedule.error || report.error) return <ErrorBanner message={schedule.error ?? report.error ?? "Le résultat n'a pas pu être chargé."} />;

  if (schedule.loading || report.loading) return <Loading label="Chargement du résultat…" />;

  if (!schedule.data) {
    return (
      <EmptyState
        title="Résultat indisponible"
        hint="Le job n'a pas encore produit de planning, ou il a échoué avant la résolution."
      />
    );
  }

  return (
    <Section
      title="Résultat"
      description="Une seule liste d'affectations : le planning d'un groupe, d'un enseignant ou d'une salle n'en sont que des filtres."
      right={
        <div className="app-surface-muted flex gap-1 rounded-lg border p-1">
          {(["planning", "rapport"] as const).map((candidate) => (
            <button
              key={candidate}
              type="button"
              onClick={() => setTab(candidate)}
              className="rounded-md px-3 py-1.5 text-sm font-semibold capitalize transition"
              style={
                tab === candidate
                  ? { background: "var(--app-accent)", color: "white" }
                  : undefined
              }
            >
              {candidate}
            </button>
          ))}
        </div>
      }
    >
      {tab === "planning" ? (
        <ScheduleGrid schedule={schedule.data} />
      ) : report.data ? (
        <ReportPanel envelope={report.data} />
      ) : (
        <EmptyState title="Aucun rapport" hint="Le rapport est produit à la fin de la résolution." />
      )}
    </Section>
  );
}
