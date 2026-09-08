import './style.css';
import { playChimeAlmostReady, playChimeCurrent, playChimeJoin, triggerHaptic } from './sound.js';
import * as api from './api.js';

// --- Application State ---
const state = {
  route: window.location.hash === '#barber' ? 'barber' : 'student',
  studentTicket: getStoredTicket(),
  barberToken: localStorage.getItem('queuecut_barber_jwt') || null,
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
  lastStatus: null,
  showCancelModal: false,
  showSettingsModal: false,
  loading: false,
};

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
  if (token) {
    localStorage.setItem('queuecut_barber_jwt', token);
  } else {
    localStorage.removeItem('queuecut_barber_jwt');
  }
}

// --- Toast Notifications ---
function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container') || createToastContainer();
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  
  const icon = type === 'success' ? '✓' : type === 'error' ? '✕' : 'ℹ';
  toast.innerHTML = `<span style="font-weight: bold">${icon}</span> <span>${message}</span>`;
  
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
  api.connectSse((eventType, data) => {
    if (eventType === 'INIT' || eventType === 'QUEUE_UPDATED') {
      state.queueStatus = data;
      if (state.studentTicket) {
        refreshMyStatus();
      }
      if (state.barberToken && state.route === 'barber') {
        refreshBarberEntries();
      }
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
  state.route = window.location.hash === '#barber' ? 'barber' : 'student';
  if (state.route === 'barber' && state.barberToken) {
    refreshBarberEntries();
  }
  render();
});

// --- RENDER FUNCTION ---
function render() {
  const app = document.getElementById('app');
  app.innerHTML = `
    <!-- Navbar Header -->
    <header class="navbar">
      <a href="#" class="brand">
        <div class="brand-icon">✂️</div>
        <div class="brand-name">Queue<span>Cut</span></div>
      </a>
      <div class="nav-links">
        <button id="toggle-sound-btn" class="nav-btn" title="Toggle audio alerts">
          ${state.soundEnabled ? '🔔 Sound ON' : '🔕 Muted'}
        </button>
        <button id="switch-view-btn" class="nav-btn ${state.route === 'barber' ? 'active' : ''}">
          ${state.route === 'student' ? ' Barber Portal' : '👤 Student View'}
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

    <!-- Footer -->
    <footer class="footer">
      QueueCut &copy; ${new Date().getFullYear()} FAST University Barber Shop. Designed for speed.
    </footer>
  `;

  bindEvents();
}

// --- STUDENT VIEW RENDERER ---
function renderStudentView() {
  const { queueOpen, currentTicket, currentStudentName, totalWaiting, estimatedWaitMinutes, avgHaircutMinutes } = state.queueStatus;
  const myStatus = state.myStatus;

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
          <div style="font-weight: 600; font-size: 1.1rem;">${myStatus.studentName} (${myStatus.studentId})</div>
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
          ${currentTicket ? `Currently serving <strong>#${currentTicket} (${currentStudentName || ''})</strong>` : 'Barber is calling next student...'}
        </div>

        <!-- Actions -->
        ${!isCurrent ? `
          <button id="open-cancel-btn" class="btn btn-danger">
            ❌ Cancel My Ticket
          </button>
        ` : ''}
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
          ${queueOpen ? 'QUEUE OPEN' : 'CLOSED'}
        </span>
      </div>

      ${currentTicket ? `
        <!-- Now Serving Highlight -->
        <div class="now-serving-hero">
          <div style="font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-secondary); font-weight: 700;">NOW SERVING IN CHAIR</div>
          <div class="ticket-number-display">#${currentTicket}</div>
          <div style="font-weight: 600; color: var(--text-primary);">${currentStudentName || 'Student'}</div>
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
            <input id="input-roll" type="text" class="form-input" placeholder="e.g. 21K-3890" required minlength="3" maxlength="50" style="text-transform: uppercase" />
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
          <h2 style="font-family: var(--font-family-display); font-size: 1.5rem; font-weight: 800;">Barber Portal</h2>
          <p style="font-size: 0.85rem; color: var(--text-secondary);">Sign in to manage today's queue</p>
        </div>

        <form id="barber-login-form">
          <div class="form-group">
            <label class="form-label">Username</label>
            <input id="login-username" type="text" class="form-input" value="arslan" required />
          </div>

          <div class="form-group">
            <label class="form-label">Password</label>
            <input id="login-password" type="password" class="form-input" placeholder="Enter password" required />
          </div>

          <button type="submit" class="btn btn-primary" ${state.loading ? 'disabled' : ''}>
            ${state.loading ? 'Signing In...' : '🔑 Sign In to Dashboard'}
          </button>
        </form>
      </div>
    `;
  }

  // Case 2: Logged in Dashboard
  const { queueOpen } = state.queueStatus;
  const currentInChair = state.barberEntries.find(e => e.status === 'CURRENT');
  const waitingList = state.barberEntries.filter(e => e.status === 'WAITING' || e.status === 'ALMOST_READY');

  return `
    <!-- Top Action Toolbar -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; flex-wrap: wrap; gap: 12px;">
      <div>
        <h1 style="font-family: var(--font-family-display); font-size: 1.6rem; font-weight: 800;">Barber Dashboard</h1>
        <p style="font-size: 0.85rem; color: var(--text-secondary);">Muhammad Arslan | FAST Campus Shop</p>
      </div>

      <div style="display: flex; gap: 10px;">
        <button id="toggle-session-btn" class="btn ${queueOpen ? 'btn-danger' : 'btn-emerald'}" style="width: auto; padding: 10px 16px;">
          ${queueOpen ? '🔒 Close Queue' : '🟢 Open Queue'}
        </button>
        <button id="open-settings-btn" class="btn btn-secondary" style="width: auto; padding: 10px 14px;" title="Haircut settings">
          ⚙️ ${state.queueStatus.avgHaircutMinutes || 20}m
        </button>
        <button id="barber-logout-btn" class="btn btn-secondary" style="width: auto; padding: 10px 14px;">
          Logout
        </button>
      </div>
    </div>

    <!-- Hero Currently in Chair -->
    <div class="card card-glow">
      <div style="font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-secondary); font-weight: 700; margin-bottom: 8px;">
        💈 CURRENTLY IN THE CHAIR
      </div>

      ${currentInChair ? `
        <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; margin-bottom: 16px;">
          <div>
            <div style="font-family: var(--font-family-display); font-size: 2.8rem; font-weight: 800; color: var(--accent-gold); line-height: 1;">
              #${currentInChair.queueNumber}
            </div>
            <div style="font-size: 1.2rem; font-weight: 700;">${currentInChair.studentName}</div>
            <div style="font-size: 0.85rem; color: var(--text-secondary);">Roll ID: ${currentInChair.studentId}</div>
          </div>

          <div style="display: flex; gap: 10px;">
            <button class="btn btn-emerald finish-entry-btn" data-id="${currentInChair.id}" style="width: auto; padding: 12px 20px;">
              ✔️ Complete Haircut
            </button>
            <button class="btn btn-danger skip-entry-btn" data-id="${currentInChair.id}" style="width: auto; padding: 12px 16px;">
              ⏭️ Skip / No-Show
            </button>
          </div>
        </div>
      ` : `
        <div style="padding: 24px 0; text-align: center; color: var(--text-muted);">
          No student currently in the chair. Click <strong>Call Next Student</strong> below.
        </div>
      `}
    </div>

    <!-- Massive Call Next CTA -->
    <div style="margin-bottom: 24px;">
      <button id="call-next-btn" class="btn btn-primary btn-giant">
        📢 CALL NEXT STUDENT ${waitingList.length > 0 ? `(#${waitingList[0].queueNumber} - ${waitingList[0].studentName})` : ''}
      </button>
    </div>

    <!-- Waiting Queue Roster -->
    <div class="card">
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
        <h3 style="font-family: var(--font-family-display); font-size: 1.1rem; font-weight: 700;">
          📋 Waiting Line (${waitingList.length} students)
        </h3>
        <span style="font-size: 0.8rem; color: var(--text-muted);">Avg time: ${state.queueStatus.avgHaircutMinutes} min/person</span>
      </div>

      ${waitingList.length > 0 ? `
        <div class="roster-list">
          ${waitingList.map(entry => `
            <div class="roster-item">
              <div class="roster-number">#${entry.queueNumber}</div>
              <div class="roster-info">
                <div class="roster-name">${entry.studentName}</div>
                <div class="roster-id">${entry.studentId}</div>
              </div>
              <span class="status-pill ${entry.status.toLowerCase().replace('_', '-')}">
                ${entry.status.replace('_', ' ')}
              </span>
              <div class="roster-actions">
                <button class="btn btn-danger btn-xs skip-entry-btn" data-id="${entry.id}">Skip</button>
              </div>
            </div>
          `).join('')}
        </div>
      ` : `
        <div style="text-align: center; color: var(--text-muted); padding: 20px;">
          Queue line is empty!
        </div>
      `}
    </div>
  `;
}

// --- MODAL RENDERERS ---
function renderCancelModal() {
  return `
    <div class="modal-overlay active">
      <div class="modal-card">
        <h3 style="font-family: var(--font-family-display); font-size: 1.25rem; font-weight: 800; margin-bottom: 12px;">
          Cancel Queue Ticket?
        </h3>
        <p style="font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 20px;">
          Are you sure you want to forfeit your place in line? You will have to join again if you change your mind.
        </p>
        <div style="display: flex; gap: 10px;">
          <button id="confirm-cancel-btn" class="btn btn-danger">Yes, Cancel Ticket</button>
          <button id="close-cancel-btn" class="btn btn-secondary">Keep My Place</button>
        </div>
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

  const switchBtn = document.getElementById('switch-view-btn');
  if (switchBtn) {
    switchBtn.onclick = () => {
      const nextRoute = state.route === 'student' ? 'barber' : 'student';
      window.location.hash = nextRoute === 'barber' ? '#barber' : '';
    };
  }

  // Student Join Form
  const joinForm = document.getElementById('join-queue-form');
  if (joinForm) {
    joinForm.onsubmit = async (e) => {
      e.preventDefault();
      const name = document.getElementById('input-name').value.trim();
      const roll = document.getElementById('input-roll').value.trim();

      if (!name || !roll) return;

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
        showToast('Ticket cancelled.', 'info');
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
        await refreshBarberEntries();
      } catch (err) {
        showToast(err.message, 'error');
      }
    };
  }

  const callNextBtn = document.getElementById('call-next-btn');
  if (callNextBtn) {
    callNextBtn.onclick = async () => {
      try {
        const res = await api.callNext(state.barberToken);
        if (res.currentEntry) {
          showToast(`Called Ticket #${res.currentEntry.queueNumber} (${res.currentEntry.studentName})`, 'success');
        } else {
          showToast('No more waiting students in line.', 'info');
        }
        await refreshBarberEntries();
      } catch (err) {
        showToast(err.message, 'error');
      }
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
      const id = btn.dataset.id;
      try {
        await api.completeEntry(id, state.barberToken);
        showToast('Haircut marked complete!', 'success');
        await refreshBarberEntries();
      } catch (err) {
        showToast(err.message, 'error');
      }
    };
  });

  document.querySelectorAll('.skip-entry-btn').forEach(btn => {
    btn.onclick = async () => {
      const id = btn.dataset.id;
      try {
        await api.skipEntry(id, state.barberToken);
        showToast('Student marked skipped.', 'info');
        await refreshBarberEntries();
      } catch (err) {
        showToast(err.message, 'error');
      }
    };
  });

  // Settings Modal
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
