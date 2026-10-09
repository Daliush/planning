import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import { fileURLToPath } from "node:url";
import { resolve, sep } from "node:path";
import { readdir } from "node:fs/promises";

// Interroge la découverte réelle de Codex sans créer de conversation ni lancer de modèle.
const projectRoot = resolve(fileURLToPath(new URL("../", import.meta.url)));
const skillRoot = resolve(projectRoot, ".agents/skills");
const child = spawn("codex", ["app-server", "--stdio"], {
  cwd: projectRoot, windowsHide: true, stdio: ["pipe", "pipe", "pipe"],
});
const pending = new Map();
let sequence = 0;
const lines = createInterface({ input: child.stdout });
child.stderr.on("data", () => {}); // Ne pas recopier la configuration personnelle dans les preuves.
function failPending(error) {
  for (const waiter of pending.values()) { clearTimeout(waiter.timer); waiter.reject(error); }
  pending.clear();
}
child.on("error", failPending);
child.on("exit", (code) => failPending(new Error(`Codex a quitté le contrôle (${code}).`)));
lines.on("line", (line) => {
  let response;
  try { response = JSON.parse(line); } catch { return; }
  const waiter = pending.get(response.id);
  if (!waiter) return;
  pending.delete(response.id);
  clearTimeout(waiter.timer);
  if (response.error) waiter.reject(new Error(response.error.message));
  else waiter.resolve(response.result);
});
function request(method, params) {
  const id = ++sequence;
  return new Promise((resolveRequest, reject) => {
    const timer = setTimeout(() => {
      pending.delete(id); reject(new Error(`Délai dépassé pour ${method}.`));
    }, 15000);
    pending.set(id, { resolve: resolveRequest, reject, timer });
    child.stdin.write(`${JSON.stringify({ id, method, params })}\n`);
  });
}
try {
  await request("initialize", { clientInfo: { name: "planning_discovery_check", version: "1.0.0" } });
  child.stdin.write(`${JSON.stringify({ method: "initialized", params: {} })}\n`);
  const result = await request("skills/list", { cwds: [projectRoot], forceReload: true });
  const entry = result.data.find((value) => resolve(value.cwd) === projectRoot);
  if (!entry) throw new Error("Codex n'a pas retourné les skills du dépôt.");
  const localSkills = entry.skills.filter((skill) => resolve(skill.path).startsWith(skillRoot + sep));
  const expected = (await readdir(skillRoot, { withFileTypes: true })).filter((value) => value.isDirectory()).map((value) => value.name);
  const failures = expected.filter((name) => !localSkills.some((skill) => skill.name === name && skill.enabled));
  const localErrors = (entry.errors ?? []).filter((error) => resolve(error.path).startsWith(skillRoot + sep));
  if (failures.length || localErrors.length) throw new Error(`Skills absents/désactivés : ${failures.join(", ")}; erreurs locales : ${localErrors.length}.`);
  console.log(`Codex découvre ${localSkills.length} skills actifs dans ce dépôt :`);
  for (const skill of localSkills.sort((a, b) => a.name.localeCompare(b.name))) console.log(`- ${skill.name}`);
  await request("config/read", { cwd: projectRoot, includeLayers: false });
  console.log("Lecture native de la configuration du projet réussie.");
} catch (error) {
  console.error(error.message); process.exitCode = 1;
} finally {
  lines.close(); child.stdin.end(); child.kill();
}
