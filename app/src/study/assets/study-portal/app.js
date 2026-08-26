const app = document.querySelector("#app");

const SAFE_RESPONSE_TYPES = new Set([
  "scale",
  "single_choice",
  "free_text",
  "integer",
  "ranking",
]);
let pollTimer = null;
let autosaveTimer = null;
let draftRevision = 0;
let investigatorCsrf = "";
let previewSteps = [];

async function request(path, options = {}) {
  const init = {
    method: options.method || "GET",
    credentials: "same-origin",
    headers: { Accept: "application/json", ...(options.headers || {}) },
  };
  if (options.body !== undefined) {
    init.headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(options.body);
  }
  const response = await fetch(`/study/app${path}`, init);
  let payload = {};
  try {
    payload = await response.json();
  } catch {
    payload = {};
  }
  if (!response.ok) {
    const error = new Error("Die Anfrage konnte nicht abgeschlossen werden.");
    error.status = response.status;
    error.code = payload.error;
    throw error;
  }
  return payload;
}

async function downloadInvestigatorExport(path, fallbackFilename) {
  const response = await fetch(`/study/app${path}`, {
    method: "GET",
    credentials: "same-origin",
    headers: { Accept: "application/zip, text/csv" },
  });
  if (!response.ok) {
    throw new Error("Der Export konnte nicht erstellt werden.");
  }
  const disposition = response.headers.get("Content-Disposition") || "";
  const match = disposition.match(/filename="([A-Za-z0-9._-]+)"/);
  const filename = match ? match[1] : fallbackFilename;
  const objectUrl = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
}

function clearApp() {
  clearTimeout(pollTimer);
  clearTimeout(autosaveTimer);
  pollTimer = null;
  autosaveTimer = null;
  app.replaceChildren();
  window.scrollTo({ top: 0, left: 0, behavior: "instant" });
}

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = String(text);
  return node;
}

function pageFrame(label, heading) {
  const section = element("section", "page-card");
  if (label) section.append(element("p", "eyebrow", label));
  const title = element("h1", "", heading);
  title.tabIndex = -1;
  section.append(title);
  app.append(section);
  requestAnimationFrame(() => title.focus({ preventScroll: true }));
  return section;
}

function primaryButton(text) {
  const button = element("button", "button button-primary", text);
  button.type = "button";
  return button;
}

function renderErrorSummary(errors) {
  clearErrorSummary();

  const summary = element("section", "error-summary");
  summary.id = "error-summary";
  summary.setAttribute("role", "alert");
  summary.setAttribute("aria-labelledby", "error-summary-title");
  summary.tabIndex = -1;
  const title = element(
    "h2",
    "",
    "Bitte prüfe die folgenden Angaben.",
  );
  title.id = "error-summary-title";
  summary.append(title);
  const list = document.createElement("ul");
  for (const error of errors) {
    const item = document.createElement("li");
    const link = element("a", "", error.message);
    link.href = `#${error.id}`;
    link.addEventListener("click", (event) => {
      event.preventDefault();
      const target = document.getElementById(error.id);
      if (target) target.focus();
    });
    item.append(link);
    list.append(item);
    const field = document.getElementById(error.id);
    if (field) field.setAttribute("aria-invalid", "true");
  }
  summary.append(list);
  app.prepend(summary);

  const firstInvalid = document.getElementById(errors[0].id);
  if (firstInvalid) firstInvalid.focus();
}

function clearErrorSummary() {
  const previous = document.getElementById("error-summary");
  if (previous) previous.remove();
}

function renderStudyInformation() {
  const information = element("section", "study-information");
  information.setAttribute("aria-labelledby", "study-information-title");
  const title = element("h2", "study-information-title", "Studieninformation");
  title.id = "study-information-title";
  information.append(
    title,
    element(
      "p",
      "study-information-intro",
      "Alle Informationen zur Teilnahme stehen direkt auf dieser Seite. Lies sie bitte vollständig und stelle der Versuchsleitung alle offenen Fragen, bevor du einwilligst.",
    ),
  );

  const sections = [
    [
      "Worum geht es?",
      [
        "Diese Masterarbeitsstudie an der TH Köln untersucht, wie Menschen mit einem KI-basierten Smartphone-Agenten zusammenarbeiten und seine Ausführung beaufsichtigen.",
        "Der Agent erledigt vorbereitete Smartphone-Aufgaben. Du beobachtest ihn und kannst – abhängig vom jeweiligen Abschnitt – Aktionen bestätigen, pausieren, korrigieren oder abbrechen.",
      ],
    ],
    [
      "Ablauf und Dauer",
      [
        "Du lernst den Agenten zunächst in einer kurzen Übungsphase kennen.",
        "Danach bearbeitest du sechs Aufgaben auf einem vorbereiteten Studien-Smartphone in drei unterschiedlichen Aufsichtsbedingungen (C1, C2 und C3).",
        "Nach den Aufgaben beantwortest du kurze Fragebögen; zum Abschluss folgt ein kurzes Gespräch.",
        "Die Teilnahme dauert gewöhnlich 60 bis 75 Minuten und höchstens 90 Minuten.",
      ],
    ],
    [
      "Welche Daten werden gespeichert?",
      [
        "Verwendet werden ausschließlich vorbereitete Studienkonten und synthetische Inhalte. Du nutzt nicht dein eigenes Telefon oder deine privaten Konten; bei Banking-Aufgaben wird kein echtes Geld bewegt.",
        "Gespeichert werden deine Teilnehmer-ID, Fragebogenantworten, Aufgabenresultate, Agentenaktionen, Bestätigungen und Eingriffe sowie technische Protokolle, Screenshots und Touch-Ereignisse der Studiensitzung.",
        "Dein Name und deine digitale Einwilligung werden getrennt vom pseudonymisierten Forschungsdatensatz gespeichert. Die Auswertung erfolgt unter deiner Teilnehmer-ID.",
        "Die Daten werden auf den vorbereiteten Studiengeräten gespeichert und für die wissenschaftliche Auswertung exportiert.",
      ],
    ],
    [
      "Risiken und mögliche Belastungen",
      [
        "Die gleichzeitige Beobachtung und gelegentliche Bestätigung kann anstrengend sein. Durch Rückfragen, das Overlay oder technische Probleme können kurze Verzögerungen entstehen.",
        "Du kannst jederzeit eine Pause verlangen oder die Teilnahme beenden. Bei Fragen oder Unwohlsein wendest du dich direkt an die Versuchsleitung.",
      ],
    ],
    [
      "Freiwilligkeit und Widerruf",
      [
        "Die Teilnahme ist freiwillig. Du kannst sie jederzeit und ohne Nachteile abbrechen.",
        "Solange deine Daten noch über die Teilnehmer-ID zugeordnet werden können, kannst du ihre Löschung verlangen. Nenne der Versuchsleitung dafür deine Teilnehmer-ID.",
        "Ein möglicher Kursbonus ist unabhängig von deiner Aufgabenleistung. Dafür erforderliche Kontaktdaten werden nur nach einer separaten digitalen Einwilligung und getrennt von den Forschungsdaten verarbeitet.",
      ],
    ],
    [
      "Verwendung der Ergebnisse",
      [
        "Die Ergebnisse fließen in eine Masterarbeit an der TH Köln ein. Berichtet werden zusammengefasste Ergebnisse; einzelne Teilnehmende sollen daraus nicht identifizierbar sein.",
        "Ansprechperson für Fragen, Abbruch oder Datenlöschung ist Andreas als Versuchsleitung der Studie an der TH Köln.",
      ],
    ],
  ];

  for (const [heading, paragraphs] of sections) {
    const card = element("article", "study-information-card");
    card.append(element("h3", "", heading));
    for (const paragraph of paragraphs) card.append(element("p", "", paragraph));
    information.append(card);
  }
  return information;
}

function renderConsent(state, previewIndex = null) {
  clearApp();
  const section = pageFrame("Studienteilnahme", "Einwilligung");
  section.append(
    element(
      "p",
      "lead",
      "Bitte lies die vollständige Studieninformation auf dieser Seite. Bestätige die Einwilligung erst, wenn du alles verstanden hast und offene Fragen geklärt sind.",
    ),
    renderStudyInformation(),
  );
  const form = element("form", "consent-form");
  form.noValidate = true;
  const nameGroup = element("div", "field-group");
  const nameLabel = element("label", "", "Vollständiger Name");
  nameLabel.htmlFor = "consent-full-name";
  const name = document.createElement("input");
  name.id = "consent-full-name";
  name.name = "full_name";
  name.type = "text";
  name.autocomplete = "name";
  name.required = true;
  nameGroup.append(nameLabel, name);
  form.append(nameGroup);
  const acknowledgements = [
    ["study_information_read", "Ich habe die oben auf dieser Seite dargestellte Studieninformation vollständig gelesen, verstanden und konnte Fragen stellen."],
    ["voluntary_participation", "Ich nehme freiwillig an der Studie teil."],
    ["data_processing_agreed", "Ich stimme der beschriebenen Aufzeichnung und pseudonymisierten Verarbeitung meiner Studiendaten zu."],
    ["withdrawal_understood", "Ich weiß, dass ich meine Einwilligung jederzeit ohne Nachteile widerrufen kann."],
  ];
  for (const [id, labelText] of acknowledgements) {
    const row = element("div", "choice");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.id = `consent-${id}`;
    checkbox.name = id;
    const label = element("label", "", labelText);
    label.htmlFor = checkbox.id;
    row.append(checkbox, label);
    form.append(row);
  }
  const submit = primaryButton("Einwilligung verbindlich absenden");
  submit.type = "submit";
  submit.id = "submit-consent";
  form.append(submit);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const errors = [];
    if (!name.value.trim()) {
      errors.push({
        id: name.id,
        message: "Bitte gib deinen vollständigen Namen ein.",
      });
    }
    const values = {};
    for (const [id] of acknowledgements) {
      const checkbox = document.getElementById(`consent-${id}`);
      values[id] = checkbox.checked;
      if (!checkbox.checked) {
        errors.push({
          id: checkbox.id,
          message: "Bitte bestätige alle erforderlichen Einwilligungspunkte.",
        });
      }
    }
    if (errors.length) {
      renderErrorSummary(errors);
      return;
    }
    if (previewIndex !== null) {
      renderPreviewStep(previewIndex + 1);
      return;
    }
    submit.disabled = true;
    try {
      const result = await request("/api/participant/consent", {
        method: "POST",
        body: {
          full_name: name.value.trim(),
          acknowledgements: values,
        },
      });
      continueStudyFlow(result);
    } catch {
      submit.disabled = false;
      renderErrorSummary([{ id: submit.id, message: "Die Einwilligung konnte nicht gespeichert werden. Bitte versuche es erneut." }]);
    }
  });
  section.append(form);
}

function renderTraining(state, previewIndex = null) {
  clearApp();
  const section = pageFrame("Vorbereitung", "Übungsphase");
  section.append(
    element(
      "p",
      "lead",
      "Bevor es losgeht, probierst du kurz aus, wie du Caddie beobachten und jederzeit beeinflussen kannst.",
    ),
  );
  const steps = element("div", "training-cards");
  for (const [title, text] of [
    ["Beobachten", "Caddie zeigt verständlich an, was als Nächstes passiert."],
    ["Eingreifen", "Du kannst auf dem Smartphone pausieren, stoppen oder Caddie per Sprache korrigieren."],
    ["Weiterarbeiten", "Nach einer Pause kannst du Caddie fortsetzen. Bei einem Problem bleibt die Aufgabe sicher stehen."],
  ]) {
    const card = element("article", "training-card");
    card.append(element("h2", "", title), element("p", "", text));
    steps.append(card);
  }
  const exercise = element("section", "training-exercise");
  exercise.append(
    element("p", "eyebrow", "Freies Üben"),
    element("h2", "", "Probiere Caddie in Ruhe aus"),
    element("p", "", "Der Übungsmodus ist der normale Caddie-Modus. Wähle selbst eine harmlose Aufgabe – es gibt hier keine richtige oder falsche Aufgabe."),
    element("p", "training-command", "Beginne deine Aufgabe wie später mit „Hey Jarvis“. Du kannst Caddie beobachten, pausieren, fortsetzen, korrigieren oder stoppen."),
  );
  const exerciseSteps = element("ol", "training-exercise-steps");
  for (const text of [
    "Tippe auf „Freies Üben starten“. Caddie bleibt dabei im normalen Modus.",
    "Gib Caddie anschließend eine beliebige harmlose Aufgabe deiner Wahl.",
    "Probiere bei Bedarf Pausieren, Fortsetzen, Korrigieren und Stoppen aus.",
    "Wenn du dich sicher fühlst, schließe die Übung unten ab.",
  ]) exerciseSteps.append(element("li", "", text));
  const trainingStatus = element("p", "status-line", "Die Übung wurde noch nicht gestartet.");
  trainingStatus.setAttribute("role", "status");
  const launch = primaryButton("Freies Üben auf dem Smartphone starten");
  launch.id = "participant-start-training";
  launch.addEventListener("click", async () => {
    if (previewIndex !== null) {
      trainingStatus.textContent = "In der echten Studie aktiviert dieser Knopf den normalen Caddie-Modus auf dem Smartphone.";
      return;
    }
    launch.disabled = true;
    try {
      await request("/api/participant/training/start", {
        method: "POST",
        body: { expected_revision: state.session.workflow_revision },
      });
      launch.textContent = "Normalmodus erneut vorbereiten";
      trainingStatus.textContent = "Der normale Caddie-Modus ist bereit. Du kannst jetzt mit „Hey Jarvis“ eine eigene Aufgabe beginnen.";
    } catch {
      renderErrorSummary([{ id: launch.id, message: "Der normale Caddie-Modus konnte nicht vorbereitet werden. Bitte versuche es erneut." }]);
    } finally {
      launch.disabled = false;
    }
  });
  exercise.append(exerciseSteps, launch, trainingStatus);

  const button = primaryButton("Übung abschließen und erste Aufgabe vorbereiten");
  button.id = "participant-continue-training";
  button.addEventListener("click", async () => {
    if (previewIndex !== null) {
      renderPreviewStep(previewIndex + 1);
      return;
    }
    button.disabled = true;
    try {
      renderParticipantState(await request("/api/participant/continue", {
        method: "POST",
        body: { expected_revision: state.session.workflow_revision },
      }));
    } catch {
      button.disabled = false;
      renderErrorSummary([{ id: button.id, message: "Die erste Aufgabe konnte nicht vorbereitet werden. Bitte versuche es erneut." }]);
    }
  });
  section.append(steps, exercise, button);
}

function renderTaskCard(state, previewIndex = null) {
  clearApp();
  const progress = Number.isInteger(state.session.current_trial_index)
    ? `Aufgabe ${state.session.current_trial_index + 1} von 6`
    : "Aktueller Fortschritt";
  const section = pageFrame(progress, "Aktuelle Aufgabe");
  const briefing = state.task && state.task.briefing && typeof state.task.briefing === "object"
    ? state.task.briefing
    : null;
  const briefingCard = element("div", "task-briefing");
  if (briefing && typeof briefing.situation === "string") {
    const situation = element("section", "task-briefing-section task-briefing-situation");
    situation.append(
      element("h2", "task-briefing-heading", "Situation"),
      element("p", "", briefing.situation),
    );
    briefingCard.append(situation);
  }
  if (briefing && Array.isArray(briefing.apps) && briefing.apps.length > 0) {
    const apps = element("section", "task-briefing-section");
    apps.append(element("h2", "task-briefing-heading", "Verwendete Apps"));
    const appGrid = element("div", "task-app-grid");
    for (const app of briefing.apps) {
      if (!app || typeof app.name !== "string" || typeof app.purpose !== "string") continue;
      const appCard = element("article", "task-app-card");
      appCard.append(element("h3", "", app.name), element("p", "", app.purpose));
      appGrid.append(appCard);
    }
    apps.append(appGrid);
    briefingCard.append(apps);
  }
  if (briefing && typeof briefing.goal === "string") {
    const goal = element("section", "task-briefing-section task-briefing-goal");
    goal.append(
      element("h2", "task-briefing-heading", "Ziel"),
      element("p", "", briefing.goal),
    );
    briefingCard.append(goal);
  }
  if (briefing && typeof briefing.preparation === "string") {
    const preparation = element("section", "task-briefing-section task-briefing-preparation");
    preparation.append(
      element("h2", "task-briefing-heading", "Vorbereitung am Smartphone"),
      element("p", "", briefing.preparation),
    );
    briefingCard.append(preparation);
  }
  if (briefingCard.childElementCount > 0) section.append(briefingCard);
  const button = primaryButton("Ausgangslage angesehen – Aufgabe beginnen");
  button.addEventListener("click", async () => {
    if (previewIndex !== null) {
      renderPreviewStep(previewIndex + 1);
      return;
    }
    button.disabled = true;
    try {
      const started = await request("/api/participant/start", {
        method: "POST",
        body: { expected_revision: state.session.workflow_revision },
      });
      renderParticipantState(started);
    } catch {
      button.disabled = false;
      renderErrorSummary([
        {
          id: button.id,
          message: "Die Aufgabe konnte nicht gestartet werden. Bitte versuche es erneut oder wende dich an die Versuchsleitung.",
        },
      ]);
    }
  });
  button.id = "participant-start";
  section.append(button);
}

function spokenTaskInstruction(state) {
  if (!state.task || typeof state.task.instruction !== "string") return null;
  const instruction = state.task.instruction.trim();
  if (!instruction) return null;
  const withoutWakeWord = instruction
    .replace(/^hey\s+jarvis\s*[,.:;!-]?\s*/i, "")
    .replace(/^jarvis\s*[,.:;!-]?\s*/i, "");
  if (!withoutWakeWord) return "Hey Jarvis";
  const command = withoutWakeWord.charAt(0).toLocaleLowerCase("de-DE")
    + withoutWakeWord.slice(1);
  return `Hey Jarvis, ${command}`;
}

async function pollTrialStatus(sessionId) {
  try {
    const state = await request("/api/participant/state");
    const workflow = state.session && state.session.workflow_state;
    if (workflow === "task_card" || workflow === "trial_running") {
      pollTimer = setTimeout(() => pollTrialStatus(sessionId), 1000);
      return;
    }
    continueStudyFlow(state);
  } catch (error) {
    if (error.status === 401 || error.status === 409) {
      continueStudyFlow({ session_id: sessionId });
      return;
    }
    renderOffline(sessionId);
  }
}

function renderTrialRunning(state, previewIndex = null) {
  clearApp();
  const section = pageFrame("Aktueller Durchgang", "Aufgabe läuft");
  section.append(
    element(
      "p",
      "lead",
      "Sprich den folgenden Satz vollständig zum Studien-Smartphone. Diese Seite aktualisiert sich danach automatisch.",
    ),
  );
  const spokenInstruction = spokenTaskInstruction(state);
  if (spokenInstruction) {
    const prompt = element("section", "task-speech-prompt");
    prompt.append(
      element("p", "eyebrow", "Jetzt laut sagen"),
      element("blockquote", "task-spoken-command", spokenInstruction),
    );
    section.append(prompt);
  }
  const briefing = state.task && state.task.briefing && typeof state.task.briefing === "object"
    ? state.task.briefing
    : null;
  if (briefing && typeof briefing.goal === "string" && briefing.goal.trim()) {
    const reminder = element("section", "task-goal-reminder");
    reminder.append(
      element("h2", "task-briefing-heading", "Ziel und wichtige Werte"),
      element("p", "", briefing.goal),
    );
    if (Array.isArray(briefing.reference_values) && briefing.reference_values.length > 0) {
      const values = element("ul", "task-reference-values");
      for (const value of briefing.reference_values) {
        if (typeof value === "string" && value.trim()) values.append(element("li", "", value));
      }
      reminder.append(values);
    }
    section.append(reminder);
  }
  const status = element("p", "status-line", "Status wird geprüft …");
  status.setAttribute("role", "status");
  section.append(status);
  if (previewIndex === null) {
    pollTimer = setTimeout(() => pollTrialStatus(state.session.id), 1000);
  }
}

function continueStudyFlow(state) {
  const sessionId = Number.isInteger(state && state.session && state.session.id)
    ? state.session.id
    : Number.isInteger(state && state.session_id)
      ? state.session_id
      : null;
  clearApp();
  pageFrame("", "Studienablauf wird fortgesetzt …");
  loadBootstrap(sessionId);
}

function safeInstrument(candidate) {
  if (!candidate || typeof candidate !== "object" || !Array.isArray(candidate.items)) {
    return null;
  }
  const items = [];
  for (const raw of candidate.items) {
    if (
      !raw ||
      typeof raw.id !== "string" ||
      !/^[a-z][a-z0-9_]{0,63}$/.test(raw.id) ||
      typeof raw.prompt !== "string" ||
      !SAFE_RESPONSE_TYPES.has(raw.response_type) ||
      !Array.isArray(raw.response_anchors) ||
      !raw.response_anchors.every((anchor) => typeof anchor === "string")
    ) {
      return null;
    }
    items.push({
      id: raw.id,
      prompt: raw.prompt,
      response_type: raw.response_type,
      response_anchors: raw.response_anchors.slice(0, 24),
      minimum: Number.isInteger(raw.minimum) ? raw.minimum : null,
      maximum: Number.isInteger(raw.maximum) ? raw.maximum : null,
      step: Number.isInteger(raw.step) && raw.step > 0 ? raw.step : 1,
      required: raw.required !== false,
      section: typeof raw.section === "string" ? raw.section : "",
    });
  }
  return {
    id: typeof candidate.id === "string" ? candidate.id : "instrument",
    title: typeof candidate.title === "string" ? candidate.title : "Fragebogen",
    items,
  };
}

function instrumentForState(state) {
  return safeInstrument(state.instrument);
}

function appendAnchors(container, anchors) {
  if (!anchors.length) return;
  const row = element("div", "anchor-row");
  for (const anchor of anchors) row.append(element("span", "", anchor));
  container.append(row);
}

function appendRadioGroup(form, item, values, labelValues) {
  const fieldset = document.createElement("fieldset");
  fieldset.className = "question-card";
  fieldset.id = item.id;
  fieldset.tabIndex = -1;
  fieldset.append(element("legend", "", item.prompt));
  const grid = element(
    "div",
    item.response_type === "single_choice"
      ? "choice-grid choice-grid--options"
      : "choice-grid choice-grid--scale",
  );
  values.forEach((value, index) => {
    const choice = element("div", "choice");
    const input = document.createElement("input");
    input.type = "radio";
    input.name = item.id;
    input.id = `${item.id}_${index}`;
    input.value = String(value);
    const label = element("label", "", labelValues[index]);
    label.htmlFor = input.id;
    choice.append(input, label);
    grid.append(choice);
  });
  fieldset.append(grid);
  appendAnchors(fieldset, item.response_type === "scale" ? item.response_anchors : []);
  form.append(fieldset);
}

function appendRange(form, item) {
  const fieldset = document.createElement("fieldset");
  fieldset.className = "question-card";
  fieldset.append(element("legend", "", item.prompt));
  const input = document.createElement("input");
  input.type = "range";
  input.id = item.id;
  input.name = item.id;
  input.min = String(item.minimum);
  input.max = String(item.maximum);
  input.step = String(item.step);
  input.value = String(item.minimum);
  input.dataset.answered = "false";
  const value = element("output", "range-value", "Noch nicht beantwortet");
  value.htmlFor = item.id;
  input.addEventListener("input", () => {
    value.textContent = input.value;
  });
  fieldset.append(input, value);
  appendAnchors(fieldset, item.response_anchors);
  form.append(fieldset);
}

function appendTextControl(form, item) {
  const group = element("div", "field-group question-card");
  const label = element("label", "", item.prompt);
  label.htmlFor = item.id;
  const input = item.response_type === "free_text"
    ? document.createElement("textarea")
    : document.createElement("input");
  input.id = item.id;
  input.name = item.id;
  if (!item.required) {
    label.append(element("span", "optional-label", "Optional"));
    input.placeholder = "Wenn du möchtest, kannst du hier etwas ergänzen.";
  }
  if (item.response_type === "integer") {
    input.type = "number";
    input.step = "1";
    input.inputMode = "numeric";
  }
  group.append(label, input);
  form.append(group);
}

function appendRanking(form, item) {
  const fieldset = document.createElement("fieldset");
  fieldset.className = "question-card";
  fieldset.id = item.id;
  fieldset.tabIndex = -1;
  fieldset.append(element("legend", "", item.prompt));
  const rows = element("div", "ranking-grid");
  item.response_anchors.forEach((anchor, index) => {
    const row = element("div", "ranking-row");
    const controlId = `${item.id}_${index}`;
    const label = element("label", "", anchor);
    label.htmlFor = controlId;
    const select = document.createElement("select");
    select.id = controlId;
    select.name = controlId;
    const placeholder = element("option", "", "Rang wählen");
    placeholder.value = "";
    select.append(placeholder);
    for (let rank = 1; rank <= 3; rank += 1) {
      const option = element("option", "", String(rank));
      option.value = String(rank);
      select.append(option);
    }
    row.append(label, select);
    rows.append(row);
  });
  fieldset.append(rows);
  form.append(fieldset);
}

function appendInstrumentItem(form, item) {
  if (item.response_type === "ranking") {
    appendRanking(form, item);
    return;
  }
  if (
    item.response_type === "scale" &&
    item.minimum !== null &&
    item.maximum !== null &&
    item.maximum - item.minimum <= 10
  ) {
    const values = [];
    for (let value = item.minimum; value <= item.maximum; value += item.step) {
      values.push(value);
    }
    appendRadioGroup(form, item, values, values.map(String));
    return;
  }
  if (item.response_type === "scale") {
    appendRange(form, item);
    return;
  }
  if (item.response_type === "single_choice") {
    appendRadioGroup(form, item, item.response_anchors, item.response_anchors);
    return;
  }
  appendTextControl(form, item);
}

function appendInstrumentSection(form, title) {
  if (!title) return;
  const heading = element("div", "questionnaire-section");
  heading.append(
    element("span", "questionnaire-section__line"),
    element("h3", "", title),
  );
  form.append(heading);
}

function hydrateInstrument(form, instrument, answers) {
  if (!answers || typeof answers !== "object" || Array.isArray(answers)) return;
  for (const item of instrument.items) {
    if (!Object.prototype.hasOwnProperty.call(answers, item.id)) continue;
    const value = answers[item.id];
    if (item.response_type === "ranking") {
      if (!value || typeof value !== "object" || Array.isArray(value)) continue;
      item.response_anchors.forEach((anchor, index) => {
        const rank = value[anchor];
        const select = document.getElementById(`${item.id}_${index}`);
        if (select && Number.isInteger(rank) && rank >= 1 && rank <= 3) {
          select.value = String(rank);
        }
      });
      continue;
    }
    if (
      item.response_type === "scale" &&
      item.minimum !== null &&
      item.maximum !== null &&
      item.maximum - item.minimum > 10
    ) {
      const input = document.getElementById(item.id);
      if (
        input &&
        Number.isInteger(value) &&
        value >= item.minimum &&
        value <= item.maximum &&
        (value - item.minimum) % item.step === 0
      ) {
        input.value = String(value);
        input.dataset.answered = "true";
        const output = input.parentElement.querySelector(".range-value");
        if (output) output.textContent = String(value);
      }
      continue;
    }
    if (item.response_type === "scale" || item.response_type === "single_choice") {
      const valid = item.response_type === "single_choice"
        ? typeof value === "string" && item.response_anchors.includes(value)
        : Number.isInteger(value) &&
          value >= item.minimum &&
          value <= item.maximum &&
          (value - item.minimum) % item.step === 0;
      if (!valid) continue;
      const input = Array.from(form.elements[item.id] || []).find(
        (control) => control.value === String(value),
      );
      if (input) input.checked = true;
      continue;
    }
    const input = document.getElementById(item.id);
    if (item.response_type === "free_text" && typeof value === "string") {
      input.value = String(value);
    } else if (item.response_type === "integer" && Number.isInteger(value)) {
      input.value = String(value);
    }
  }
}

function collectAnswers(form, instrument) {
  const data = new FormData(form);
  const answers = {};
  for (const item of instrument.items) {
    if (item.response_type === "ranking") {
      const ranking = {};
      item.response_anchors.forEach((anchor, index) => {
        const value = data.get(`${item.id}_${index}`);
        if (value !== null && value !== "") ranking[anchor] = Number(value);
      });
      answers[item.id] = ranking;
      continue;
    }
    const control = document.getElementById(item.id);
    let value = data.get(item.id);
    if (control && control.type === "range" && control.dataset.answered !== "true") {
      value = null;
    }
    if (value === null || String(value).trim() === "") continue;
    answers[item.id] = ["scale", "integer"].includes(item.response_type)
      ? Number(value)
      : String(value);
  }
  return answers;
}

function validateAnswers(instrument, answers) {
  const errors = [];
  for (const item of instrument.items) {
    const target = document.getElementById(item.id);
    if (target) target.removeAttribute("aria-invalid");
    if (item.response_type === "ranking") {
      const ranking = answers[item.id] || {};
      const missingIndex = item.response_anchors.findIndex(
        (anchor) => ranking[anchor] === undefined,
      );
      if (missingIndex !== -1) {
        errors.push({
          id: `${item.id}_${missingIndex}`,
          message: "Bitte weise jeder Bedingung einen Rang zu.",
        });
        continue;
      }
      if (
        new Set(Object.values(ranking)).size !== item.response_anchors.length
      ) {
        const values = Object.values(ranking);
        const duplicateIndex = values.findIndex(
          (value, index) => values.indexOf(value) !== index,
        );
        errors.push({
          id: `${item.id}_${Math.max(duplicateIndex, 0)}`,
          message: "Bitte verwende jeden Rang genau einmal.",
        });
      }
      continue;
    }
    if (item.required && answers[item.id] === undefined) {
      errors.push({ id: item.id, message: `${item.prompt}: Bitte auswählen.` });
    }
  }
  if (
    instrument.id === "task" &&
    ["Ja", "Ich bin mir nicht sicher"].includes(answers.task_anomaly_detected)
  ) {
    for (const id of ["task_anomaly_timing", "task_anomaly_response"]) {
      if (answers[id] !== undefined) continue;
      const item = instrument.items.find((candidate) => candidate.id === id);
      errors.push({ id, message: `${item.prompt}: Bitte auswählen.` });
    }
  }
  return errors;
}

function updateTaskAnomalyQuestions(form, instrument) {
  if (instrument.id !== "task") return;
  const answer = new FormData(form).get("task_anomaly_detected");
  const showFollowUps = answer === "Ja" || answer === "Ich bin mir nicht sicher";
  for (const id of ["task_observation", "task_anomaly_timing", "task_anomaly_response"]) {
    const control = document.getElementById(id);
    const card = control ? control.closest(".question-card") : null;
    if (!card) continue;
    card.hidden = !showFollowUps;
    if (showFollowUps) continue;
    card.querySelectorAll("input, textarea, select").forEach((input) => {
      if (input.type === "radio" || input.type === "checkbox") input.checked = false;
      else input.value = "";
    });
  }
}

async function saveDraft(form, instrument, status) {
  try {
    const result = await request("/api/drafts", {
      method: "POST",
      body: {
        answers: collectAnswers(form, instrument),
        expected_revision: draftRevision,
      },
    });
    if (result.draft && Number.isInteger(result.draft.revision)) {
      draftRevision = result.draft.revision;
    }
    status.textContent = "Zwischenstand gespeichert.";
    return true;
  } catch {
    status.textContent = "Zwischenstand konnte nicht gespeichert werden.";
    return false;
  }
}

async function submitInstrument(form, state, instrument, reviewedAnswers = null, reviewedButton = null) {
  const answers = reviewedAnswers || collectAnswers(form, instrument);
  const errors = validateAnswers(instrument, answers);
  if (errors.length) {
    renderErrorSummary(errors);
    return;
  }
  clearErrorSummary();
  clearTimeout(autosaveTimer);
  const button = reviewedButton || form.querySelector('button[type="submit"]');
  button.disabled = true;
  try {
    const result = await request("/api/participant/submit", {
      method: "POST",
      body: {
        answers,
        expected_revision: draftRevision,
        expected_workflow_revision: state.session.workflow_revision,
      },
    });
    continueStudyFlow(result);
  } catch {
    button.disabled = false;
    renderErrorSummary([{ id: button.id, message: "Die Antworten konnten nicht gespeichert werden. Bitte versuche es erneut." }]);
  }
}

function renderTaskAnswerReview(section, form, state, instrument, answers, previewIndex) {
  form.classList.add("is-reviewing");
  form.querySelectorAll("input, textarea, select, button").forEach((control) => {
    control.disabled = true;
  });
  const review = element("section", "task-answer-review");
  review.tabIndex = -1;
  review.setAttribute("role", "region");
  review.setAttribute("aria-labelledby", "task-answer-review-title");
  const title = element("h2", "", "Eine Angabe kurz bestätigen");
  title.id = "task-answer-review-title";
  review.append(
    element("p", "eyebrow", "Kurz prüfen"),
    title,
    element(
      "p",
      "lead",
      `Bei „Ist dir bei der Aufgabe etwas Ungewöhnliches oder ein möglicher Fehler aufgefallen?“ hast du „${answers.task_anomaly_detected}“ ausgewählt. Bitte prüfe nur diese Angabe noch einmal.`,
    ),
  );
  const actions = element("div", "button-row");
  const back = element("button", "button button-secondary", "← Angabe ändern");
  back.type = "button";
  back.addEventListener("click", () => {
    review.remove();
    form.classList.remove("is-reviewing");
    form.querySelectorAll("input, textarea, select, button").forEach((control) => {
      control.disabled = false;
    });
    const firstQuestion = document.getElementById("task_anomaly_detected");
    if (firstQuestion) firstQuestion.focus();
    if (firstQuestion) firstQuestion.scrollIntoView({ behavior: "smooth", block: "center" });
  });
  const confirm = primaryButton("Angaben so absenden");
  confirm.type = "button";
  confirm.id = "confirm-task-answers";
  confirm.addEventListener("click", async () => {
    if (previewIndex !== null) {
      renderPreviewStep(previewIndex + 1);
      return;
    }
    await submitInstrument(form, state, instrument, answers, confirm);
  });
  actions.append(back, confirm);
  review.append(actions);
  section.append(review);
  review.focus();
  review.scrollIntoView({ behavior: "smooth", block: "center" });
}

function renderInstrument(state, previewIndex = null) {
  clearApp();
  const draft = state.draft &&
    typeof state.draft === "object" &&
    state.draft.answers &&
    typeof state.draft.answers === "object" &&
    !Array.isArray(state.draft.answers) &&
    Number.isInteger(state.draft.revision) &&
    state.draft.revision >= 0
    ? state.draft
    : null;
  draftRevision = draft ? state.draft.revision : 0;
  const instrument = instrumentForState(state);
  if (!instrument) {
    continueStudyFlow(state);
    return;
  }
  const section = pageFrame("Fragebogen", instrument.title);
  const intro = instrument.id === "task"
    ? "Halte zuerst deinen spontanen Eindruck fest. Bewerte anschließend das Ergebnis und die möglichen Folgen."
    : instrument.id === "block"
      ? "Denke an die beiden Aufgaben des gerade abgeschlossenen Abschnitts. Es gibt keine richtigen oder falschen Antworten."
      : instrument.items.some((item) => item.id === "condition_ranking")
        ? "Vergleiche nun die drei Arten der Zusammenarbeit und wähle, wie viel Aufsicht du je nach möglichen Folgen bevorzugst."
        : "Zum Abschluss noch ein paar kurze Angaben zu dir und deiner Vorerfahrung.";
  section.append(element("p", "lead", intro));
  const form = element("form", "instrument-form");
  form.noValidate = true;
  let previousSection = null;
  for (const item of instrument.items) {
    if (item.section && item.section !== previousSection) {
      appendInstrumentSection(form, item.section);
      previousSection = item.section;
    }
    appendInstrumentItem(form, item);
  }
  hydrateInstrument(form, instrument, draft ? draft.answers : {});
  updateTaskAnomalyQuestions(form, instrument);
  const status = element(
    "p",
    "save-status",
    draft ? "Zwischenstand wiederhergestellt." : "",
  );
  status.setAttribute("role", "status");
  const button = primaryButton("Antworten absenden");
  button.type = "submit";
  button.id = "submit-instrument";
  form.append(status, button);
  form.addEventListener("input", (event) => {
    if (event.target.type === "range") event.target.dataset.answered = "true";
    updateTaskAnomalyQuestions(form, instrument);
    if (previewIndex !== null) return;
    clearTimeout(autosaveTimer);
    const saveDraft = () => saveDraftRequest(form, instrument, status);
    autosaveTimer = setTimeout(saveDraft, 600);
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const answers = collectAnswers(form, instrument);
    const errors = validateAnswers(instrument, answers);
    if (errors.length) {
      renderErrorSummary(errors);
      return;
    }
    clearErrorSummary();
    if (instrument.id === "task") {
      clearTimeout(autosaveTimer);
      if (previewIndex === null) {
        const saved = await saveDraftRequest(form, instrument, status);
        if (!saved) {
          renderErrorSummary([
            {
              id: button.id,
              message: "Der aktuelle Zwischenstand konnte nicht gespeichert werden. Bitte versuche es erneut.",
            },
          ]);
          return;
        }
      }
      renderTaskAnswerReview(section, form, state, instrument, answers, previewIndex);
      return;
    }
    if (previewIndex !== null) {
      renderPreviewStep(previewIndex + 1);
      return;
    }
    submitInstrument(form, state, instrument);
  });
  section.append(form);
}

function saveDraftRequest(form, instrument, status) {
  return saveDraft(form, instrument, status);
}

function renderInterview(state, previewIndex = null) {
  clearApp();
  const section = pageFrame("Abschlussgespräch", "Deine Erfahrungen mit Caddie");
  section.append(element("p", "lead", "Beantworte die Fragen bitte in deinen eigenen Worten. Es gibt keine richtigen oder falschen Antworten."));
  const form = element("form", "interview-form");
  form.noValidate = true;
  for (const question of state.interview_guide || []) {
    const group = element("div", "interview-question");
    const label = element("label", "", question.prompt);
    label.htmlFor = `interview-${question.id}`;
    const input = document.createElement("textarea");
    input.id = `interview-${question.id}`;
    input.name = question.id;
    input.rows = 4;
    input.required = true;
    group.append(label, input);
    form.append(group);
  }
  const submit = primaryButton("Antworten absenden");
  submit.id = "submit-interview";
  submit.type = "submit";
  form.append(submit);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const answers = {};
    const errors = [];
    for (const question of state.interview_guide || []) {
      const input = document.getElementById(`interview-${question.id}`);
      if (!input.value.trim()) errors.push({ id: input.id, message: "Bitte beantworte diese Frage." });
      else answers[question.id] = input.value.trim();
    }
    if (errors.length) {
      renderErrorSummary(errors);
      return;
    }
    if (previewIndex !== null) {
      renderPreviewStep(previewIndex + 1);
      return;
    }
    submit.disabled = true;
    try {
      renderParticipantState(await request("/api/participant/interview", {
        method: "POST",
        body: { answers, expected_revision: state.session.workflow_revision },
      }));
    } catch {
      submit.disabled = false;
      renderErrorSummary([{ id: submit.id, message: "Die Antworten konnten nicht gespeichert werden. Bitte versuche es erneut." }]);
    }
  });
  section.append(form);
}

function renderDebrief(state, previewIndex = null) {
  clearApp();
  const completed = state.session.workflow_state === "completed";
  const section = pageFrame("Abschluss", completed ? "Vielen Dank für deine Teilnahme." : "Aufklärung nach der Studie");
  section.append(
    element(
      "p",
      "lead",
      completed
        ? "Deine Antworten wurden gespeichert und die Teilnahme ist beendet. Du kannst diese Seite jetzt schließen."
        : "In einigen Aufgaben waren absichtlich vorbereitete Abweichungen enthalten. Sie dienten dazu zu untersuchen, wann Menschen Fehler bemerken und wie sie darauf reagieren.",
    ),
  );
  if (!completed) {
    const variants = element("div", "debrief-cards");
    for (const variant of state.error_variants || []) {
      const card = element("article", "debrief-card");
      card.append(element("h2", "", "Vorbereitete Abweichung"));
      if (variant.task_instruction) {
        card.append(element("p", "meta", `Aufgabe: ${variant.task_instruction}`));
      }
      card.append(element("p", "", variant.description || variant.id));
      variants.append(card);
    }
    const button = primaryButton("Studie abschließen");
    button.id = "participant-complete-study";
    button.addEventListener("click", async () => {
      if (previewIndex !== null) {
        renderPreviewStep(previewIndex + 1);
        return;
      }
      button.disabled = true;
      try {
        renderParticipantState(await request("/api/participant/continue", {
          method: "POST",
          body: { expected_revision: state.session.workflow_revision },
        }));
      } catch {
        button.disabled = false;
        renderErrorSummary([{ id: button.id, message: "Die Studie konnte nicht abgeschlossen werden. Bitte versuche es erneut." }]);
      }
    });
    section.append(variants, button);
  }
}

function renderModeratorHome() {
  clearApp();
  const section = pageFrame("Geschützter Bereich", "Moderatorbereich");
  section.append(
    element(
      "p",
      "lead",
      "Die Anmeldung war erfolgreich. Die Versuchsleitung kann den Ablauf fortsetzen.",
    ),
  );
}

function outcomeLabel(outcome) {
  return {
    not_started: "Noch nicht angelegt",
    in_progress: "In Bearbeitung",
    completed: "Erfolgreich abgeschlossen",
    aborted: "Abgebrochen",
  }[outcome] || outcome || "Unbekannt";
}

function participantRow(summary) {
  const row = element("button", "participant-row");
  row.type = "button";
  row.addEventListener("click", () => {
    if (summary.test_data && summary.session_id !== null) {
      loadSessionDetail(summary.session_id);
    } else {
      loadParticipantDetail(summary.participant_id);
    }
  });
  const identity = element("span", "participant-identity");
  identity.append(
    element("strong", "", summary.participant_id),
    element("span", "muted", summary.workflow_state || "not_started"),
  );
  row.append(
    identity,
    element("span", "participant-progress", `${summary.completed_trials || 0}/${summary.total_trials || 6} Aufgaben`),
    element(
      "span",
      `status-badge status-${summary.has_problem ? "problem" : summary.outcome}`,
      summary.has_problem && summary.outcome !== "completed"
        ? `Prüfen · ${outcomeLabel(summary.outcome)}`
        : outcomeLabel(summary.outcome),
    ),
  );
  return row;
}

function renderParticipantDashboard(catalog) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "Probanden");
  section.append(element("p", "lead", "Proband auswählen, Fortschritt und Ergebnisse ansehen oder einen neuen Studiendurchlauf anlegen."));
  const actions = element("div", "dashboard-actions");
  const create = primaryButton("+ Neuer Proband");
  create.addEventListener("click", () => renderSessionSetup(catalog));
  const preview = element("button", "button button-secondary", "Oberfläche durchklicken");
  preview.type = "button";
  preview.addEventListener("click", loadPreview);
  const test = element("button", "button button-secondary", "Echten Studientest starten");
  test.type = "button";
  test.addEventListener("click", renderStudyTestSetup);
  actions.append(create, preview, test);
  section.append(actions, element("h2", "section-heading", "Probanden"));
  const live = element("div", "participant-list");
  if (!(catalog.participants || []).length) {
    live.append(element("p", "muted", "Noch keine Probanden angelegt."));
  }
  for (const participant of catalog.participants || []) live.append(participantRow(participant));
  section.append(live, element("h2", "section-heading", "Testläufe"));
  if (!(catalog.test_runs || []).length) {
    section.append(element("p", "muted", "Noch keine gespeicherten Testläufe."));
  } else {
    const tests = element("div", "participant-list test-list");
    for (const run of catalog.test_runs) tests.append(participantRow(run));
    section.append(tests);
  }
}

function valueGrid(values) {
  const grid = element("dl", "value-grid");
  for (const [key, value] of Object.entries(values || {})) {
    grid.append(
      element("dt", "", key),
      element("dd", "", typeof value === "object" ? JSON.stringify(value) : value),
    );
  }
  return grid;
}

function detailGroup(container, title, records, render) {
  const group = element("details", "detail-block");
  group.open = records.length > 0;
  group.append(element("summary", "", `${title} (${records.length})`));
  const content = element("div", "detail-content");
  if (!records.length) content.append(element("p", "muted", "Keine Einträge."));
  for (const record of records) content.append(render(record));
  group.append(content);
  container.append(group);
}

function responseEditorInput(key, value) {
  const wrapper = element("label", "edit-field", key);
  let input;
  if (typeof value === "boolean") {
    input = document.createElement("select");
    for (const optionValue of [true, false]) {
      const option = element("option", "", optionValue ? "Ja" : "Nein");
      option.value = String(optionValue);
      option.selected = optionValue === value;
      input.append(option);
    }
    input.dataset.valueType = "boolean";
  } else if (typeof value === "number") {
    input = document.createElement("input");
    input.type = "number";
    input.step = "any";
    input.value = String(value);
    input.dataset.valueType = "number";
  } else if (value !== null && typeof value === "object") {
    input = document.createElement("textarea");
    input.value = JSON.stringify(value, null, 2);
    input.dataset.valueType = "json";
  } else {
    input = document.createElement("input");
    input.type = "text";
    input.value = value === null ? "" : String(value);
    input.dataset.valueType = "string";
  }
  input.name = key;
  wrapper.append(input);
  return wrapper;
}

function parsedEditorValue(input) {
  if (input.dataset.valueType === "number") {
    const value = Number(input.value);
    if (!Number.isFinite(value)) throw new Error("Ungültige Zahl");
    return value;
  }
  if (input.dataset.valueType === "boolean") return input.value === "true";
  if (input.dataset.valueType === "json") return JSON.parse(input.value);
  return input.value;
}

function editableResponseCard(session, response, participantId) {
  const item = element("article", "record-card");
  item.append(
    element("h3", "", `${response.instrument_id} · Position ${response.position + 1}`),
    valueGrid(response.answers),
  );
  const edit = element("button", "button button-secondary", "Bearbeiten");
  edit.type = "button";
  edit.addEventListener("click", () => {
    edit.hidden = true;
    const form = element("form", "edit-response-form");
    const fields = element("div", "edit-fields");
    for (const [key, value] of Object.entries(response.answers || {})) {
      fields.append(responseEditorInput(key, value));
    }
    const status = element("p", "save-status", "Änderungen werden revisionsgeschützt protokolliert.");
    status.setAttribute("role", "status");
    const actions = element("div", "dashboard-actions");
    const cancel = element("button", "button button-secondary", "Abbrechen");
    cancel.type = "button";
    cancel.addEventListener("click", () => {
      form.remove();
      edit.hidden = false;
    });
    const save = primaryButton("Korrektur speichern");
    save.type = "submit";
    actions.append(cancel, save);
    form.append(fields, status, actions);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      save.disabled = true;
      try {
        const answers = {};
        for (const input of form.querySelectorAll("input, textarea, select")) {
          answers[input.name] = parsedEditorValue(input);
        }
        await investigatorRequest(
          `/api/sessions/${session.id}/responses/${encodeURIComponent(response.instrument_id)}/${response.position}/correct`,
          {
            answers,
            missing: response.missing || {},
            expected_revision: response.revision,
          },
        );
        if (session.mode === "test") await loadSessionDetail(session.id);
        else await loadParticipantDetail(participantId);
      } catch (error) {
        status.textContent = error.status === 409
          ? "Die Werte wurden inzwischen geändert. Bitte Ansicht neu laden."
          : "Korrektur konnte nicht gespeichert werden. Eingaben prüfen.";
        save.disabled = false;
      }
    });
    item.append(form);
  });
  item.append(edit);
  return item;
}

function renderTrialEvidence(entry) {
  const section = element("section", "evidence-section");
  section.append(
    element("h3", "", "Fehlerwahrnehmung je Aufgabe"),
    element("p", "muted", "Getrennte Sicht auf Versuchsplan, tatsächliche Ausführung, Selbstauskunft und beobachtbares Eingreifen."),
  );
  const trials = entry.trials || [];
  if (!trials.length) {
    section.append(element("p", "muted", "Noch keine abgeschlossene Aufgabe."));
    return section;
  }
  const cards = element("div", "evidence-grid");
  for (const trial of trials) {
    const index = trial.trial_index;
    const events = (entry.events || []).filter((event) =>
      Number(event.details && event.details.trial_index) === index,
    );
    const response = (entry.responses || []).find((item) =>
      item.instrument_id === "task" && item.position === index,
    );
    const injected = events.some((event) => event.event_type === "error_injected");
    const reactions = events.filter((event) => [
      "participant_intervention",
      "correction_routed",
      "correction_rewound",
      "compensation_step_completed",
    ].includes(event.event_type));
    const answers = response && response.answers ? response.answers : {};
    const card = element("article", "evidence-card");
    card.append(element("h4", "", `Aufgabe ${index + 1}`));
    const facts = element("dl", "status-grid");
    for (const [label, value] of [
      ["Fehler vorgesehen", trial.assigned_error ? "Ja" : "Nein"],
      ["Fehler tatsächlich ausgelöst", trial.assigned_error ? (injected ? "Ja, protokolliert" : "Nicht protokolliert") : "Nicht vorgesehen"],
      ["Vom Probanden berichtet", answers.task_anomaly_detected || "Noch keine Angabe"],
      ["Zeitpunkt", answers.task_anomaly_timing || "–"],
      ["Reaktion laut Fragebogen", answers.task_anomaly_response || "–"],
      ["Beobachtbares Eingreifen", reactions.length ? reactions.map((event) => event.event_type).join(", ") : "Kein Laufzeitereignis"],
    ]) facts.append(element("dt", "", label), element("dd", "", value));
    card.append(facts);
    if (answers.task_observation) card.append(element("p", "evidence-note", `Freitext: ${answers.task_observation}`));
    cards.append(card);
  }
  section.append(cards);
  return section;
}

function renderParticipantDetail(detail) {
  clearApp();
  const testData = (detail.sessions || []).some((entry) => entry.session.mode === "test");
  const section = pageFrame(testData ? "TEST DATA" : "Proband", detail.participant_id);
  const back = element("button", "button button-secondary", "← Alle Probanden");
  back.type = "button";
  back.addEventListener("click", loadInvestigatorDashboard);
  section.append(back);
  if (!detail.sessions || !detail.sessions.length) {
    section.append(element("p", "lead", "Für diesen Probanden wurde noch keine Sitzung angelegt."));
    const create = primaryButton(`${detail.participant_id} anlegen`);
    create.addEventListener("click", async () => {
      create.disabled = true;
      try {
        await investigatorRequest("/api/sessions", { participant_id: detail.participant_id, mode: "live" });
        await loadParticipantDetail(detail.participant_id);
      } catch { create.disabled = false; }
    });
    section.append(create);
    return;
  }
  for (const entry of detail.sessions) {
    const session = entry.session;
    const card = element("article", "session-detail");
    card.append(element("h2", "", `Sitzung #${session.id}`));
    const metadata = element("dl", "status-grid");
    for (const [label, value] of [
      ["Status", session.status], ["Ablauf", session.workflow_state],
      ["Aktuelle Aufgabe", session.current_trial_index === null ? "–" : `${session.current_trial_index + 1}/6`],
      ["Angelegt", session.entered_at], ["Quelle", session.source],
    ]) metadata.append(element("dt", "", label), element("dd", "", value));
    card.append(metadata);
    const open = primaryButton("Sitzung öffnen / fortsetzen");
    open.addEventListener("click", () => loadInvestigatorState(session.id));
    card.append(open, renderTrialEvidence(entry));
    detailGroup(
      card,
      "Ausgefüllte Fragebögen",
      entry.responses || [],
      (response) => editableResponseCard(session, response, detail.participant_id),
    );
    detailGroup(card, "Beobachtungen", entry.observations || [], (observation) => {
      const item = element("article", "record-card");
      item.append(element("h3", "", `${observation.task_id} · ${observation.condition}`), element("p", "", observation.notes || "Keine Notiz"));
      return item;
    });
    detailGroup(card, "Ergebnisse", entry.trials || [], (trial) => element("div", "timeline-row", `Aufgabe ${trial.trial_index + 1} · ${trial.outcome} · ${trial.submitted_at}`));
    detailGroup(card, "Interview", entry.interviews || [], (note) => element("div", "record-card", `${note.question_id}: ${note.answer}`));
    detailGroup(card, "Study Logs", entry.events || [], (event) => {
      const item = element("div", "timeline-row", `${event.created_at} · ${event.event_type} · ${event.actor}`);
      if (Object.keys(event.details || {}).length) item.append(valueGrid(event.details));
      return item;
    });
    section.append(card);
  }
  if (!testData) {
    const danger = element("details", "danger-zone");
    danger.append(element("summary", "", "Proband löschen"));
    danger.append(
      element(
        "p",
        "muted",
        "Entfernt diesen Probanden mit allen Sitzungen, Antworten, Einwilligungen und Study Logs aus der aktuellen App-Datenbank. Bereits erstellte Backups bleiben erhalten.",
      ),
    );
    const status = element("p", "save-status", "");
    status.setAttribute("role", "status");
    const remove = element("button", "button button-danger", "Proband aus App löschen");
    remove.type = "button";
    remove.addEventListener("click", async () => {
      const confirmed = window.confirm(
        `${detail.participant_id} aus der App-Datenbank löschen? Bereits erstellte Backups bleiben erhalten.`,
      );
      if (!confirmed) return;
      remove.disabled = true;
      try {
        await investigatorRequest(
          `/api/participants/${encodeURIComponent(detail.participant_id)}/delete`,
          { confirmed: true },
        );
        await loadInvestigatorDashboard();
      } catch (error) {
        status.textContent = error.status === 400
          ? "Eine aktive Sitzung muss zuerst abgebrochen werden."
          : "Der Proband konnte nicht gelöscht werden.";
        remove.disabled = false;
      }
    });
    danger.append(remove, status);
    section.append(danger);
  }
}

async function loadParticipantDetail(participantId) {
  try {
    renderParticipantDetail(await request(`/api/participants/${encodeURIComponent(participantId)}`));
  } catch { renderOffline(); }
}

async function loadSessionDetail(sessionId) {
  try {
    renderParticipantDetail(await request(`/api/sessions/${sessionId}/details`));
  } catch { renderOffline(); }
}

async function loadInvestigatorDashboard() {
  try {
    renderParticipantDashboard(await request("/api/participants"));
  } catch { renderOffline(); }
}

function renderPreviewStep(index) {
  if (!previewSteps[index]) {
    loadInvestigatorDashboard();
    return;
  }
  renderParticipantState(previewSteps[index], index);
}

function appendPreviewControls(index) {
  const section = app.querySelector(".page-card");
  if (!section) return;
  const banner = element(
    "p",
    "preview-banner",
    `VORSCHAU ${index + 1}/${previewSteps.length} · NICHT GESPEICHERT · KEINE TELEFONAKTIONEN`,
  );
  section.insertBefore(banner, section.firstChild);
  const actions = element("div", "dashboard-actions preview-actions");
  const exit = element("button", "button button-secondary", "Vorschau beenden");
  exit.type = "button";
  exit.addEventListener("click", loadInvestigatorDashboard);
  actions.append(exit);
  if (index > 0) {
    const previous = element("button", "button button-secondary", "Zurück");
    previous.type = "button";
    previous.addEventListener("click", () => renderPreviewStep(index - 1));
    actions.append(previous);
  }
  const workflow = previewSteps[index] && previewSteps[index].session
    ? previewSteps[index].session.workflow_state
    : "";
  if (workflow === "trial_running" && index < previewSteps.length - 1) {
    const next = primaryButton("Telefonaufgabe als abgeschlossen anzeigen");
    next.addEventListener("click", () => renderPreviewStep(index + 1));
    actions.append(next);
  }
  section.append(actions);
}

async function loadPreview() {
  try {
    const preview = await request("/api/preview");
    previewSteps = Array.isArray(preview.steps) ? preview.steps : [];
    if (!previewSteps.length) throw new Error("preview unavailable");
    renderPreviewStep(0);
  } catch {
    renderOffline();
  }
}

async function renderStudyTestSetup() {
  let options;
  try {
    options = await request("/api/test-runs/options");
  } catch {
    renderOffline();
    return;
  }
  clearApp();
  const section = pageFrame("TEST DATA", "Echter Studientest");
  section.append(element("p", "lead", "Dieser Lauf setzt die Studien-Apps zurück und nutzt Sprache, C1/C2/C3 sowie den nativen deterministischen Executor. Er bleibt vollständig von echten Probandendaten getrennt."));
  const form = element("form", "session-form");
  const task = document.createElement("select");
  task.id = "test-task";
  for (const entry of options.tasks || []) {
    const option = element("option", "", `${entry.id} · ${entry.criticality}`);
    option.value = entry.id;
    option.dataset.instruction = entry.instruction;
    task.append(option);
  }
  const instruction = element("p", "task-instruction", task.selectedOptions[0]?.dataset.instruction || "");
  task.addEventListener("change", () => {
    instruction.textContent = task.selectedOptions[0]?.dataset.instruction || "";
  });
  const condition = document.createElement("select");
  condition.id = "test-condition";
  const labels = {
    c1_stepwise: "C1 · Schrittweise Bestätigung",
    c2_final_checkpoint: "C2 · Finale Bestätigung",
    c3_voluntary_intervention: "C3 · Freiwillige Intervention",
  };
  for (const value of options.conditions || []) {
    const option = element("option", "", labels[value] || value);
    option.value = value;
    condition.append(option);
  }
  const injectLabel = element("label", "test-checkbox");
  const inject = document.createElement("input");
  inject.type = "checkbox";
  inject.id = "test-inject-error";
  injectLabel.append(inject, document.createTextNode(" Kontrollierten Studienfehler einbauen"));
  const status = element("p", "save-status", "Beim Start werden die Studien-Apps zurückgesetzt.");
  status.setAttribute("role", "status");
  const actions = element("div", "dashboard-actions");
  const back = element("button", "button button-secondary", "← Zurück");
  back.type = "button";
  back.addEventListener("click", loadInvestigatorDashboard);
  const start = primaryButton("Test vorbereiten");
  start.type = "submit";
  actions.append(back, start);
  form.append(
    element("label", "", "Aufgabe"),
    task,
    instruction,
    element("label", "", "Bedingung"),
    condition,
    injectLabel,
    status,
    actions,
  );
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    start.disabled = true;
    status.textContent = "Telefon wird zurückgesetzt und Test wird vorbereitet…";
    try {
      const run = await investigatorRequest("/api/test-runs", {
        task_id: task.value,
        condition: condition.value,
        inject_error: inject.checked,
      });
      renderArmedStudyTest(run);
    } catch {
      status.textContent = "Test konnte nicht vorbereitet werden. Accessibility und laufenden Agent prüfen.";
      start.disabled = false;
    }
  });
  section.append(form);
}

function renderArmedStudyTest(run) {
  clearApp();
  const section = pageFrame("TEST DATA · BEREIT", "Testlauf sprechen");
  section.append(
    element("p", "preview-banner", `${run.participant_id} · NICHT LIVE`),
    element("p", "lead", "Sprich jetzt exakt die folgende Studienaufgabe in Caddie:"),
    element("p", "task-instruction", run.instruction),
    element("p", "status-line", `${run.condition} · Fehler: ${run.inject_error ? "ja" : "nein"}`),
  );
  const actions = element("div", "dashboard-actions");
  const details = primaryButton("Testlauf und Logs ansehen");
  details.addEventListener("click", () => loadSessionDetail(run.session_id));
  const dashboard = element("button", "button button-secondary", "Zur Übersicht");
  dashboard.type = "button";
  dashboard.addEventListener("click", loadInvestigatorDashboard);
  actions.append(details, dashboard);
  section.append(actions);
}

function investigatorRequest(path, body) {
  return request(path, {
    method: "POST",
    headers: {
      "X-CSRF-Token": investigatorCsrf,
      "X-Caddie-Handoff-Recovery": "1",
    },
    body,
  });
}

function renderInvestigatorLogin(state, investigatorSessionId = null) {
  renderLogin(state && state.pin_configured !== false, investigatorSessionId);
}

function renderSessionSetup(state) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "+ Neuer Proband");
  section.append(element("p", "lead", "Die nächste ID wird vorgeschlagen. Die Studienmatrix wird automatisch und unveränderbar zugewiesen."));
  const form = element("form", "session-form");
  const participantLabel = element("label", "", "Probanden-ID");
  participantLabel.htmlFor = "participant-id";
  const participant = document.createElement("input");
  participant.id = "participant-id";
  participant.name = "participant_id";
  participant.pattern = "(?:P)?0*[1-9][0-9]*";
  participant.value = state && state.available_participant_ids
    ? state.available_participant_ids[0] || ""
    : "";
  participant.required = true;
  const actions = element("div", "dashboard-actions");
  const cancel = element("button", "button button-secondary", "Abbrechen");
  cancel.type = "button";
  cancel.addEventListener("click", loadInvestigatorDashboard);
  const submit = primaryButton("Proband anlegen");
  submit.type = "submit";
  actions.append(cancel, submit);
  form.append(participantLabel, participant, actions);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    submit.disabled = true;
    try {
      const created = await investigatorRequest("/api/sessions", {
        participant_id: participant.value,
        mode: "live",
      });
      renderParticipantState(created);
    } catch {
      submit.disabled = false;
      renderErrorSummary([
        { id: participant.id, message: "Sitzung konnte nicht angelegt werden." },
      ]);
    }
  });
  section.append(form);
  if (state && state.message) section.append(element("p", "status-line", state.message));
}

function paperStep(title) {
  const step = document.createElement("details");
  step.className = "paper-step";
  step.append(element("summary", "", title));
  return step;
}

function appendPaperMissingReason(container, itemId) {
  const label = element("label", "", "Fehlgrund");
  label.htmlFor = `${itemId}-missing-reason`;
  const select = document.createElement("select");
  select.id = `${itemId}-missing-reason`;
  select.className = "missing-reason";
  select.name = `${itemId}_missing_reason`;
  for (const [value, text] of [
    ["", "Kein Fehlgrund"],
    ["not_answered", "Nicht beantwortet"],
    ["illegible", "Nicht lesbar"],
    ["paper_missing", "Papier fehlt"],
  ]) {
    const option = element("option", "", text);
    option.value = value;
    select.append(option);
  }
  container.append(label, select);
}

function renderPaperHeldConsent(state, section) {
  const step = paperStep("2 · Einwilligung vom vorliegenden Papier erfassen");
  if (state.paper_progress.confirmed_held_consent) {
    step.append(element("p", "save-status", "Einwilligung erfasst und bestätigt."));
    renderCourseBonusForm(section);
    section.append(step);
    return;
  }
  const form = element("form", "paper-form");
  const name = document.createElement("input");
  name.type = "text";
  name.required = true;
  name.id = "paper-consent-name";
  const consentedAt = document.createElement("input");
  consentedAt.type = "text";
  consentedAt.required = true;
  consentedAt.id = "paper-consented-at";
  const submit = primaryButton("Papier-Einwilligung erfassen");
  submit.type = "submit";
  const status = element("p", "save-status", "");
  status.setAttribute("role", "status");
  form.append(
    element("label", "", "Vollständiger Name auf der Einwilligung"),
    name,
    element("label", "", "Zeitpunkt auf der Einwilligung"),
    consentedAt,
    status,
    submit,
  );
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    submit.disabled = true;
    try {
      await paperRequest(state, "/paper/consent", {
        full_name: name.value,
        consented_at: consentedAt.value,
      });
      await paperRequest(state, "/paper/confirm-consent", {
        confirmed_at: new Date().toISOString(),
      });
      status.textContent = "Einwilligung erfasst und bestätigt.";
      await refreshPaperState(state);
    } catch {
      submit.disabled = false;
      status.textContent = "Einwilligung konnte nicht gespeichert werden.";
    }
  });
  step.append(form);
  section.append(step);
  renderCourseBonusForm(section);
}

function paperRequest(state, path, body) {
  return investigatorRequest(`/api/sessions/${state.session.id}${path}`, body);
}

async function refreshPaperState(state) {
  const refreshed = await request(
    `/api/investigator/state?session_id=${encodeURIComponent(state.session.id)}`,
  );
  renderInvestigatorState(refreshed);
}

function renderCourseBonusForm(section) {
  const step = paperStep("Optionaler Kursbonus-Nachweis");
  const form = element("form", "paper-form");
  const name = document.createElement("input");
  name.type = "text";
  name.id = "course-bonus-name";
  const matriculation = document.createElement("input");
  matriculation.type = "text";
  matriculation.id = "course-bonus-matriculation";
  const consentedAt = document.createElement("input");
  consentedAt.type = "text";
  consentedAt.id = "course-bonus-consented-at";
  const status = element("p", "save-status", "");
  status.setAttribute("role", "status");
  const button = primaryButton("Optionalen Nachweis getrennt speichern");
  button.type = "submit";
  form.append(
    element("label", "", "Vollständiger Name"),
    name,
    element("label", "", "Matrikelnummer"),
    matriculation,
    element("label", "", "Einwilligungszeitpunkt"),
    consentedAt,
    status,
    button,
  );
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    button.disabled = true;
    try {
      const saved = await investigatorRequest("/api/course-bonus", {
        full_name: name.value,
        matriculation_number: matriculation.value,
        consented_at: consentedAt.value,
      });
      status.textContent = "Optionaler Nachweis getrennt gespeichert.";
      button.type = "button";
      button.textContent = "Nach Bestätigung durch den Professor löschen";
      button.disabled = false;
      button.onclick = async () => {
        button.disabled = true;
        try {
          await investigatorRequest(`/api/course-bonus/${saved.id}/delete`, {});
          form.replaceChildren(element("p", "save-status", "Kursbonus-Nachweis gelöscht."));
        } catch {
          button.disabled = false;
          status.textContent = "Kursbonus-Nachweis konnte nicht gelöscht werden.";
        }
      };
    } catch {
      button.disabled = false;
      status.textContent = "Optionaler Nachweis konnte nicht gespeichert werden.";
    }
  });
  step.append(
    element(
      "p",
      "lead",
      "Freiwillig und getrennt von den Forschungsdaten. Keine Teilnehmer-ID eingeben.",
    ),
    form,
  );
  section.append(step);
}

function scopedPaperInstrument(instrument, scope) {
  return {
    ...instrument,
    items: instrument.items.map((item) => ({
      ...item,
      source_id: item.id,
      id: `${scope}_${item.id}`,
    })),
  };
}

function collectPaperAnswers(form, instrument) {
  const data = new FormData(form);
  const answers = {};
  const missing = {};
  const errors = [];
  for (const item of instrument.items) {
    const answerId = item.source_id;
    const reason = data.get(`${item.id}_missing_reason`);
    if (reason) {
      answers[answerId] = null;
      missing[answerId] = String(reason);
      continue;
    }
    if (item.response_type === "ranking") {
      const ranking = {};
      item.response_anchors.forEach((anchor, index) => {
        const value = data.get(`${item.id}_${index}`);
        if (value !== null && value !== "") ranking[anchor] = Number(value);
      });
      if (
        Object.keys(ranking).length !== item.response_anchors.length ||
        new Set(Object.values(ranking)).size !== item.response_anchors.length
      ) {
        errors.push({
          id: `${item.id}_0`,
          message: `${item.prompt}: vollständiges eindeutiges Ranking oder Fehlgrund angeben.`,
        });
      } else {
        answers[answerId] = ranking;
      }
      continue;
    }
    const control = form.elements[item.id];
    let value = data.get(item.id);
    if (
      control &&
      control.type === "range" &&
      control.dataset.answered !== "true"
    ) {
      value = null;
    }
    if (value === null || String(value).trim() === "") {
      errors.push({
        id: item.id,
        message: `${item.prompt}: Wert oder Fehlgrund angeben.`,
      });
      continue;
    }
    answers[answerId] = ["scale", "integer"].includes(item.response_type)
      ? Number(value)
      : String(value);
  }
  return { answers, missing, errors };
}

function completedPaperResponse(state, instrumentId, position) {
  return (state.paper_progress.submitted_response_keys || []).find(
    (entry) =>
      entry.instrument_id === instrumentId && entry.position === position,
  );
}

function renderPaperInstrumentForm(state, entry, instrumentId) {
  const instrument = safeInstrument(entry.instrument);
  if (!instrument) return element("p", "save-status", "Formular nicht verfügbar.");
  const scope = `paper_${instrumentId}_${entry.position}`;
  const scoped = scopedPaperInstrument(instrument, scope);
  const form = element("form", "instrument-form paper-form");
  form.noValidate = true;
  form.append(
    element(
      "h2",
      "",
      instrumentId === "task"
        ? `${entry.position + 1} · ${entry.id}`
        : instrumentId === "block"
          ? `${entry.position + 1} · ${entry.condition}`
          : "Demografie und Präferenzranking",
    ),
  );
  for (const item of scoped.items) {
    appendInstrumentItem(form, item);
    appendPaperMissingReason(form, item.id);
  }
  const status = element("p", "save-status", "");
  status.setAttribute("role", "status");
  const button = primaryButton("Transkription speichern");
  button.type = "submit";
  const completed = completedPaperResponse(
    state,
    instrumentId,
    entry.position,
  );
  if (completed) {
    status.textContent = "Vollständig gespeichert.";
    button.disabled = true;
  }
  form.addEventListener("input", (event) => {
    if (event.target.type === "range") event.target.dataset.answered = "true";
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const collected = collectPaperAnswers(form, scoped);
    if (collected.errors.length) {
      renderErrorSummary(collected.errors);
      return;
    }
    button.disabled = true;
    try {
      await paperRequest(state, "/paper/answers", {
        instrument_id: instrumentId,
        position: entry.position,
        answers: collected.answers,
        missing: collected.missing,
        expected_revision: completed ? completed.revision : 0,
        submit: true,
      });
      status.textContent = "Vollständig gespeichert.";
      await refreshPaperState(state);
    } catch {
      button.disabled = false;
      status.textContent = "Speichern fehlgeschlagen; Eingaben bleiben erhalten.";
    }
  });
  form.append(status, button);
  return form;
}

function renderPaperTaskForms(state, section) {
  const step = paperStep("3 · Sechs Aufgabenfragebögen in Zuweisungsreihenfolge");
  for (const entry of state.paper_plan.tasks) {
    step.append(renderPaperInstrumentForm(state, entry, "task"));
  }
  section.append(step);
}

function renderPaperBlockForms(state, section) {
  const step = paperStep("4 · Drei Bedingungsfragebögen in Zuweisungsreihenfolge");
  for (const entry of state.paper_plan.blocks) {
    step.append(renderPaperInstrumentForm(state, entry, "block"));
  }
  section.append(step);
}

function appendPaperSelect(form, scope, name, label, values) {
  const controlId = `${scope}_${name}`;
  const text = element("label", "", label);
  text.htmlFor = controlId;
  const select = document.createElement("select");
  select.id = controlId;
  select.name = name;
  select.required = true;
  const blank = element("option", "", "Bitte wählen");
  blank.value = "";
  select.append(blank);
  for (const [value, caption] of values) {
    const option = element("option", "", caption);
    option.value = value;
    select.append(option);
  }
  form.append(text, select);
}

function paperBooleanValues() {
  return [["true", "Ja"], ["false", "Nein"]];
}

function renderPaperObservationForm(state, entry) {
  const scope = `paper_observation_${entry.trial_index}`;
  const form = element("form", "paper-form observation-form");
  form.append(
    element(
      "h2",
      "",
      `${entry.task_id} · ${entry.variant.id} · ${entry.variant.description}`,
    ),
  );
  appendPaperSelect(
    form,
    scope,
    "spontaneous_detection",
    "Spontan erkannt",
    paperBooleanValues(),
  );
  appendPaperSelect(form, scope, "detection_stage", "Erkennungsphase", [
    ["stepwise_gate", "Schrittweise Bestätigung"],
    ["final_checkpoint", "Finaler Checkpoint"],
    ["during_execution", "Während der Ausführung"],
    ["after_execution_before_prompt", "Nach Ausführung, vor Hinweis"],
    ["after_moderator_prompt", "Nach Moderationshinweis"],
    ["not_detected", "Nicht erkannt"],
  ]);
  appendPaperSelect(form, scope, "evidence_type", "Evidenz", [
    ["spoken_identification", "Mündliche Benennung"],
    ["rejection", "Ablehnung"],
    ["correction", "Korrektur"],
    ["intervention", "Eingriff"],
    ["none", "Keine"],
    ["other", "Sonstige"],
  ]);
  appendPaperSelect(
    form,
    scope,
    "moderator_prompt_given",
    "Moderationshinweis gegeben",
    paperBooleanValues(),
  );
  appendPaperSelect(
    form,
    scope,
    "detected_only_after_prompt",
    "Erst nach Hinweis erkannt",
    paperBooleanValues(),
  );
  const notesLabel = element("label", "", "Notizen");
  notesLabel.htmlFor = `${scope}_notes`;
  const notes = document.createElement("textarea");
  notes.id = `${scope}_notes`;
  notes.name = "notes";
  const status = element("p", "save-status", "");
  status.setAttribute("role", "status");
  const button = primaryButton("Fehlerbeobachtung speichern");
  button.type = "submit";
  if (
    (state.paper_progress.saved_observation_indices || []).includes(
      entry.trial_index,
    )
  ) {
    status.textContent = "Vollständig gespeichert.";
    button.disabled = true;
  }
  form.append(notesLabel, notes, status, button);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const observation = {
      "spontaneous_detection": data.get("spontaneous_detection") === "true",
      "detection_stage": String(data.get("detection_stage") || ""),
      "evidence_type": String(data.get("evidence_type") || ""),
      "moderator_prompt_given": data.get("moderator_prompt_given") === "true",
      "detected_only_after_prompt":
        data.get("detected_only_after_prompt") === "true",
      notes: String(data.get("notes") || ""),
    };
    const invalid = [
      "spontaneous_detection",
      "detection_stage",
      "evidence_type",
      "moderator_prompt_given",
      "detected_only_after_prompt",
    ].some((name) => data.get(name) === "");
    if (
      invalid ||
      (
        observation.detection_stage === "after_moderator_prompt" &&
        (
          !observation.moderator_prompt_given ||
          !observation.detected_only_after_prompt
        )
      )
    ) {
      status.textContent = "Bitte konsistente Beobachtungsangaben wählen.";
      return;
    }
    button.disabled = true;
    try {
      await paperRequest(state, "/paper/observations", {
        trial_index: entry.trial_index,
        observation,
      });
      status.textContent = "Vollständig gespeichert.";
      await refreshPaperState(state);
    } catch {
      button.disabled = false;
      status.textContent = "Speichern fehlgeschlagen; Eingaben bleiben erhalten.";
    }
  });
  return form;
}

function renderPaperErrorForms(state, section) {
  const step = paperStep("5 · Nur die drei zugewiesenen Fehlerbeobachtungen");
  for (const entry of state.paper_plan.errors) {
    step.append(renderPaperObservationForm(state, entry));
  }
  section.append(step);
}

function renderPaperEndForm(state, section) {
  const step = paperStep("6 · Demografie und Präferenzranking");
  step.append(renderPaperInstrumentForm(state, state.paper_plan.end, "end"));
  section.append(step);
}

function renderPaperInterview(state, section) {
  const step = paperStep("7 · Interviewnotizen");
  for (const question of state.paper_plan.interview) {
    const form = element("form", "paper-form");
    const label = element("label", "", question.prompt);
    label.htmlFor = `paper_interview_${question.id}`;
    const answer = document.createElement("textarea");
    answer.id = `paper_interview_${question.id}`;
    answer.name = "answer";
    const status = element("p", "save-status", "");
    status.setAttribute("role", "status");
    const button = primaryButton("Interviewnotiz speichern");
    button.type = "submit";
    if (
      (state.paper_progress.interview_question_ids || []).includes(question.id)
    ) {
      status.textContent = "Vollständig gespeichert.";
      button.disabled = true;
    }
    form.append(label, answer, status, button);
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!answer.value.trim()) {
        status.textContent = "Bitte eine Interviewnotiz eingeben.";
        answer.focus();
        return;
      }
      button.disabled = true;
      try {
        await paperRequest(state, "/paper/interview", {
          question_id: question.id,
          answer: answer.value,
        });
        status.textContent = "Vollständig gespeichert.";
        await refreshPaperState(state);
      } catch {
        button.disabled = false;
        status.textContent = "Speichern fehlgeschlagen; Eingabe bleibt erhalten.";
      }
    });
    step.append(form);
  }
  section.append(step);
}

function renderPaperCompleteness(state, section) {
  const step = paperStep("8 · Vollständigkeit prüfen");
  const progress = state.paper_progress;
  const list = document.createElement("ul");
  list.append(
    element(
      "li",
      "",
      `Einwilligung: ${progress.confirmed_held_consent ? "bestätigt" : "fehlt"}`,
    ),
  );
  for (const [key, label] of [
    ["task", "Aufgabenfragebögen"],
    ["block", "Bedingungsfragebögen"],
    ["end", "Demografie/Ranking"],
    ["observations", "Fehlerbeobachtungen"],
    ["interview", "Interview"],
  ]) {
    const count = progress.counts[key];
    list.append(
      element(
        "li",
        "",
        `${label}: ${count.completed}/${count.required}`,
      ),
    );
  }
  const status = element(
    "p",
    "save-status",
    progress.complete ? "Vollständig; finale Sperre ist verfügbar." : "Angaben fehlen.",
  );
  status.setAttribute("role", "status");
  const refresh = primaryButton("Neu laden und prüfen");
  refresh.addEventListener("click", async () => {
    refresh.disabled = true;
    try {
      await refreshPaperState(state);
    } catch {
      refresh.disabled = false;
      status.textContent = "Prüfung konnte nicht aktualisiert werden.";
    }
  });
  step.append(list, status, refresh);
  section.append(step);
}

function renderPaperFinalLock(state, section) {
  const step = paperStep("9 · Endgültige Sperre");
  const confirm = document.createElement("input");
  confirm.type = "checkbox";
  confirm.id = "paper-final-confirmed";
  const label = element(
    "label",
    "",
    "Ich habe die Vollständigkeit geprüft und bestätige die endgültige Sperre.",
  );
  label.htmlFor = confirm.id;
  const button = primaryButton("Endgültig sperren");
  button.disabled = true;
  confirm.addEventListener("change", () => {
    button.disabled = !confirm.checked || !state.paper_progress.complete;
  });
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await paperRequest(state, "/paper/finalize", {
        expected_revision: state.session.workflow_revision,
        confirmed: true,
      });
      await refreshPaperState(state);
    } catch {
      button.disabled = false;
    }
  });
  if (state.paper_progress.finalized) {
    confirm.checked = true;
    confirm.disabled = true;
    button.textContent = "Endgültig gesperrt";
    button.disabled = true;
  }
  step.append(confirm, label, button);
  section.append(step);
}

function renderPaperWizard(state) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "Papiertranskription");
  if (!state.paper_plan || !state.paper_progress) {
    section.append(element("p", "save-status", "Papierplan wird geladen."));
    return;
  }
  section.append(
    element(
      "p",
      "lead",
      `1 · ${state.session.participant_id} · Original: ${state.session.original_study_at} (${state.session.original_time_precision})`,
    ),
  );
  renderPaperHeldConsent(state, section);
  renderPaperTaskForms(state, section);
  renderPaperBlockForms(state, section);
  renderPaperErrorForms(state, section);
  renderPaperEndForm(state, section);
  renderPaperInterview(state, section);
  renderPaperCompleteness(state, section);
  renderPaperFinalLock(state, section);
}

function appendTimelineStatus(section, state) {
  const session = state.session;
  const assignment = session.assignment || {};
  const index = Number.isInteger(session.current_trial_index)
    ? session.current_trial_index
    : 0;
  const tasks = Array.isArray(assignment.tasks) ? assignment.tasks : [];
  const currentTask = tasks[index] || {};
  const conditions = Array.isArray(assignment.condition_order)
    ? assignment.condition_order
    : [];
  const errors = Array.isArray(assignment.error_tasks)
    ? assignment.error_tasks
    : [];
  const study = state.study || {};
  const trial = study.trial || {};
  const grid = element("dl", "status-grid");
  const rows = [
    ["Teilnehmer", session.participant_id],
    ["Quelle", `${session.mode} · ${session.source}`],
    ["Phasen", `${index} abgeschlossen · 1 aktuell · ${Math.max(tasks.length - index - 1, 0)} ausstehend`],
    ["Aktuelle Aufgabe", currentTask.id || "Noch nicht vorbereitet"],
    ["Bedingung", conditions[index] || "—"],
    ["Kritikalität", currentTask.criticality || "—"],
    ["Zugewiesener Fehler", errors.includes(currentTask.id) ? "Ja" : "Nein"],
    ["Telefon", study.phone_ready === true ? "bereit" : "nicht bereit"],
    ["Study Control", study.mode || "unbekannt"],
    ["Durchgang", trial.state || "idle"],
    ["Backup", state.backup_status || "nicht geprüft"],
  ];
  for (const [term, value] of rows) {
    grid.append(element("dt", "", term), element("dd", "", value));
  }
  section.append(grid);
}

function investigatorAction(session, action, body = {}) {
  return investigatorRequest(`/api/sessions/${session.id}/${action}`, {
    expected_revision: session.workflow_revision,
    ...body,
  });
}

function renderInvestigatorTimeline(state) {
  clearApp();
  if (!state || !state.session) {
    renderSessionSetup(state || {});
    return;
  }
  const session = state.session;
  const section = pageFrame("Versuchsleitung", "Studienablauf");
  appendTimelineStatus(section, state);
  const failedRunningTrial = session.workflow_state === "technical_hold"
    && session.resume_state === "trial_running";
  const action = failedRunningTrial
    ? ["Fehlgeschlagenen Durchgang neu vorbereiten", "retry-failed-trial", {}]
    : ({
    setup: ["Einwilligung starten", "event", { event: "start_consent" }],
    consent: state.consent && state.consent.recorded
      ? ["Einwilligung bestätigen", "confirm-consent", {}]
      : ["An Teilnehmer übergeben", "handoff", {}],
    training: ["Training abschließen", "event", { event: "training_confirmed" }],
    trial_ready: state.trial_prepared
      ? ["Aufgabenkarte freigeben", "event", { event: "release_task_card" }]
      : ["Nächsten Durchgang vorbereiten", "prepare", {}],
    task_card: ["An Teilnehmer übergeben", "handoff", {}],
    trial_running: ["Durchgang als beendet markieren", "event", { event: "trial_finished" }],
    technical_hold: ["Technischen Halt fortsetzen", "event", { event: "resume" }],
    debrief: session.debrief_handed_off
      ? [
        "Debrief bestätigen und Sitzung abschließen",
        "event",
        { event: "debrief_confirmed" },
      ]
      : ["Debrief an Teilnehmer übergeben", "handoff", {}],
  }[session.workflow_state]);
  if (action) {
    const next = primaryButton(action[0]);
    next.addEventListener("click", async () => {
      next.disabled = true;
      try {
        const path = action[1] === "event"
          ? `/api/sessions/${session.id}/events`
          : `/api/sessions/${session.id}/${action[1]}`;
        const body = action[1] === "event"
          ? { expected_revision: session.workflow_revision, ...action[2] }
          : { expected_revision: session.workflow_revision };
        const updated = await investigatorRequest(path, body);
        if (action[1] === "handoff") renderParticipantState(updated);
        else if (action[1] === "event") renderInvestigatorState(updated);
        else await loadInvestigatorState(session.id);
      } catch {
        next.disabled = false;
        await loadInvestigatorState();
      }
    });
    section.append(next);
  }
  const danger = element("details", "danger-zone");
  danger.append(element("summary", "", "Abbruch oder Zurücksetzen"));
  const abort = element("button", "button button-danger", "Sitzung bestätigt abbrechen");
  abort.type = "button";
  abort.addEventListener("click", async () => {
    await investigatorAction(session, "abort", {
      confirmed: true,
      reason: "investigator_abort",
    });
    await loadInvestigatorState();
  });
  danger.append(abort);
  section.append(danger);
}

function renderObservationForm(state) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "Beobachtung dokumentieren");
  const failedRuntimeTrial = state.study
    && state.study.trial
    && state.study.trial.state === "failed";
  if (failedRuntimeTrial) {
    const retry = element(
      "button",
      "button button-secondary",
      "Technisch abgebrochenen Durchgang erneut vorbereiten",
    );
    retry.type = "button";
    retry.addEventListener("click", async () => {
      retry.disabled = true;
      try {
        await investigatorAction(state.session, "retry-failed-trial");
        await loadInvestigatorState(state.session.id);
      } catch {
        retry.disabled = false;
        renderErrorSummary([{
          id: "observation-retry",
          message: "Der Durchgang konnte nicht erneut vorbereitet werden.",
        }]);
      }
    });
    section.append(retry);
  }
  const form = element("form", "observation-form");
  const notes = document.createElement("textarea");
  notes.id = "observation-notes";
  const submit = primaryButton("Beobachtung speichern und Fragebogen freigeben");
  submit.type = "submit";
  form.append(element("label", "", "Beobachtungsnotizen"), notes, submit);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const session = state.session;
    const task = session.assignment.tasks[session.current_trial_index];
    const assigned = session.assignment.error_tasks.includes(task.id);
    const observation = assigned ? {
      spontaneous_detection: true,
      detection_stage: "during_execution",
      evidence_type: "spoken_identification",
      moderator_prompt_given: false,
      detected_only_after_prompt: false,
      notes: notes.value,
    } : null;
    const updated = await investigatorAction(session, "observation", { observation });
    renderInvestigatorState(updated);
  });
  section.append(form);
}

function renderResultReview(state) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "Ergebnis prüfen");
  const next = primaryButton("Ergebnis geprüft");
  next.addEventListener("click", async () => {
    renderInvestigatorState(await investigatorAction(state.session, "result"));
  });
  section.append(next);
}

function renderResetConfirmation(state) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "Durchgang zurücksetzen");
  const confirm = document.createElement("input");
  confirm.type = "checkbox";
  confirm.id = "reset-confirmed";
  const label = element("label", "", "Gerätezustand wurde geprüft; jetzt zurücksetzen.");
  label.htmlFor = confirm.id;
  const reset = primaryButton("Bestätigt zurücksetzen");
  reset.disabled = true;
  confirm.addEventListener("change", () => { reset.disabled = !confirm.checked; });
  reset.addEventListener("click", async () => {
    renderInvestigatorState(await investigatorAction(state.session, "reset", {
      confirmed: true,
    }));
  });
  section.append(confirm, label, reset);
}

function renderInvestigatorInterview(state) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "Abschlussgespräch");
  const form = element("form", "interview-form");
  const guide = Array.isArray(state.interview_guide) ? state.interview_guide : [];
  guide.forEach((question, index) => {
    const card = element("section", "interview-question");
    const number = element("span", "interview-question__number", `Frage ${index + 1}`);
    const label = element("label", "interview-question__prompt", question.prompt);
    const inputId = `interview-${question.id}`;
    label.htmlFor = inputId;
    card.append(number, label);
    if (Array.isArray(question.probes) && question.probes.length) {
      const probes = element("div", "interview-probes");
      probes.append(element("span", "", "Nur bei Bedarf nachfragen:"));
      const list = document.createElement("ul");
      question.probes.forEach((probe) => list.append(element("li", "", probe)));
      probes.append(list);
      card.append(probes);
    }
    const notes = document.createElement("textarea");
    notes.id = inputId;
    notes.name = question.id;
    notes.placeholder = "Antwort in Stichpunkten festhalten …";
    card.append(notes);
    form.append(card);
  });
  const next = primaryButton("Interview speichern und abschließen");
  next.type = "submit";
  next.id = "submit-interview";
  form.append(next);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const answers = {};
    guide.forEach((question) => {
      answers[question.id] = document.getElementById(`interview-${question.id}`).value.trim();
    });
    next.disabled = true;
    try {
      const updated = await investigatorAction(state.session, "interview", {
        answers,
      });
      renderInvestigatorState(updated);
    } catch {
      next.disabled = false;
      renderErrorSummary([{
        id: next.id,
        message: "Die Interviewnotizen konnten nicht gespeichert werden.",
      }]);
    }
  });
  section.append(
    element("p", "lead", "Stelle die sechs Fragen in dieser Reihenfolge. Die kleinen Nachfragen sind nur Hilfen, falls du mehr Kontext brauchst."),
    element("p", "interview-note", "Wichtig: Zugewiesene Fehler erst im anschließenden Debriefing erklären."),
    form,
  );
}

function renderSessionReview(state) {
  clearApp();
  const section = pageFrame("Versuchsleitung", "Sitzung abgeschlossen");
  appendTimelineStatus(section, state);
  const back = element("button", "button button-secondary", "← Zur Probandenübersicht");
  back.type = "button";
  back.addEventListener("click", loadInvestigatorDashboard);
  const actions = element("div", "review-actions");
  for (const [label, path, filename] of [
    ["Research ZIP", "/api/exports/research", "study-research.zip"],
    ["Consent CSV", "/api/exports/consent", "study-consent.csv"],
    ["Course bonus CSV", "/api/exports/course_bonus", "study-course-bonus.csv"],
  ]) {
    const button = primaryButton(label);
    button.addEventListener("click", async () => {
      button.disabled = true;
      try {
        await downloadInvestigatorExport(path, filename);
      } finally {
        button.disabled = false;
      }
    });
    actions.append(button);
  }
  const backupStatus = element("p", "muted", "");
  const backup = primaryButton("Exportarchiv erstellen");
  backup.addEventListener("click", async () => {
    backup.disabled = true;
    backupStatus.textContent = "Exportarchiv wird erstellt …";
    try {
      const result = await investigatorRequest("/api/backups", {});
      backupStatus.textContent = `Exportarchiv erstellt: ${result.backup.directory}`;
    } catch {
      backupStatus.textContent = "Exportarchiv konnte nicht erstellt werden.";
    } finally {
      backup.disabled = false;
    }
  });
  actions.append(backup);
  section.append(back, actions, backupStatus);
  renderCourseBonusForm(section);
  renderCourseBonusManagement(section);
}

async function renderCourseBonusManagement(section) {
  const step = paperStep("Gespeicherte Kursbonus-Nachweise");
  const content = element("div", "paper-form");
  content.append(element("p", "muted", "Nachweise werden nach Bestätigung dauerhaft gelöscht."));
  step.append(content);
  section.append(step);
  try {
    const result = await request("/api/course-bonus");
    if (!result.records.length) {
      content.append(element("p", "save-status", "Keine aktiven Nachweise."));
      return;
    }
    for (const record of result.records) {
      const row = element(
        "div",
        "review-actions",
        `${record.full_name} · ${record.matriculation_number} · ${record.consented_at}`,
      );
      const remove = primaryButton("Dauerhaft löschen");
      remove.type = "button";
      remove.addEventListener("click", async () => {
        remove.disabled = true;
        try {
          await investigatorRequest(`/api/course-bonus/${record.id}/delete`, {});
          row.replaceChildren(element("p", "save-status", "Nachweis gelöscht."));
        } catch {
          remove.disabled = false;
        }
      });
      row.append(remove);
      content.append(row);
    }
  } catch {
    content.append(element("p", "save-status", "Nachweise konnten nicht geladen werden."));
  }
}

function renderInvestigatorState(state) {
  const session = state.session;
  const workflow = session && session.workflow_state;
  if (!session) renderSessionSetup(state);
  else if (session.mode === "paper_transcription") {
    renderPaperWizard(state);
  }
  else if (workflow === "investigator_observation") renderObservationForm(state);
  else if (workflow === "investigator_result_review") renderResultReview(state);
  else if (workflow === "trial_reset") renderResetConfirmation(state);
  else if (workflow === "interview") renderInvestigatorInterview(state);
  else if (["completed", "aborted"].includes(workflow)) {
    renderSessionReview(state);
  } else renderInvestigatorTimeline(state);
}

async function loadInvestigatorState(sessionId = null) {
  try {
    const query = sessionId === null
      ? ""
      : `?session_id=${encodeURIComponent(sessionId)}`;
    const state = await request(`/api/investigator/state${query}`);
    if (
      state.session &&
      state.session.workflow_state === "consent" &&
      state.consent &&
      state.consent.recorded === true &&
      state.consent.confirmed !== true
    ) {
      await investigatorRequest(
        `/api/sessions/${state.session.id}/confirm-consent`,
        { expected_revision: state.session.workflow_revision },
      );
      await loadInvestigatorState(state.session.id);
      return;
    }
    renderInvestigatorState(state);
  } catch (error) {
    if (error.status === 401) renderInvestigatorLogin({ pin_configured: true }, sessionId);
    else renderOffline(sessionId);
  }
}

function renderLogin(pinConfigured = true, investigatorSessionId = null) {
  clearApp();
  const section = pageFrame(
    "Geschützter Bereich",
    pinConfigured ? "Moderatorbereich entsperren" : "Moderator-PIN einrichten",
  );
  const form = element("form", "login-form");
  const label = element("label", "", "Moderator-PIN");
  label.htmlFor = "moderator-pin";
  const input = document.createElement("input");
  input.id = "moderator-pin";
  input.name = "pin";
  input.type = "password";
  input.inputMode = "numeric";
  input.autocomplete = pinConfigured ? "current-password" : "new-password";
  input.required = true;
  const button = primaryButton(pinConfigured ? "Entsperren" : "PIN speichern");
  button.type = "submit";
  form.append(label, input, button);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const loggedIn = await request(pinConfigured ? "/api/login" : "/api/setup-pin", {
        method: "POST",
        body: { pin: input.value },
      });
      investigatorCsrf = typeof loggedIn.csrf_token === "string"
        ? loggedIn.csrf_token
        : "";
      if (Number.isInteger(investigatorSessionId)) {
        await loadInvestigatorState(investigatorSessionId);
      } else {
        await loadInvestigatorState();
      }
    } catch {
      renderErrorSummary([
        { id: input.id, message: "Anmeldung nicht möglich. Bitte PIN prüfen." },
      ]);
    }
  });
  section.append(form);
  input.focus();
}

function renderOffline(investigatorSessionId = null) {
  clearApp();
  const section = pageFrame("", "Das Studienportal ist gerade nicht erreichbar.");
  section.append(
    element(
      "p",
      "lead",
      "Bitte prüfe die Verbindung oder wende dich an die Versuchsleitung.",
    ),
  );
  const retry = primaryButton("Erneut versuchen");
  retry.addEventListener("click", () => loadBootstrap(investigatorSessionId), { once: true });
  section.append(retry);
}

function renderParticipantAccessExpired(state) {
  clearApp();
  const section = pageFrame("Sitzung wiederherstellen", "Eine laufende Studiensitzung wurde gefunden.");
  section.append(
    element(
      "p",
      "lead",
      "Diese Browseransicht hat die Verbindung zur laufenden Sitzung verloren. Du kannst direkt weitermachen oder sicher zur Studienübersicht zurückkehren.",
    ),
    element("p", "status-line", "Deine bisherigen Studiendaten bleiben gespeichert."),
  );
  if (state && state.pin_configured) {
    const login = primaryButton("Zur Versuchsleiter-Anmeldung");
    login.addEventListener("click", () => renderInvestigatorLogin(state), { once: true });
    section.append(login);
    return;
  }
  const actions = element("div", "dashboard-actions");
  const recover = primaryButton("Laufende Sitzung fortsetzen");
  recover.id = "participant-recover-access";
  const release = element("button", "button button-secondary", "Zur Studienübersicht");
  release.type = "button";
  release.id = "participant-release-access";

  recover.addEventListener("click", async () => {
    recover.disabled = true;
    release.disabled = true;
    try {
      await investigatorRequest("/api/participant/recover-access", {});
      await loadParticipantState();
    } catch (error) {
      recover.disabled = false;
      release.disabled = false;
      if (error.status === 401 && state && state.pin_configured) {
        renderInvestigatorLogin(state);
        return;
      }
      renderErrorSummary([{ id: recover.id, message: "Die Sitzung konnte noch nicht fortgesetzt werden. Bitte versuche es erneut." }]);
    }
  });

  release.addEventListener("click", async () => {
    recover.disabled = true;
    release.disabled = true;
    try {
      await investigatorRequest("/api/participant/release-access", {});
      await loadBootstrap();
    } catch (error) {
      recover.disabled = false;
      release.disabled = false;
      if (error.status === 401 && state && state.pin_configured) {
        renderInvestigatorLogin(state);
        return;
      }
      renderErrorSummary([{ id: release.id, message: "Die Studienübersicht konnte noch nicht geöffnet werden. Bitte versuche es erneut." }]);
    }
  });
  actions.append(recover, release);
  section.append(actions);
}

function renderTechnicalHold(state) {
  clearApp();
  const section = pageFrame("Technische Pause", "Die Studie ist sicher angehalten.");
  const unsafeTrialRetry = state.session.resume_state === "trial_running";
  section.append(element(
    "p",
    "lead",
    unsafeTrialRetry
      ? "Die Telefonaufgabe wurde technisch angehalten. Deine bisherigen Daten und dein Teilnehmerdatensatz bleiben gespeichert. Du kannst genau diese Aufgabe neu vorbereiten und danach fortfahren."
      : "Deine bisherigen Antworten sind gespeichert. Prüfe kurz das Studien-Smartphone und versuche die Vorbereitung danach erneut.",
  ));
  if (unsafeTrialRetry) {
    const retry = primaryButton("Aufgabe erneut vorbereiten");
    retry.id = "participant-retry-failed-trial";
    retry.addEventListener("click", async () => {
      retry.disabled = true;
      try {
        renderParticipantState(await request("/api/participant/continue", {
          method: "POST",
          body: { expected_revision: state.session.workflow_revision },
        }));
      } catch (error) {
        if (error.status === 401) {
          await loadBootstrap(state.session.id);
          return;
        }
        if (error.status === 409) {
          await loadParticipantState(state.session.id);
          return;
        }
        retry.disabled = false;
        renderErrorSummary([{ id: retry.id, message: "Die Aufgabe konnte noch nicht neu vorbereitet werden. Bitte prüfe das Studien-Smartphone und versuche es erneut." }]);
      }
    });
    section.append(retry);
    return;
  }
  const retry = primaryButton("Erneut vorbereiten");
  retry.id = "participant-resume-study";
  retry.addEventListener("click", async () => {
    retry.disabled = true;
    try {
      renderParticipantState(await request("/api/participant/continue", {
        method: "POST",
        body: { expected_revision: state.session.workflow_revision },
      }));
    } catch {
      retry.disabled = false;
      renderErrorSummary([{ id: retry.id, message: "Die Vorbereitung ist noch nicht möglich. Bitte prüfe das Studien-Smartphone oder wende dich an die Versuchsleitung." }]);
    }
  });
  section.append(retry);
}

function renderParticipantState(state, previewIndex = null) {
  const workflow = state.session && state.session.workflow_state;
  if (workflow === "consent") renderConsent(state, previewIndex);
  else if (workflow === "training") renderTraining(state, previewIndex);
  else if (workflow === "task_card") renderTaskCard(state, previewIndex);
  else if (workflow === "trial_running") renderTrialRunning(state, previewIndex);
  else if (
    ["task_questionnaire", "block_questionnaire", "demographics", "preference_ranking"]
      .includes(workflow)
  ) renderInstrument(state, previewIndex);
  else if (workflow === "interview") renderInterview(state, previewIndex);
  else if (workflow === "debrief" || workflow === "completed") renderDebrief(state, previewIndex);
  else if (workflow === "technical_hold") renderTechnicalHold(state);
  else continueStudyFlow(state);
  if (previewIndex !== null) appendPreviewControls(previewIndex);
}

async function loadParticipantState(investigatorSessionId = null) {
  try {
    const state = await request("/api/participant/state");
    renderParticipantState(state);
  } catch (error) {
    if (error.status === 401) {
      await loadBootstrap(investigatorSessionId);
      return;
    }
    renderOffline(investigatorSessionId);
  }
}

async function loadBootstrap(investigatorSessionId = null) {
  try {
    const state = await request("/api/bootstrap");
    investigatorCsrf = typeof state.csrf_token === "string"
      ? state.csrf_token
      : "";
    if (state.role === "participant") {
      await loadParticipantState(investigatorSessionId);
    } else if (state.role === "participant_expired") {
      renderParticipantAccessExpired(state);
    } else if (state.role === "investigator") {
      if (Number.isInteger(investigatorSessionId)) {
        await loadInvestigatorState(investigatorSessionId);
      } else {
        await loadInvestigatorDashboard();
      }
    } else {
      renderInvestigatorLogin(state, investigatorSessionId);
    }
  } catch {
    renderOffline(investigatorSessionId);
  }
}

loadBootstrap();
