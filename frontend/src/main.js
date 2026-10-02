import './style.css';
import { playChimeAlmostReady, playChimeCurrent, playChimeJoin, triggerHaptic } from './sound.js';
import * as api from './api.js';

// --- Application State ---
const state = {
  route: window.location.hash === '#barber' ? 'barber' : 'student',
  studentTicket: getStoredTicket(),
  barberToken: null, // Always require login on portal access
  queueStatus: {
    queueOpen: false,
    currentTicket: null,
    currentStudentName: null,
    totalWaiting: 0,
    estimatedWaitMinutes: 0,
    avgHaircutMinutes: 20,
  },
  myStatus: null,
  barberEntries: [],
  soundEnabled: localStorage.getItem('queuecut_sound') !== 'false',
  theme: getStoredTheme(),
  lastStatus: null,
  showCancelModal: false,
  showSettingsModal: false,
  showPasswordModal: false,
  loading: false,
};

// --- Theme (Light / Night) ---
function getStoredTheme() {
  try {
    return localStorage.getItem('queuecut_theme') === 'dark' ? 'dark' : 'light';
  } catch (e) {
    return 'light';
  }
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', theme === 'dark' ? '#000000' : '#eef5ff');
}

applyTheme(state.theme);

// --- Storage Helpers ---
function getStoredTicket() {
  try {
    const raw = localStorage.getItem('queuecut_ticket');
    if (!raw) return null;
    const ticket = JSON.parse(raw);
    const today = new Date().toISOString().split('T')[0];
    // Automatically expire tickets from previous days
    if (ticket.sessionDate && ticket.sessionDate !== today) {
      localStorage.removeItem('queuecut_ticket');
      return null;
    }
    return ticket;
  } catch (e) {
    return null;
  }
}

function setStoredTicket(ticket) {
  state.studentTicket = ticket;
  if (ticket) {
    localStorage.setItem('queuecut_ticket', JSON.stringify(ticket));
  } else {
    localStorage.removeItem('queuecut_ticket');
  }
}

function setBarberToken(token) {
  state.barberToken = token;
  // Ensure no persistent token remains so switching views always requires logging in again
  localStorage.removeItem('queuecut_barber_jwt');
}

// FAST roll number: batch 21–29 + campus letter + dash + 4 digits (same rule as the backend)
const ROLL_NUMBER_PATTERN = /^2[1-9][FMKLIP]-\d{4}$/;

// Barber action in flight — blocks double taps on Call Next / Done / Skip
let barberBusy = false;

// --- HTML Escaping (student names / IDs are user input) ---
function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// --- Toast Notifications ---
function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container') || createToastContainer();
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  
  const icon = type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ';
  toast.innerHTML = `<span style="font-weight: bold">${icon}</span> <span>${esc(message)}</span>`;
  
  container.appendChild(toast);
  setTimeout(() => {
    toast.remove();
  }, 4000);
}

function createToastContainer() {
  const el = document.createElement('div');
  el.id = 'toast-container';
  document.body.appendChild(el);
  return el;
}

// --- Real-time SSE Connection ---
function initSse() {
  api.connectSse(async (eventType, data) => {
    if (eventType === 'INIT' || eventType === 'QUEUE_UPDATED') {
      state.queueStatus = data;
      // Wait for the fresh ticket / roster before re-rendering, otherwise the screen shows stale data
      await Promise.all([
        state.studentTicket ? refreshMyStatus() : null,
        state.barberToken && state.route === 'barber' ? refreshBarberEntries() : null,
      ]);
      render();
    } else if (eventType === 'SESSION_STATUS_CHANGED') {
      showToast(data.message, data.isQueueOpen ? 'success' : 'info');
      state.queueStatus.queueOpen = data.isQueueOpen;
      render();
    }
  });
}

// --- Student Status Refresh ---
async function refreshMyStatus() {
  if (!state.studentTicket) return;
  try {
    const status = await api.fetchMyStatus(
      state.studentTicket.entryId,
      state.studentTicket.studentToken
    );
    state.myStatus = status;

    // Check for status changes and play sound/haptic alerts
    if (state.lastStatus !== status.status) {
      if (status.status === 'ALMOST_READY' && state.soundEnabled) {
        playChimeAlmostReady();
        triggerHaptic([200, 100, 200, 100, 200]);
        showToast("⚡ YOU'RE NEXT! Head to the barber shop now.", 'info');
      } else if (status.status === 'CURRENT' && state.soundEnabled) {
        playChimeCurrent();
        triggerHaptic([300, 100, 300]);
        showToast("🎉 YOUR TURN! Take your seat in the chair.", 'success');
      }
      state.lastStatus = status.status;
    }

    // Clear local storage if ticket reached terminal state
    if (['COMPLETED', 'SKIPPED', 'CANCELLED'].includes(status.status)) {
      if (status.status === 'COMPLETED') {
        showToast('✂️ Haircut complete. Thanks for visiting!', 'success');
      } else if (status.status === 'SKIPPED') {
        showToast('You were marked as no-show and removed from the queue.', 'error');
      }
      setStoredTicket(null);
      state.myStatus = null;
      state.lastStatus = null;
    }
  } catch (e) {
    if (e.status === 404 || e.status === 403) {
      setStoredTicket(null);
      state.myStatus = null;
    }
  }
}

// --- Barber Roster Refresh ---
async function refreshBarberEntries() {
  if (!state.barberToken) return;
  try {
    const entries = await api.fetchEntries('ACTIVE', state.barberToken);
    state.barberEntries = entries;
  } catch (e) {
    if (e.status === 401 || e.status === 403) {
      setBarberToken(null);
      showToast('Barber session expired. Please log in again.', 'error');
    }
  }
}

// --- Initial Data Load ---
async function init() {
  localStorage.removeItem('queuecut_barber_jwt');
  try {
    const status = await api.fetchQueueStatus();
    state.queueStatus = status;
  } catch (e) {
    console.warn('Backend server not connected yet, showing offline state.');
  }

  if (state.studentTicket) {
    await refreshMyStatus();
  }

  if (state.barberToken && state.route === 'barber') {
    await refreshBarberEntries();
  }

  initSse();
  render();
}

// --- Router / Hash Listener ---
window.addEventListener('hashchange', () => {
  const previousRoute = state.route;
  state.route = window.location.hash === '#barber' ? 'barber' : 'student';
  if (state.route === 'barber' && previousRoute !== 'barber') {
    // Always require fresh login when opening barber / admin portal
    setBarberToken(null);
  }
  if (state.route === 'barber' && state.barberToken) {
    refreshBarberEntries();
  }
  render();
});

// --- RENDER FUNCTION ---
function render() {
  const app = document.getElementById('app');
  // Live updates re-render the whole page — keep what the user is typing (and the cursor)
  const typed = {};
  app.querySelectorAll('input[id]').forEach(el => { typed[el.id] = el.value; });
  const focused = document.activeElement?.id;
  const caret = focused ? [document.activeElement.selectionStart, document.activeElement.selectionEnd] : null;

  app.innerHTML = `
    <!-- Navbar Header -->
    <header class="navbar">
      <a href="#" class="brand">
        <div class="brand-icon">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="6" cy="6" r="3"></circle>
            <circle cx="6" cy="18" r="3"></circle>
            <line x1="20" y1="4" x2="8.12" y2="15.88"></line>
            <line x1="14.47" y1="14.48" x2="20" y2="20"></line>
            <line x1="8.12" y1="8.12" x2="12" y2="12"></line>
          </svg>
        </div>
        <div class="brand-name">QueueCut</div>
      </a>
      <div class="nav-links">
        <button id="toggle-theme-btn" class="nav-btn" title="Switch light / night mode">
          ${state.theme === 'dark' ? '☀️ Light' : '🌙 Night'}
        </button>
        <button id="toggle-sound-btn" class="nav-btn" title="Toggle audio alerts">
          ${state.soundEnabled ? '🔔 Sound ON' : '🔕 Muted'}
        </button>
        <button id="switch-view-btn" class="nav-btn ${state.route === 'barber' ? 'active' : ''}">
          ${state.route === 'student' ? '✂️ Admin Portal' : '👤 Student View'}
        </button>
      </div>
    </header>

    <!-- Main Container -->
    <main class="container ${state.route === 'barber' ? 'container-wide' : ''}">
      ${state.route === 'student' ? renderStudentView() : renderBarberView()}
    </main>

    <!-- Modals -->
    ${state.showCancelModal ? renderCancelModal() : ''}
    ${state.showSettingsModal ? renderSettingsModal() : ''}
    ${state.showPasswordModal ? renderPasswordModal() : ''}

    <!-- Footer -->
    <footer class="footer">
      QueueCut &copy; ${new Date().getFullYear()} FAST University Barber Shop. Designed for speed.
    </footer>
  `;

  for (const [id, value] of Object.entries(typed)) {
    const el = document.getElementById(id);
    if (el && value && !el.value) el.value = value;
  }
  if (focused) {
    const el = document.getElementById(focused);
    if (el) {
      el.focus();
      try { el.setSelectionRange(caret[0], caret[1]); } catch (e) { /* not a text input */ }
    }
  }

  bindEvents();
}

// --- STUDENT VIEW RENDERER ---
function renderStudentView() {
  const { queueOpen, currentTicket, currentStudentName, totalWaiting, estimatedWaitMinutes, avgHaircutMinutes } = state.queueStatus;
  const myStatus = state.myStatus;

  // Case 0: Ticket stored but status not loaded yet — never show the join form again
  if (state.studentTicket && !myStatus) {
    return `
      <div class="card" style="text-align: center;">
        <div style="font-size: 0.85rem; color: var(--text-secondary); font-weight: 600; margin-bottom: 8px;">YOUR QUEUE TICKET</div>
        <div class="ticket-number-display">#${esc(state.studentTicket.queueNumber)}</div>
        <div style="color: var(--text-secondary); font-size: 0.9rem;">Loading your live status...</div>
      </div>
    `;
  }

  // Case 1: Student has an active ticket
  if (state.studentTicket && myStatus) {
    const isAlmost = myStatus.status === 'ALMOST_READY';
    const isCurrent = myStatus.status === 'CURRENT';

    return `
      <!-- Active Ticket Card -->
      <div class="card ${isAlmost || isCurrent ? 'card-glow' : ''}">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
          <span style="font-size: 0.85rem; color: var(--text-secondary); font-weight: 600;">YOUR QUEUE TICKET</span>
          <span class="status-pill ${myStatus.status.toLowerCase().replace('_', '-')}">
            <span class="pulse-dot"></span>
            ${myStatus.status.replace('_', ' ')}
          </span>
        </div>

        <!-- Ticket Number -->
        <div class="now-serving-hero">
          <div style="font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-secondary);">Ticket Number</div>
          <div class="ticket-number-display">#${myStatus.queueNumber}</div>
          <div style="font-weight: 600; font-size: 1.1rem;">${esc(myStatus.studentName)} (${esc(myStatus.studentId)})</div>
        </div>

        <!-- Dynamic Alert Banners -->
        ${isAlmost ? `
          <div class="alert-banner warning">
            <div class="alert-icon">⚡</div>
            <div>
              <div class="alert-title">YOU'RE NEXT IN LINE!</div>
              <div class="alert-desc">Only 1 person ahead of you. Please head to the barber shop now.</div>
            </div>
          </div>
        ` : ''}

        ${isCurrent ? `
          <div class="alert-banner success">
            <div class="alert-icon">💈</div>
            <div>
              <div class="alert-title">YOUR TURN! TAKE YOUR SEAT</div>
              <div class="alert-desc">You are currently in the chair with Muhammad Arslan. Enjoy your haircut!</div>
            </div>
          </div>
        ` : ''}

        <!-- Stat Boxes -->
        <div class="stat-grid">
          <div class="stat-box">
            <div class="stat-lbl">PEOPLE AHEAD</div>
            <div class="stat-val">${myStatus.peopleAhead}</div>
          </div>
          <div class="stat-box">
            <div class="stat-lbl">EST. WAIT TIME</div>
            <div class="stat-val">~${myStatus.estimatedWaitMinutes} <span style="font-size: 1rem">MIN</span></div>
          </div>
        </div>

        <!-- Progress Timeline -->
        <div style="background: var(--bg-surface-elevated); padding: 14px; border-radius: var(--radius-md); margin-bottom: 20px; font-size: 0.85rem; color: var(--text-secondary); text-align: center;">
          ${currentTicket ? `Currently serving <strong>#${currentTicket} (${esc(currentStudentName)})</strong>` : 'Barber is calling next student...'}
        </div>

        <!-- Actions -->
        <div style="display: flex; flex-direction: column; gap: 10px; margin-top: 10px;">
          ${!isCurrent ? `
            <button id="open-cancel-btn" class="btn btn-danger">
              ⏭️ Skip &amp; Leave Queue
            </button>
            <div style="font-size: 0.78rem; color: var(--text-muted); text-align: center;">
              Can't make it? Skip to leave the line. If you join again, you'll go to the end of the queue.
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }

  // Case 2: No active ticket (Public Join View)
  return `
    <!-- Public Status Card -->
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
        <div>
          <h1 style="font-family: var(--font-family-display); font-size: 1.5rem; font-weight: 800;">FAST Barber Queue</h1>
          <p style="font-size: 0.85rem; color: var(--text-secondary);">Muhammad Arslan's Shop</p>
        </div>
        <span class="status-pill ${queueOpen ? 'open' : 'closed'}">
          <span class="pulse-dot"></span>
          ${queueOpen ? 'OPEN' : 'CLOSED'}
        </span>
      </div>

      ${currentTicket ? `
        <!-- Now Serving Highlight -->
        <div class="now-serving-hero">
          <div style="font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-secondary); font-weight: 700;">NOW SERVING IN CHAIR</div>
          <div class="ticket-number-display">#${currentTicket}</div>
          <div style="font-weight: 600; color: var(--text-primary);">${esc(currentStudentName || 'Student')}</div>
        </div>
      ` : ''}

      <!-- Stat Grid -->
      <div class="stat-grid">
        <div class="stat-box">
          <div class="stat-lbl">TOTAL WAITING</div>
          <div class="stat-val">${totalWaiting}</div>
        </div>
        <div class="stat-box">
          <div class="stat-lbl">EST. WAIT TIME</div>
          <div class="stat-val">~${estimatedWaitMinutes} <span style="font-size: 1rem">MIN</span></div>
        </div>
      </div>
    </div>

    <!-- Join Queue Form Card -->
    <div class="card">
      <h2 style="font-family: var(--font-family-display); font-size: 1.2rem; font-weight: 700; margin-bottom: 16px;">
        🏷️ Join the Virtual Queue
      </h2>

      ${queueOpen ? `
        <form id="join-queue-form">
          <div class="form-group">
            <label class="form-label" for="input-name">Full Name</label>
            <input id="input-name" type="text" class="form-input" placeholder="e.g. Usman Tariq" required minlength="2" maxlength="100" />
          </div>

          <div class="form-group">
            <label class="form-label" for="input-roll">Student Roll ID</label>
            <input id="input-roll" type="text" class="form-input" placeholder="e.g. 24F-3089" required maxlength="8" autocapitalize="characters" autocomplete="off" style="text-transform: uppercase" />
            <span style="font-size: 0.75rem; color: var(--text-muted); margin-top: 4px; display: block;">
              Format: batch (21–29) + campus letter (F, M, K, L, I, P) + dash + 4 digits
            </span>
          </div>

          <button type="submit" class="btn btn-primary" ${state.loading ? 'disabled' : ''}>
            ${state.loading ? 'Joining Queue...' : '✂️ GET MY QUEUE NUMBER'}
          </button>
        </form>
      ` : `
        <div class="alert-banner warning" style="margin-bottom: 0;">
          <div class="alert-icon">🔒</div>
          <div>
            <div class="alert-title">The Queue is Currently Closed</div>
            <div class="alert-desc">Muhammad Arslan has not opened the queue yet. Please check back shortly or scan QR at the shop.</div>
          </div>
        </div>
      `}
    </div>
  `;
}

// --- BARBER VIEW RENDERER ---
function renderBarberView() {
  // Case 1: Not logged in
  if (!state.barberToken) {
    return `
      <div class="card" style="max-width: 400px; margin: 40px auto;">
        <div style="text-align: center; margin-bottom: 20px;">
          <div class="brand-icon" style="margin: 0 auto 12px; width: 48px; height: 48px; font-size: 26px;">✂️</div>
          <h2 style="font-family: var(--font-family-display); font-size: 1.5rem; font-weight: 800;">Barber / Admin Portal</h2>
          <p style="font-size: 0.85rem; color: var(--text-secondary);">Restricted access &mdash; Barber credentials required</p>
        </div>

        <form id="barber-login-form">
          <div class="form-group">
            <label class="form-label" for="login-username">Username</label>
            <input id="login-username" type="text" class="form-input" placeholder="Enter username" autocomplete="username" autocapitalize="none" required />
          </div>

          <div class="form-group">
            <label class="form-label" for="login-password">Password</label>
            <input id="login-password" type="password" class="form-input" placeholder="Enter password" autocomplete="current-password" required />
          </div>

          <button type="submit" class="btn btn-primary" ${state.loading ? 'disabled' : ''}>
            ${state.loading ? 'Signing In...' : '🔑 Sign In to Admin Dashboard'}
          </button>
        </form>
      </div>
    `;
  }

  // Case 2: Logged in Dashboard
  const { queueOpen } = state.queueStatus;
  const currentInChair = state.barberEntries.find(e => e.status === 'CURRENT');
  const waitingList = state.barberEntries.filter(e => e.status === 'WAITING' || e.status === 'ALMOST_READY');
  const nextUp = waitingList[0];
  const avg = state.queueStatus.avgHaircutMinutes || 20;

  let callNextLabel;
  if (currentInChair && nextUp) {
    callNextLabel = `✔️ Finish #${esc(currentInChair.queueNumber)} &amp; Call Next &rarr; #${esc(nextUp.queueNumber)} ${esc(nextUp.studentName)}`;
  } else if (currentInChair) {
    callNextLabel = `✔️ Finish #${esc(currentInChair.queueNumber)} (no one waiting)`;
  } else if (nextUp) {
    callNextLabel = `📢 Call #${esc(nextUp.queueNumber)} ${esc(nextUp.studentName)} to the Chair`;
  } else {
    callNextLabel = 'No students waiting';
  }

  return `
    <!-- Top Action Toolbar -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px;">
      <div>
        <h1 style="font-family: var(--font-family-display); font-size: 1.6rem; font-weight: 800;">Barber Dashboard</h1>
        <p style="font-size: 0.85rem; color: var(--text-secondary);">Muhammad Arslan | FAST Campus Shop</p>
      </div>

      <div style="display: flex; gap: 10px; flex-wrap: wrap;">
        <button id="toggle-session-btn" class="btn ${queueOpen ? 'btn-danger' : 'btn-emerald'}" style="width: auto; padding: 10px 16px;">
          ${queueOpen ? '🔒 Close Queue' : '🟢 Open Queue'}
        </button>
        <button id="open-password-btn" class="btn btn-secondary" style="width: auto; padding: 10px 14px;" title="Change admin password">
          🔑 Password
        </button>
        <button id="open-settings-btn" class="btn btn-secondary" style="width: auto; padding: 10px 14px;" title="Haircut settings">
          ⚙️ ${state.queueStatus.avgHaircutMinutes || 20}m
        </button>
        <button id="barber-logout-btn" class="btn btn-secondary" style="width: auto; padding: 10px 14px;">
          Logout
        </button>
      </div>
    </div>

    <!-- 1. Who is in the chair right now -->
    <div class="section-label">💈 In the chair now</div>
    <div class="card chair-card ${currentInChair ? 'occupied' : ''}">
      ${currentInChair ? `
        <div class="chair-row">
          <div>
            <div class="chair-number">#${esc(currentInChair.queueNumber)}</div>
            <div class="chair-name">${esc(currentInChair.studentName)}</div>
            <div class="chair-meta">
              Roll ID: ${esc(currentInChair.studentId)}
              ${currentInChair.calledAt ? ` &middot; in chair since ${formatTime(currentInChair.calledAt)}` : ''}
            </div>
          </div>
          <div class="chair-actions">
            <button class="btn btn-emerald finish-entry-btn" data-id="${esc(currentInChair.id)}">
              ✔️ Haircut Done
            </button>
            <button class="btn btn-danger skip-entry-btn" data-id="${esc(currentInChair.id)}">
              ⏭️ No-Show
            </button>
          </div>
        </div>
      ` : `
        <div class="chair-empty">
          🪑 Chair is empty${nextUp ? ` &mdash; press the blue button to call <strong>#${esc(nextUp.queueNumber)} ${esc(nextUp.studentName)}</strong>` : ''}
        </div>
      `}
    </div>

    <!-- Call Next CTA — label says exactly what will happen -->
    <div style="margin-bottom: 28px;">
      <button id="call-next-btn" class="btn btn-primary btn-giant" ${!currentInChair && !nextUp ? 'disabled' : ''}>
        ${callNextLabel}
      </button>
    </div>

    <!-- 2. Waiting line (not in the chair yet) -->
    <div class="section-label">📋 Waiting line &mdash; ${waitingList.length} ${waitingList.length === 1 ? 'student' : 'students'}</div>
    <div class="card">
      ${waitingList.length > 0 ? `
        <div class="roster-list">
          ${waitingList.map((entry, i) => `
            <div class="roster-item ${i === 0 ? 'next' : ''}">
              <div class="roster-pos">${i + 1}</div>
              <div class="roster-number">#${esc(entry.queueNumber)}</div>
              <div class="roster-info">
                <div class="roster-name">${esc(entry.studentName)}</div>
                <div class="roster-id">${esc(entry.studentId)} &middot; ~${i * avg + (currentInChair ? avg : 0)} min</div>
              </div>
              ${i === 0 ? '<span class="badge-next">NEXT</span>' : ''}
              <div class="roster-actions">
                <button class="btn btn-danger btn-xs skip-entry-btn" data-id="${esc(entry.id)}">Skip</button>
              </div>
            </div>
          `).join('')}
        </div>
      ` : `
        <div style="text-align: center; color: var(--text-muted); padding: 20px;">
          No one is waiting.
        </div>
      `}
    </div>
  `;
}

function formatTime(iso) {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

// --- MODAL RENDERERS ---
function renderCancelModal() {
  return `
    <div class="modal-overlay active">
      <div class="modal-card">
        <h3 style="font-family: var(--font-family-display); font-size: 1.25rem; font-weight: 800; margin-bottom: 12px;">
          Skip &amp; leave the queue?
        </h3>
        <p style="font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 20px;">
          You will lose your place (#${esc(state.studentTicket?.queueNumber)}). If you join again later, you will get a new number at the <strong>end of the line</strong>.
        </p>
        <div style="display: flex; gap: 10px;">
          <button id="confirm-cancel-btn" class="btn btn-danger">Yes, Skip</button>
          <button id="close-cancel-btn" class="btn btn-secondary">Keep My Place</button>
        </div>
      </div>
    </div>
  `;
}

function renderPasswordModal() {
  return `
    <div class="modal-overlay active">
      <div class="modal-card">
        <h3 style="font-family: var(--font-family-display); font-size: 1.25rem; font-weight: 800; margin-bottom: 6px;">
          🔑 Change Admin Password
        </h3>
        <p style="font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 16px;">
          Use a new password that you haven't used anywhere else (at least 8 characters).
        </p>
        <form id="password-form">
          <input type="text" name="username" value="arslan" autocomplete="username" hidden />
          <div class="form-group">
            <label class="form-label" for="input-current-password">Current Password</label>
            <input id="input-current-password" type="password" class="form-input" autocomplete="current-password" required />
          </div>
          <div class="form-group">
            <label class="form-label" for="input-new-password">New Password</label>
            <input id="input-new-password" type="password" class="form-input" autocomplete="new-password" minlength="8" maxlength="72" required />
          </div>
          <div class="form-group">
            <label class="form-label" for="input-confirm-password">Confirm New Password</label>
            <input id="input-confirm-password" type="password" class="form-input" autocomplete="new-password" minlength="8" maxlength="72" required />
          </div>
          <div style="display: flex; gap: 10px; margin-top: 20px;">
            <button type="submit" class="btn btn-primary" ${state.loading ? 'disabled' : ''}>
              ${state.loading ? 'Saving...' : 'Change Password'}
            </button>
            <button type="button" id="close-password-btn" class="btn btn-secondary">Cancel</button>
          </div>
        </form>
      </div>
    </div>
  `;
}

function renderSettingsModal() {
  return `
    <div class="modal-overlay active">
      <div class="modal-card">
        <h3 style="font-family: var(--font-family-display); font-size: 1.25rem; font-weight: 800; margin-bottom: 16px;">
          ⚙️ Queue Settings
        </h3>
        <form id="settings-form">
          <div class="form-group">
            <label class="form-label">Average Haircut Duration (Minutes)</label>
            <input id="input-avg-time" type="number" min="5" max="120" class="form-input" value="${state.queueStatus.avgHaircutMinutes || 20}" required />
            <span style="font-size: 0.75rem; color: var(--text-muted); margin-top: 4px; display: block;">
              Used to calculate estimated wait times on student screens.
            </span>
          </div>

          <div style="display: flex; gap: 10px; margin-top: 20px;">
            <button type="submit" class="btn btn-primary">Save Settings</button>
            <button type="button" id="close-settings-btn" class="btn btn-secondary">Close</button>
          </div>
        </form>
      </div>
    </div>
  `;
}

// --- EVENT BINDING ---
function bindEvents() {
  // Navigation
  const soundBtn = document.getElementById('toggle-sound-btn');
  if (soundBtn) {
    soundBtn.onclick = () => {
      state.soundEnabled = !state.soundEnabled;
      localStorage.setItem('queuecut_sound', state.soundEnabled);
      showToast(state.soundEnabled ? 'Audio alerts enabled 🔔' : 'Audio alerts muted 🔕', 'info');
      render();
    };
  }

  const themeBtn = document.getElementById('toggle-theme-btn');
  if (themeBtn) {
    themeBtn.onclick = () => {
      state.theme = state.theme === 'dark' ? 'light' : 'dark';
      try {
        localStorage.setItem('queuecut_theme', state.theme);
      } catch (e) {
        // Storage unavailable — theme still applies for this visit
      }
      applyTheme(state.theme);
      render();
    };
  }

  const switchBtn = document.getElementById('switch-view-btn');
  if (switchBtn) {
    switchBtn.onclick = () => {
      const nextRoute = state.route === 'student' ? 'barber' : 'student';
      if (nextRoute === 'barber') {
        // Always require login when clicking into admin / barber portal
        setBarberToken(null);
      }
      window.location.hash = nextRoute === 'barber' ? '#barber' : '';
    };
  }

  // Student Join Form
  const joinForm = document.getElementById('join-queue-form');
  if (joinForm) {
    joinForm.onsubmit = async (e) => {
      e.preventDefault();
      const name = document.getElementById('input-name').value.trim();
      const roll = document.getElementById('input-roll').value.trim().toUpperCase();

      if (!name || !roll || state.studentTicket) return;
      if (!ROLL_NUMBER_PATTERN.test(roll)) {
        showToast('Invalid roll number. Use the format 24F-3089 (batch 21–29, letter F/M/K/L/I/P, dash, 4 digits).', 'error');
        return;
      }

      state.loading = true;
      render();

      try {
        const response = await api.joinQueue(name, roll);
        setStoredTicket({
          entryId: response.entryId,
          queueNumber: response.queueNumber,
          studentToken: response.studentToken,
          studentName: response.studentName,
          studentId: response.studentId,
          sessionDate: new Date().toISOString().split('T')[0],
        });

        if (state.soundEnabled) playChimeJoin();
        triggerHaptic([150]);
        showToast(`Successfully joined! Your Ticket is #${response.queueNumber}`, 'success');
        await refreshMyStatus();
      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        state.loading = false;
        render();
      }
    };
  }

  // Cancel Ticket Modals
  const openCancelBtn = document.getElementById('open-cancel-btn');
  if (openCancelBtn) {
    openCancelBtn.onclick = () => {
      state.showCancelModal = true;
      render();
    };
  }

  const closeCancelBtn = document.getElementById('close-cancel-btn');
  if (closeCancelBtn) {
    closeCancelBtn.onclick = () => {
      state.showCancelModal = false;
      render();
    };
  }

  const confirmCancelBtn = document.getElementById('confirm-cancel-btn');
  if (confirmCancelBtn) {
    confirmCancelBtn.onclick = async () => {
      if (!state.studentTicket) return;
      try {
        await api.cancelMyEntry(state.studentTicket.entryId, state.studentTicket.studentToken);
        setStoredTicket(null);
        state.myStatus = null;
        state.showCancelModal = false;
        showToast("You skipped and left the queue. Join again anytime — you'll be added at the end.", 'info');
      } catch (err) {
        showToast(err.message, 'error');
      }
      render();
    };
  }

  // Barber Login Form
  const loginForm = document.getElementById('barber-login-form');
  if (loginForm) {
    loginForm.onsubmit = async (e) => {
      e.preventDefault();
      const user = document.getElementById('login-username').value.trim();
      const pass = document.getElementById('login-password').value;

      state.loading = true;
      render();

      try {
        const res = await api.loginBarber(user, pass);
        setBarberToken(res.token);
        showToast(`Welcome back, ${res.fullName}!`, 'success');
        await refreshBarberEntries();
      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        state.loading = false;
        render();
      }
    };
  }

  // Barber Actions
  const toggleSessionBtn = document.getElementById('toggle-session-btn');
  if (toggleSessionBtn) {
    toggleSessionBtn.onclick = async () => {
      try {
        if (state.queueStatus.queueOpen) {
          await api.closeSession(state.barberToken);
          showToast('Queue closed for today.', 'info');
        } else {
          await api.openSession(state.barberToken);
          showToast('Queue is now OPEN!', 'success');
        }
        state.queueStatus = await api.fetchQueueStatus();
        await refreshBarberEntries();
      } catch (err) {
        showToast(err.message, 'error');
      }
      render();
    };
  }

  const callNextBtn = document.getElementById('call-next-btn');
  if (callNextBtn) {
    callNextBtn.onclick = async () => {
      if (barberBusy) return; // ignore extra taps while the request is in flight
      barberBusy = true;
      callNextBtn.disabled = true;
      const inChair = state.barberEntries.find(e => e.status === 'CURRENT');
      try {
        const res = await api.callNext(state.barberToken, inChair?.id);
        if (res.currentEntry) {
          showToast(`Called Ticket #${res.currentEntry.queueNumber} (${res.currentEntry.studentName})`, 'success');
        } else {
          showToast('No more waiting students in line.', 'info');
        }
      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        await refreshBarberEntries();
        barberBusy = false;
      }
      render();
    };
  }

  const logoutBtn = document.getElementById('barber-logout-btn');
  if (logoutBtn) {
    logoutBtn.onclick = () => {
      setBarberToken(null);
      showToast('Logged out.', 'info');
      render();
    };
  }

  document.querySelectorAll('.finish-entry-btn').forEach(btn => {
    btn.onclick = async () => {
      if (barberBusy) return;
      barberBusy = true;
      btn.disabled = true;
      const id = btn.dataset.id;
      try {
        await api.completeEntry(id, state.barberToken);
        showToast('Haircut marked complete!', 'success');
      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        await refreshBarberEntries();
        barberBusy = false;
      }
      render();
    };
  });

  document.querySelectorAll('.skip-entry-btn').forEach(btn => {
    btn.onclick = async () => {
      if (barberBusy) return;
      barberBusy = true;
      btn.disabled = true;
      const id = btn.dataset.id;
      try {
        await api.skipEntry(id, state.barberToken);
        showToast('Student marked skipped.', 'info');
      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        await refreshBarberEntries();
        barberBusy = false;
      }
      render();
    };
  });

  // Settings Modal
  // Change Password Modal
  const openPasswordBtn = document.getElementById('open-password-btn');
  if (openPasswordBtn) {
    openPasswordBtn.onclick = () => {
      state.showPasswordModal = true;
      render();
    };
  }

  const closePasswordBtn = document.getElementById('close-password-btn');
  if (closePasswordBtn) {
    closePasswordBtn.onclick = () => {
      state.showPasswordModal = false;
      render();
    };
  }

  const passwordForm = document.getElementById('password-form');
  if (passwordForm) {
    passwordForm.onsubmit = async (e) => {
      e.preventDefault();
      const current = document.getElementById('input-current-password').value;
      const next = document.getElementById('input-new-password').value;
      const confirm = document.getElementById('input-confirm-password').value;

      if (next !== confirm) {
        showToast('New password and confirmation do not match.', 'error');
        return;
      }

      state.loading = true;
      render();
      try {
        await api.changePassword(current, next, state.barberToken);
        state.showPasswordModal = false;
        showToast('Password changed. Use the new password next time you log in.', 'success');
      } catch (err) {
        showToast(err.message, 'error');
      } finally {
        state.loading = false;
        render();
      }
    };
  }

  const openSettingsBtn = document.getElementById('open-settings-btn');
  if (openSettingsBtn) {
    openSettingsBtn.onclick = () => {
      state.showSettingsModal = true;
      render();
    };
  }

  const closeSettingsBtn = document.getElementById('close-settings-btn');
  if (closeSettingsBtn) {
    closeSettingsBtn.onclick = () => {
      state.showSettingsModal = false;
      render();
    };
  }

  const settingsForm = document.getElementById('settings-form');
  if (settingsForm) {
    settingsForm.onsubmit = async (e) => {
      e.preventDefault();
      const val = document.getElementById('input-avg-time').value;
      try {
        const res = await api.updateSettings(val, state.barberToken);
        state.queueStatus.avgHaircutMinutes = res.avgHaircutMin;
        state.showSettingsModal = false;
        showToast(`Average haircut time set to ${res.avgHaircutMin} min.`, 'success');
      } catch (err) {
        showToast(err.message, 'error');
      }
      render();
    };
  }
}

// Start application
init();
