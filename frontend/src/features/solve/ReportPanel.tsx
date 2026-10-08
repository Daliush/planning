import React from "react";
import type { SolveReportEnvelope, Violation } from "../../api/types";
import { Card, Chip, Section } from "../../ui/Section";

/**
 * Le contrat d'affichage minimal du §16.6 : un verdict binaire, les violations
 * dures d'abord et triées par pénalité décroissante, chaque violation nommant les
 * objets impliqués, et le total par contrainte pour repérer une règle massivement
 * violée — signe d'une configuration incohérente plutôt que d'un planning
 * difficile.
 */
export function ReportPanel({ envelope }: { envelope: SolveReportEnvelope }) {
  const { report, constraintLabels } = envelope;
  const [showSoft, setShowSoft] = React.useState(false);

  const totals = React.useMemo(
    () =>
      Object.entries(report.penaltyByConstraint)
        .sort(([, a], [, b]) => b - a)
        .filter(([, penalty]) => penalty > 0),
    [report.penaltyByConstraint]
  );

  return (
    <div className="space-y-4">
      <Section
        title="Verdict"
        description="Aucune quantité de confort ne compense une contrainte dure violée : le score est comparé lexicographiquement."
      >
        <div className="flex flex-wrap items-center gap-3">
          {report.feasible ? (
            <span className="rounded-lg bg-emerald-50 px-3 py-2 text-sm font-semibold text-emerald-800">
              Planning valide — aucune contrainte dure violée
            </span>
          ) : (
            <span className="rounded-lg bg-red-50 px-3 py-2 text-sm font-semibold text-red-800">
              {report.hardViolations.length} violation(s) bloquante(s) à corriger
            </span>
          )}
          <Chip>score dur {report.hardScore}</Chip>
          <Chip>score souple {report.softScore}</Chip>
        </div>

        {totals.length > 0 ? (
          <div className="mt-4">
            <h3 className="app-muted mb-2 text-xs font-bold uppercase tracking-wide">
              Pénalité totale par contrainte
            </h3>
            <div className="space-y-1">
              {totals.map(([constraintId, penalty]) => (
                <div key={constraintId} className="flex items-center gap-2 text-sm">
                  <span className="w-56 shrink-0 truncate">
                    {constraintLabels[constraintId] ?? constraintId}
                  </span>
                  <div className="app-surface-muted h-2 flex-1 overflow-hidden rounded-full border">
                    <div
                      className="h-full rounded-full"
                      style={{
                        width: `${Math.round((penalty / totals[0][1]) * 100)}%`,
                        background: "var(--app-accent)",
                      }}
                    />
                  </div>
                  <span className="app-muted w-20 shrink-0 text-right font-mono text-xs">
                    {penalty}
                  </span>
                </div>
              ))}
            </div>
          </div>
        ) : null}
      </Section>

      {report.hardViolations.length > 0 ? (
        <Section
          title="Violations bloquantes"
          description="Triées par pénalité décroissante : c'est ce qu'il faut corriger en premier."
        >
          <ViolationList violations={report.hardViolations} />
        </Section>
      ) : null}

      <Section
        title={`Violations souples (${report.softViolations.length})`}
        description="Le solveur les a arbitrées entre elles selon les poids configurés."
        right={
          <button
            type="button"
            className="app-accent text-sm font-semibold"
            onClick={() => setShowSoft((current) => !current)}
          >
            {showSoft ? "Masquer" : "Afficher"}
          </button>
        }
      >
        {showSoft ? (
          <ViolationList violations={report.softViolations} />
        ) : (
          <p className="app-muted text-sm">
            {report.softViolations.length === 0
              ? "Aucune."
              : "Repliées pour garder l'écran lisible."}
          </p>
        )}
      </Section>
    </div>
  );
}

function ViolationList({ violations }: { violations: Violation[] }) {
  return (
    <ul className="space-y-2">
      {violations.slice(0, 100).map((violation, index) => (
        <li key={`${violation.constraintId}-${index}`}>
          <Card>
            <div className="flex items-start justify-between gap-2">
              <p className="text-sm">{violation.message}</p>
              <Chip tone={violation.level === "HARD" ? "danger" : undefined}>
                {violation.penalty}
              </Chip>
            </div>
            <p className="app-muted mt-1 font-mono text-xs">{violation.constraintId}</p>
          </Card>
        </li>
      ))}
      {violations.length > 100 ? (
        <li className="app-muted text-sm">
          … et {violations.length - 100} autre(s), masquées pour rester lisible.
        </li>
      ) : null}
    </ul>
  );
}
