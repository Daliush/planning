import React from "react";
import type { Assignment, ScheduleView } from "../../api/types";
import { EmptyState } from "../../ui/Feedback";
import { dayLabel, shortTime } from "../../utils/format";

type Session = {
  key: string;
  demandId: string;
  demandLabel: string;
  dayInCycle: number;
  startTime: string;
  endTime: string;
  slots: Assignment[];
  unfilled: number;
};

/**
 * Il n'existe qu'un seul emploi du temps : « celui de la 6e A », « celui de
 * Jean » et « l'occupation de la salle B12 » sont trois filtres sur la même liste
 * plate d'affectations (§6.14). Le sélecteur ci-dessous est exactement ce filtre.
 */
export function ScheduleGrid({ schedule }: { schedule: ScheduleView }) {
  const [focus, setFocus] = React.useState<string>("");

  const sessions = React.useMemo(() => groupIntoSessions(schedule.assignments), [schedule]);

  const visible = React.useMemo(
    () =>
      focus
        ? sessions.filter((session) =>
            session.slots.some((assignment) => assignment.resourceId === focus)
          )
        : sessions,
    [sessions, focus]
  );

  const days = React.useMemo(
    () => [...new Set(schedule.slots.map((slot) => slot.dayInCycle))].sort((a, b) => a - b),
    [schedule.slots]
  );

  const startTimes = React.useMemo(
    () =>
      [...new Set(schedule.slots.map((slot) => slot.startTime))].sort((a, b) => a.localeCompare(b)),
    [schedule.slots]
  );

  const grouped = React.useMemo(() => {
    const map = new Map<string, Session[]>();
    visible.forEach((session) => {
      const key = `${session.dayInCycle}|${session.startTime}`;
      map.set(key, [...(map.get(key) ?? []), session]);
    });
    return map;
  }, [visible]);

  if (schedule.assignments.length === 0) {
    return <EmptyState title="Aucune affectation" hint="La résolution n'a encore rien produit." />;
  }

  const byType = new Map<string, typeof schedule.resources>();
  schedule.resources.forEach((resource) => {
    byType.set(resource.typeCode, [...(byType.get(resource.typeCode) ?? []), resource]);
  });

  return (
    <div className="space-y-3">
      <label className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-medium">Emploi du temps de</span>
        <select
          className="app-input w-auto py-1.5 text-sm"
          value={focus}
          onChange={(event) => setFocus(event.target.value)}
        >
          <option value="">Toutes les ressources</option>
          {[...byType.entries()].map(([typeCode, list]) => (
            <optgroup key={typeCode} label={typeCode}>
              {list.map((resource) => (
                <option key={resource.id} value={resource.id}>
                  {resource.label}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        <span className="app-muted text-xs">{visible.length} séance(s)</span>
      </label>

      <div className="overflow-x-auto pb-2">
        <div
          className="schedule-grid min-w-[44rem]"
          style={{ "--schedule-days": days.length } as React.CSSProperties}
        >
          <div />
          {days.map((day) => (
            <div key={day} className="app-muted pb-1 text-center text-xs font-bold uppercase">
              {dayLabel(day)}
            </div>
          ))}

          {startTimes.map((startTime) => (
            <React.Fragment key={startTime}>
              <div className="app-muted pt-2 text-right text-xs font-medium">
                {shortTime(startTime)}
              </div>
              {days.map((day) => {
                const cell = grouped.get(`${day}|${startTime}`) ?? [];
                return (
                  <div key={`${day}-${startTime}`} className="min-h-[3.25rem] space-y-1">
                    {cell.map((session) => (
                      <SessionCard key={session.key} session={session} focus={focus} />
                    ))}
                  </div>
                );
              })}
            </React.Fragment>
          ))}
        </div>
      </div>
    </div>
  );
}

function SessionCard({ session, focus }: { session: Session; focus: string }) {
  const others = session.slots.filter(
    (assignment) => assignment.resourceId && assignment.resourceId !== focus
  );

  return (
    <div
      className="app-surface rounded-lg border p-2 text-xs shadow-sm"
      style={
        session.unfilled > 0
          ? { borderColor: "#dc2626" }
          : { borderColor: "var(--app-accent-soft)" }
      }
    >
      <p className="truncate font-semibold" title={session.demandLabel}>
        {session.demandLabel}
      </p>
      <p className="app-muted">
        {shortTime(session.startTime)} – {shortTime(session.endTime)}
      </p>
      <ul className="app-muted mt-1 space-y-0.5">
        {others.map((assignment) => (
          <li key={assignment.id} className="truncate">
            {assignment.role} : {assignment.resourceLabel}
          </li>
        ))}
        {session.unfilled > 0 ? (
          <li className="font-semibold text-red-600">{session.unfilled} poste(s) non pourvu(s)</li>
        ) : null}
      </ul>
    </div>
  );
}

function groupIntoSessions(assignments: Assignment[]): Session[] {
  const map = new Map<string, Session>();

  assignments.forEach((assignment) => {
    const key = `${assignment.demandId}#${assignment.occurrence}`;
    const existing = map.get(key);
    if (existing) {
      existing.slots.push(assignment);
      if (!assignment.resourceId) existing.unfilled += 1;
      return;
    }
    map.set(key, {
      key,
      demandId: assignment.demandId,
      demandLabel: assignment.demandLabel ?? "Séance",
      dayInCycle: assignment.dayInCycle,
      startTime: assignment.startTime,
      endTime: assignment.endTime,
      slots: [assignment],
      unfilled: assignment.resourceId ? 0 : 1,
    });
  });

  return [...map.values()].sort(
    (a, b) => a.dayInCycle - b.dayInCycle || a.startTime.localeCompare(b.startTime)
  );
}
