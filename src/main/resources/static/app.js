'use strict';

// Pipeline order, matching ApplicationStatus on the server.
const STATUSES = [
  'TO_APPLY', 'APPLIED',
  'ONLINE_ASSESSMENT', 'ONLINE_ASSESSMENT_COMPLETED',
  'HIREVUE', 'HIREVUE_COMPLETED',
  'TELEPHONE_INTERVIEW', 'TELEPHONE_INTERVIEW_COMPLETED',
  'VIDEO_INTERVIEW', 'VIDEO_INTERVIEW_COMPLETED',
  'ASSESSMENT_CENTRE', 'ASSESSMENT_CENTRE_COMPLETED',
  'OFFER', 'REJECTED', 'WITHDRAWN', 'EXPIRED',
];
const FINISHED = new Set(['REJECTED', 'WITHDRAWN', 'EXPIRED']);
const SPECIAL_LABELS = { HIREVUE: 'HireVue', HIREVUE_COMPLETED: 'HireVue completed' };

// sessionStorage rather than localStorage: the token is gone when the tab closes,
// which limits how long a leaked token stays useful.
const TOKEN_KEY = 'tracker.token';
const EMAIL_KEY = 'tracker.email';

const state = { applications: [], companies: [], view: 'applications', editingId: null };

const $ = (selector) => document.querySelector(selector);

// Builds elements without innerHTML, so user-entered text can never be run as markup.
function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === 'class') node.className = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key in node) node[key] = value;
    else node.setAttribute(key, value);
  }
  for (const child of children.flat()) {
    if (child !== null && child !== undefined) node.append(child);
  }
  return node;
}

function label(status) {
  if (SPECIAL_LABELS[status]) return SPECIAL_LABELS[status];
  const words = status.toLowerCase().replaceAll('_', ' ');
  return words[0].toUpperCase() + words.slice(1);
}

function statusGroup(status) {
  if (status === 'TO_APPLY') return 'todo';
  if (status === 'OFFER') return 'offer';
  if (status === 'REJECTED') return 'rejected';
  if (FINISHED.has(status)) return 'closed';
  return 'active';
}

// ---- Dates ----

function parseDate(iso) {
  const [year, month, day] = iso.split('-').map(Number);
  return Date.UTC(year, month - 1, day);
}

// Fixed three-letter months: browsers disagree on "Sep" versus "Sept" for en-GB.
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function formatDate(iso) {
  if (!iso) return '';
  const [year, month, day] = iso.split('-').map(Number);
  return `${day} ${MONTHS[month - 1]} ${year}`;
}

function formatDateTime(iso) {
  const moment = new Date(iso);
  const time = moment.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  return `${moment.getDate()} ${MONTHS[moment.getMonth()]} ${moment.getFullYear()}, ${time}`;
}

function formatDuration(days) {
  if (days < 1) return 'under a day';
  return days === 1 ? '1 day' : `${days} days`;
}

function daysUntil(iso) {
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((parseDate(iso) - today) / 86_400_000);
}

function deadlineClass(application) {
  if (!application.deadline || FINISHED.has(application.status)) return '';
  const days = daysUntil(application.deadline);
  if (days < 0) return 'deadline overdue';
  if (days <= 3) return 'deadline soon';
  return '';
}

// ---- API ----

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function api(method, path, body) {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401 && token) {
    logout('Your session has expired. Please log in again.');
    throw new ApiError(401, 'Session expired');
  }
  if (!response.ok) {
    let message = `Something went wrong (${response.status}).`;
    try {
      const problem = await response.json();
      if (problem.detail) message = problem.detail;
    } catch {
      // No JSON body; keep the generic message.
    }
    throw new ApiError(response.status, message);
  }
  return response.status === 204 ? null : response.json();
}

// ---- Messages ----

let noticeTimer;

function notify(message, kind = 'error') {
  const notice = $('#notice');
  clearTimeout(noticeTimer);
  notice.textContent = message;
  notice.className = `notice ${kind === 'info' ? 'info' : ''}`;
  notice.hidden = !message;
  if (message && kind === 'info') noticeTimer = setTimeout(() => (notice.hidden = true), 3000);
}

function reportError(error) {
  if (error.status !== 401) notify(error.message);
}

// ---- Sign in and out ----

function showAuth() {
  $('#topbar').hidden = true;
  $('#applications-view').hidden = true;
  $('#stats-view').hidden = true;
  $('#auth-view').hidden = false;
}

async function showApp() {
  $('#auth-view').hidden = true;
  $('#topbar').hidden = false;
  $('#user-email').textContent = sessionStorage.getItem(EMAIL_KEY) ?? '';
  await switchView(state.view);
}

function logout(message) {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(EMAIL_KEY);
  state.applications = [];
  state.companies = [];
  showAuth();
  notify(message ?? '');
}

async function onAuthSubmit(event) {
  event.preventDefault();
  const form = event.target;
  const mode = event.submitter?.dataset.mode ?? 'login';
  const credentials = { email: form.email.value.trim(), password: form.password.value };

  try {
    if (mode === 'register') await api('POST', '/api/auth/register', credentials);
    const { accessToken } = await api('POST', '/api/auth/login', credentials);
    sessionStorage.setItem(TOKEN_KEY, accessToken);
    sessionStorage.setItem(EMAIL_KEY, credentials.email.toLowerCase());
    form.reset();
    notify('');
    await showApp();
  } catch (error) {
    notify(error.message);
  }
}

// ---- Views ----

async function switchView(view) {
  state.view = view;
  document.querySelectorAll('.tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.view === view));
  $('#applications-view').hidden = view !== 'applications';
  $('#stats-view').hidden = view !== 'stats';
  try {
    if (view === 'applications') await loadApplications();
    else await loadStats();
  } catch (error) {
    reportError(error);
  }
}

// ---- Applications ----

async function loadApplications() {
  const [applications, companies] = await Promise.all([
    api('GET', '/api/applications'),
    api('GET', '/api/companies'),
  ]);
  state.applications = applications;
  state.companies = companies;
  $('#company-names').replaceChildren(...companies.map((company) => el('option', { value: company.name })));
  renderApplications();
}

// Open applications before finished ones, then soonest deadline first, then newest.
function compareApplications(a, b) {
  const aFinished = FINISHED.has(a.status);
  const bFinished = FINISHED.has(b.status);
  if (aFinished !== bFinished) return aFinished ? 1 : -1;
  if (a.deadline && b.deadline) return a.deadline.localeCompare(b.deadline);
  if (a.deadline) return -1;
  if (b.deadline) return 1;
  return b.id - a.id;
}

function renderApplications() {
  const query = $('#search').value.trim().toLowerCase();
  const filter = $('#status-filter').value;
  const rows = state.applications
    .filter((application) => !filter || application.status === filter)
    .filter((application) => !query
      || application.companyName.toLowerCase().includes(query)
      || application.roleTitle.toLowerCase().includes(query))
    .sort(compareApplications);

  $('#applications-body').replaceChildren(...rows.map(applicationRow));

  const empty = $('#applications-empty');
  empty.hidden = rows.length > 0;
  empty.textContent = state.applications.length
    ? 'No applications match your search.'
    : 'No applications yet. Add your first one to get started.';
}

function applicationRow(application) {
  const status = el('select', {
    class: `status s-${statusGroup(application.status)}`,
    'aria-label': `Status of ${application.roleTitle} at ${application.companyName}`,
    onchange: (event) => changeStatus(application, event.target),
  }, STATUSES.map((value) => el('option', { value, textContent: label(value), selected: value === application.status })));

  return el('tr', {},
    el('td', { textContent: application.companyName }),
    el('td', { textContent: application.roleTitle }),
    el('td', { class: 'muted', textContent: application.businessStream ?? '' }),
    el('td', {}, status),
    el('td', { class: 'nowrap', textContent: formatDate(application.appliedDate) }),
    el('td', { class: `nowrap ${deadlineClass(application)}`, textContent: formatDate(application.deadline) }),
    el('td', { class: 'actions' },
      el('button', { class: 'link', textContent: 'History', onclick: () => showHistory(application) }),
      el('button', { class: 'link', textContent: 'Edit', onclick: () => openApplicationDialog(application) }),
      el('button', { class: 'link danger', textContent: 'Delete', onclick: () => deleteApplication(application) })));
}

async function changeStatus(application, select) {
  const previous = application.status;
  try {
    const updated = await api('POST', `/api/applications/${application.id}/status`, { status: select.value });
    Object.assign(application, updated);
    renderApplications();
  } catch (error) {
    select.value = previous;
    reportError(error);
  }
}

async function deleteApplication(application) {
  if (!confirm(`Delete ${application.roleTitle} at ${application.companyName}? Its history goes too.`)) return;
  try {
    await api('DELETE', `/api/applications/${application.id}`);
    await loadApplications();
    notify('Application deleted.', 'info');
  } catch (error) {
    reportError(error);
  }
}

// ---- Add and edit ----

function openApplicationDialog(application) {
  const form = $('#application-form');
  form.reset();
  state.editingId = application?.id ?? null;
  $('#application-dialog-title').textContent = application ? 'Edit application' : 'Add application';
  $('#initial-status-label').hidden = Boolean(application);
  $('#application-form-error').hidden = true;

  if (application) {
    form.company.value = application.companyName;
    form.roleTitle.value = application.roleTitle;
    form.businessStream.value = application.businessStream ?? '';
    form.appliedDate.value = application.appliedDate ?? '';
    form.deadline.value = application.deadline ?? '';
  }
  $('#application-dialog').showModal();
  form.company.focus();
}

// Reuses a company you already have (matching names case-insensitively, as the server does),
// otherwise creates it.
async function companyIdFor(rawName) {
  const name = rawName.trim();
  const existing = state.companies.find((company) => company.name.toLowerCase() === name.toLowerCase());
  if (existing) return existing.id;
  const created = await api('POST', '/api/companies', { name });
  state.companies.push(created);
  return created.id;
}

async function onApplicationSubmit(event) {
  event.preventDefault();
  const form = event.target;
  const error = $('#application-form-error');
  error.hidden = true;

  const body = {
    roleTitle: form.roleTitle.value.trim(),
    businessStream: form.businessStream.value.trim() || null,
    appliedDate: form.appliedDate.value || null,
    deadline: form.deadline.value || null,
  };

  try {
    body.companyId = await companyIdFor(form.company.value);
    if (state.editingId) {
      await api('PUT', `/api/applications/${state.editingId}`, body);
    } else {
      const created = await api('POST', '/api/applications', body);
      const status = form.status.value;
      if (status !== 'TO_APPLY') await api('POST', `/api/applications/${created.id}/status`, { status });
    }
    $('#application-dialog').close();
    await loadApplications();
  } catch (failure) {
    if (failure.status === 401) return;
    error.textContent = failure.message;
    error.hidden = false;
  }
}

// ---- History ----

async function showHistory(application) {
  const list = $('#history-list');
  $('#history-title').textContent = `${application.roleTitle} at ${application.companyName}`;
  list.replaceChildren(el('li', { class: 'muted', textContent: 'Loading' }));
  $('#history-dialog').showModal();

  try {
    const changes = await api('GET', `/api/applications/${application.id}/status-changes`);
    list.replaceChildren(...changes.slice().reverse().map((change) => el('li', {},
      el('span', { class: 'history-status', textContent: label(change.toStatus) }),
      el('span', { class: 'muted small', textContent: formatDateTime(change.changedAt) }),
      change.note ? el('div', { class: 'history-note', textContent: change.note }) : null)));
  } catch (error) {
    if (error.status !== 401) list.replaceChildren(el('li', { class: 'form-error', textContent: error.message }));
  }
}

// ---- Stats ----

async function loadStats() {
  const stats = await api('GET', '/api/stats');
  renderFunnel(stats.funnel);
  renderTransitions(stats.timeBetweenStatuses);
  renderPerCompany(stats.applicationsPerCompany);
}

function headerRow(...cells) {
  return el('thead', {}, el('tr', {}, cells.map(([text, className]) => el('th', { class: className ?? '', textContent: text }))));
}

function emptyRow(columns, text) {
  return el('tr', {}, el('td', { class: 'muted', colSpan: columns, textContent: text }));
}

function renderFunnel(steps) {
  $('#funnel').replaceChildren(
    headerRow(['Stage'], ['Applications', 'num'], ['Share of applied']),
    el('tbody', {}, steps.map((step) => el('tr', {},
      el('td', { textContent: label(step.stage) }),
      el('td', { class: 'num', textContent: step.applications }),
      el('td', {},
        el('span', { class: 'bar' }, el('span', { style: `width: ${step.percentOfApplied}%` })),
        el('span', { class: 'pct muted', textContent: `${step.percentOfApplied}%` }))))));
}

function renderTransitions(transitions) {
  const rows = transitions.length
    ? transitions.map((transition) => el('tr', {},
      el('td', { textContent: `${label(transition.fromStatus)} to ${label(transition.toStatus)}` }),
      el('td', { class: 'num', textContent: formatDuration(transition.averageDays) }),
      el('td', { class: 'num muted', textContent: transition.transitions })))
    : [emptyRow(3, 'Nothing yet. This fills in as your applications move between statuses.')];
  $('#transitions').replaceChildren(
    headerRow(['From and to'], ['Average', 'num'], ['Times', 'num']),
    el('tbody', {}, rows));
}

function renderPerCompany(counts) {
  const rows = counts.length
    ? counts.map((count) => el('tr', {},
      el('td', { textContent: count.companyName }),
      el('td', { class: 'num', textContent: count.applications })))
    : [emptyRow(2, 'No applications yet.')];
  $('#per-company').replaceChildren(headerRow(['Company'], ['Applications', 'num']), el('tbody', {}, rows));
}

// ---- Start up ----

function init() {
  const statusOptions = () => STATUSES.map((value) => el('option', { value, textContent: label(value) }));
  $('#status-filter').append(...statusOptions());
  $('#application-form').status.append(...statusOptions());

  $('#auth-form').addEventListener('submit', onAuthSubmit);
  $('#application-form').addEventListener('submit', onApplicationSubmit);
  $('#logout').addEventListener('click', () => logout());
  $('#add-application').addEventListener('click', () => openApplicationDialog(null));
  $('#search').addEventListener('input', renderApplications);
  $('#status-filter').addEventListener('change', renderApplications);
  document.querySelectorAll('.tab').forEach((tab) => tab.addEventListener('click', () => switchView(tab.dataset.view)));
  document.querySelectorAll('[data-close]').forEach((button) =>
    button.addEventListener('click', () => button.closest('dialog').close()));

  if (sessionStorage.getItem(TOKEN_KEY)) showApp();
  else showAuth();
}

init();
