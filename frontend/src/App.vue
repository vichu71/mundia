<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
// Animations via Web Animations API — zero deps
import {
  Activity,
  Banknote,
  CalendarDays,
  Check,
  ChevronRight,
  CircleAlert,
  Crown,
  Home,
  Medal,
  Pencil,
  RefreshCw,
  Settings,
  Sparkles,
  Star,
  Trophy,
  TrendingUp,
  Users,
  Wifi,
  WifiOff,
  Zap,
} from '@lucide/vue'

// ──────────────────────────────────────────
//  TYPES
// ──────────────────────────────────────────
type TabId = 'home' | 'matches' | 'ranking' | 'prizes' | 'admin'

type Pool = { id: number; name: string; code: string; status: string; statusType: string; userRole: string; members: number; paid: number; pot: number }
type Match = { id: number; home: string; away: string; homeFl: string; awayFl: string; pred: string; real: string; points: number | null; status: string; statusType: string; note: string; kickoff: string | null; source: string | null }
type RankingRow = { pos: number; name: string; avatar: string; points: number; exact: number; winners: number; prize: number; delta: string; alive: boolean }
type InitialRankingRow = { pos: number; name: string; points: number; exact: number; winners: number; bonus: string }
type PrizeRow = { label: string; amount: number; state: string; stateType: string; contenders: number; pct: number }
type BracketMatch = { home: string; homeFl: string; away: string; awayFl: string; pred: string; real: string; winner: string; done: boolean }
type BracketRound = { name: string; matches: BracketMatch[] }
type Recommendation = { type: 'danger' | 'success' | 'info'; text: string }
type PendingPayment = { id: number; name: string }
type ApiSyncProvider = 'api-football' | 'wc26'
type ApiSyncJob = { key: string; label: string; lastRun: string; status: string; provider: ApiSyncProvider }
type ApiSyncState = {
  provider: string
  league: number
  season: number
  mode: string
  cadence: string
  dailyLimit: string
  lastSync: string
  liveMatches: number
  status: 'ok' | 'error' | 'syncing'
  activeSource: 'WC26_IR' | 'API_FOOTBALL'
  jobs: ApiSyncJob[]
}

// ──────────────────────────────────────────
//  AUTH
// ──────────────────────────────────────────
const JWT_KEY = 'mundia_jwt'
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''

type CurrentUser = { name: string; email: string; avatarUrl: string | null }
type UserPool = { id: number; name: string; code: string; role: string }

const authToken   = ref<string | null>(localStorage.getItem(JWT_KEY))
const currentUser = ref<CurrentUser | null>(null)
const userPools   = ref<UserPool[]>([])
const appReady    = ref(false)   // true once /me loaded

const activePoolId = computed(() => userPools.value.find(p => p.id === activePool.value)?.id ?? userPools.value[0]?.id ?? null)
const userRole     = computed(() => userPools.value.find(p => p.id === activePoolId.value)?.role ?? null)

function fetchWithAuth(url: string, init: RequestInit = {}): Promise<Response> {
  const headers: Record<string, string> = { ...(init.headers as Record<string, string> ?? {}) }
  if (authToken.value) headers['Authorization'] = `Bearer ${authToken.value}`
  return fetch(url, { ...init, headers })
}

async function loadMe() {
  const res = await fetchWithAuth(`${API_BASE_URL}/me`)
  if (!res.ok) { signOut(); return }
  const data = await res.json()
  currentUser.value = { name: data.name, email: data.email, avatarUrl: data.avatarUrl }
  userPools.value = data.pools ?? []
  if (userPools.value.length > 0 && !activePool.value) {
    activePool.value = userPools.value[0].id
  }
  appReady.value = true
}

async function handleGoogleCredential(response: { credential: string }) {
  const res = await fetch(`${API_BASE_URL}/auth/google`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ credential: response.credential }),
  })
  if (!res.ok) { console.error('Auth failed', await res.text()); return }
  const data = await res.json()
  authToken.value = data.token
  localStorage.setItem(JWT_KEY, data.token)
  await loadMe()
  if (userPools.value.length > 0) {
    await loadRemoteState()
    animateHero()
    setTimeout(() => { animateCards('.pool-pill', 80); animateCards('.home-grid > *', 70) }, 100)
  }
}

function initGoogleAuth() {
  const g = (window as any).google
  if (!g?.accounts?.id) { setTimeout(initGoogleAuth, 400); return }
  g.accounts.id.initialize({ client_id: GOOGLE_CLIENT_ID, callback: handleGoogleCredential })
  const btn = document.getElementById('g_signin_btn')
  if (btn) {
    g.accounts.id.renderButton(btn, {
      theme: 'filled_black', size: 'large', text: 'continue_with', shape: 'pill', locale: 'es',
    })
  }
}

async function signOut() {
  authToken.value = null
  currentUser.value = null
  userPools.value = []
  appReady.value = false
  localStorage.removeItem(JWT_KEY)
  setTimeout(initGoogleAuth, 200)
}

// ─── Email auth ───────────────────────────────────────────────────────────────
type AuthMode = 'google' | 'login' | 'register'
const authMode     = ref<AuthMode>('google')
const emailForm    = ref({
  email: '', password: '', displayName: '', inviteCode: '',
  loading: false, error: '',
})

async function emailLogin() {
  emailForm.value.loading = true
  emailForm.value.error = ''
  try {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: emailForm.value.email, password: emailForm.value.password }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      emailForm.value.error = err.message ?? 'Email o contraseña incorrectos'
      return
    }
    const data = await res.json()
    authToken.value = data.token
    localStorage.setItem(JWT_KEY, data.token)
    await loadMe()
    if (userPools.value.length > 0) { await loadRemoteState(); animateHero() }
  } finally {
    emailForm.value.loading = false
  }
}

async function emailRegister() {
  emailForm.value.loading = true
  emailForm.value.error = ''
  try {
    const res = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: emailForm.value.email,
        password: emailForm.value.password,
        displayName: emailForm.value.displayName,
        inviteCode: emailForm.value.inviteCode || null,
      }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      emailForm.value.error = err.message ?? 'Error al registrarse'
      return
    }
    const data = await res.json()
    authToken.value = data.token
    localStorage.setItem(JWT_KEY, data.token)
    await loadMe()
    if (userPools.value.length > 0) { await loadRemoteState(); animateHero() }
  } finally {
    emailForm.value.loading = false
  }
}

// ─── Crear porra ──────────────────────────────────────────────────────────────
const createPoolForm = ref({ name: '', description: '', entryFee: 10, loading: false, error: '' })

async function createPool() {
  if (!createPoolForm.value.name.trim()) { createPoolForm.value.error = 'El nombre es obligatorio'; return }
  createPoolForm.value.loading = true
  createPoolForm.value.error = ''
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/pools`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: createPoolForm.value.name,
        description: createPoolForm.value.description,
        entryFeeCents: Math.round(createPoolForm.value.entryFee * 100),
        currency: 'EUR',
      }),
    })
    if (!res.ok) { createPoolForm.value.error = 'Error al crear la porra'; return }
    await loadMe()
    await loadRemoteState()
    animateHero()
  } finally {
    createPoolForm.value.loading = false
  }
}

// ──────────────────────────────────────────
//  STATE
// ──────────────────────────────────────────
const activeTab   = ref<TabId>('home')
const activePool  = ref<number | null>(null)

// ──────────────────────────────────────────
//  DATA
// ──────────────────────────────────────────
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

const pools = ref<Pool[]>([])

const EMPTY_POOL: Pool = { id: 0, name: '', code: '', status: '', statusType: '', userRole: '', members: 0, paid: 0, pot: 0 }
const selectedPool   = computed(() => pools.value.find((p) => p.id === activePool.value) ?? pools.value[0] ?? EMPTY_POOL)
const groupRounds    = computed(() => bracketRounds.value.filter(r => r.name.startsWith('Group')))
const knockoutRounds = computed(() => bracketRounds.value.filter(r => !r.name.startsWith('Group')))

const matches = ref<Match[]>([
  { id: 1, home: 'Espana',    away: 'Alemania',  homeFl: 'es',     awayFl: 'de',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Partido de prueba. Sin resultado real.', kickoff: null, source: null },
  { id: 2, home: 'Brasil',    away: 'Portugal',  homeFl: 'br',     awayFl: 'pt',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Pendiente de sincronizar con API-Football.', kickoff: null, source: null },
  { id: 3, home: 'Argentina', away: 'Francia',   homeFl: 'ar',     awayFl: 'fr',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Editable mientras no empiece.', kickoff: null, source: null },
  { id: 4, home: 'Inglaterra',away: 'Italia',    homeFl: 'gb-eng', awayFl: 'it',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Sin puntos todavia.', kickoff: null, source: null },
])

const ranking = ref<RankingRow[]>([
  { pos: 1, name: 'Diego',    avatar: 'DI', points: 0, exact: 0, winners: 0, prize: 0, delta: '=', alive: true },
  { pos: 2, name: 'Juan',     avatar: 'JU', points: 0, exact: 0, winners: 0, prize: 0, delta: '=', alive: true },
  { pos: 3, name: 'David',    avatar: 'DA', points: 0, exact: 0, winners: 0, prize: 0, delta: '=', alive: true },
  { pos: 4, name: 'Sonia',    avatar: 'SO', points: 0, exact: 0, winners: 0, prize: 0, delta: '=', alive: true },
  { pos: 5, name: 'Petri',    avatar: 'PE', points: 0, exact: 0, winners: 0, prize: 0, delta: '=', alive: true },
  { pos: 6, name: 'Fernando', avatar: 'FE', points: 0, exact: 0, winners: 0, prize: 0, delta: '=', alive: true },
])

const initialRanking = ref<InitialRankingRow[]>([
  { pos: 1, name: 'Diego', points: 0, exact: 0, winners: 0, bonus: 'Sin bonus todavia' },
  { pos: 2, name: 'Juan',  points: 0, exact: 0, winners: 0, bonus: 'Sin bonus todavia' },
  { pos: 3, name: 'David', points: 0, exact: 0, winners: 0, bonus: 'Sin bonus todavia' },
])

const prizeRows = ref<PrizeRow[]>([
  { label: 'Pleno de ganadores',    amount: 0, state: 'Pendiente', stateType: 'pending', contenders: 0, pct: 75 },
  { label: 'Clasificacion general', amount: 0, state: 'Pendiente', stateType: 'pending', contenders: 0, pct: 10 },
  { label: 'Mejor apuesta inicial', amount: 0, state: 'Pendiente', stateType: 'pending', contenders: 0, pct: 5  },
  { label: 'Mas exactos',           amount: 0, state: 'Pendiente', stateType: 'pending', contenders: 0, pct: 5  },
  { label: 'Mas ganadores',         amount: 0, state: 'Pendiente', stateType: 'pending', contenders: 0, pct: 5  },
  { label: 'Campeon acertado',      amount: 0, state: 'Pendiente', stateType: 'pending', contenders: 0, pct: 5  },
])

const pendingPayments = ref<PendingPayment[]>([])

// ─── Admin state ───────────────────────────────────────────────────────────
type Member = { memberId: number; userId: number; displayName: string; email: string; role: string; paymentStatus: string }
const inviteModal   = ref({ open: false, name: '', email: '', loading: false, error: '' })
const resultForm    = ref({ matchId: null as number | null, homeGoals: 0, awayGoals: 0, loading: false })
const memberList    = ref<Member[]>([])
const membersLoaded = ref(false)

async function loadMembers() {
  if (!activePoolId.value) return
  const res = await fetchWithAuth(`${API_BASE_URL}/admin/members/${activePoolId.value}`)
  if (res.ok) {
    memberList.value = await res.json()
    membersLoaded.value = true
  }
}

async function removeMember(memberId: number, name: string) {
  if (!confirm(`¿Eliminar a ${name} de la porra? Se borrarán sus predicciones y pagos.`)) return
  await fetchWithAuth(`${API_BASE_URL}/admin/members/${memberId}`, { method: 'DELETE' })
  await Promise.all([loadMembers(), loadDashboard()])
}

async function confirmPayment(paymentId: number) {
  await fetchWithAuth(`${API_BASE_URL}/admin/payments/${paymentId}/confirm`, { method: 'POST' })
  await loadDashboard()
}

async function setMatchResult() {
  if (!resultForm.value.matchId) return
  resultForm.value.loading = true
  try {
    await fetchWithAuth(`${API_BASE_URL}/admin/matches/${resultForm.value.matchId}/result`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ homeGoals: resultForm.value.homeGoals, awayGoals: resultForm.value.awayGoals }),
    })
    await loadDashboard()
    resultForm.value.matchId = null
    resultForm.value.homeGoals = 0
    resultForm.value.awayGoals = 0
  } finally {
    resultForm.value.loading = false
  }
}

async function addMember() {
  inviteModal.value.loading = true
  inviteModal.value.error = ''
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/members`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ poolId: activePoolId.value, displayName: inviteModal.value.name, email: inviteModal.value.email }),
    })
    if (res.status === 409) { inviteModal.value.error = 'Ya es participante'; return }
    if (!res.ok) { inviteModal.value.error = 'Error al añadir'; return }
    inviteModal.value.open = false
    inviteModal.value.name = ''
    inviteModal.value.email = ''
    await loadDashboard()
  } finally {
    inviteModal.value.loading = false
  }
}

const bracketRounds = ref<BracketRound[]>([
  {
    name: 'Cuartos',
    matches: [
      { home: 'Espana',     homeFl: 'es',     away: 'Alemania',   awayFl: 'de',     pred: '0-0', real: 'Pend.', winner: 'Pendiente', done: false },
      { home: 'Brasil',     homeFl: 'br',     away: 'Portugal',   awayFl: 'pt',     pred: '0-0', real: 'Pend.', winner: 'Pendiente', done: false },
      { home: 'Argentina',  homeFl: 'ar',     away: 'Francia',    awayFl: 'fr',     pred: '0-0', real: 'Pend.', winner: 'Pendiente', done: false },
      { home: 'Inglaterra', homeFl: 'gb-eng', away: 'Italia',     awayFl: 'it',     pred: '0-0', real: 'Pend.', winner: 'Pendiente', done: false },
    ],
  },
  {
    name: 'Semis',
    matches: [
      { home: 'Pendiente', homeFl: 'un', away: 'Pendiente', awayFl: 'un', pred: '0-0', real: 'Por jugar', winner: 'Pendiente', done: false },
      { home: 'Pendiente', homeFl: 'un', away: 'Pendiente', awayFl: 'un', pred: '0-0', real: 'Por jugar', winner: 'Pendiente', done: false },
    ],
  },
  {
    name: 'Final',
    matches: [
      { home: 'Pendiente', homeFl: 'un', away: 'Pendiente', awayFl: 'un', pred: '0-0', real: 'Por jugar', winner: 'Pendiente', done: false },
    ],
  },
])

// ─── Prediction modal ───────────────────────────────────────────────────────
type PredModal = {
  open: boolean
  matchId: number | null
  home: string
  away: string
  homeFl: string
  awayFl: string
  homeGoals: number
  awayGoals: number
  aiLoading: boolean
  aiSource: string | null
}
const predModal = ref<PredModal>({
  open: false, matchId: null,
  home: '', away: '', homeFl: 'un', awayFl: 'un',
  homeGoals: 0, awayGoals: 0,
  aiLoading: false, aiSource: null,
})

function openPredModal(m: Match) {
  const parts = m.pred.replace(/\s/g,'').split('-')
  predModal.value = {
    open: true,
    matchId: m.id,
    home: m.home, away: m.away,
    homeFl: m.homeFl, awayFl: m.awayFl,
    homeGoals: parseInt(parts[0] ?? '0') || 0,
    awayGoals: parseInt(parts[1] ?? '0') || 0,
    aiLoading: false, aiSource: null,
  }
}

async function askAiPrediction() {
  if (!predModal.value.matchId) return
  predModal.value.aiLoading = true
  predModal.value.aiSource = null
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/ai/${predModal.value.matchId}`, { method: 'POST' })
    const data = await res.json()
    if (data.error) throw new Error(data.error)
    predModal.value.homeGoals = data.homeGoals
    predModal.value.awayGoals = data.awayGoals
    predModal.value.aiSource = data.source
  } catch (e) {
    console.error('AI prediction failed', e)
  } finally {
    predModal.value.aiLoading = false
  }
}

const bulkLoading = ref(false)

async function bulkRandomPredictions() {
  bulkLoading.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/bulk-random`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ poolId: activePoolId.value }),
    })
    if (!res.ok) throw new Error(`${res.status}`)
    const data: Array<{ matchId: number; homeGoals: number; awayGoals: number }> = await res.json()
    if (!Array.isArray(data)) throw new Error('Unexpected response')
    // Apply each prediction to the local matches array
    data.forEach(pred => {
      const m = matches.value.find(x => x.id === pred.matchId)
      if (m) m.pred = `${pred.homeGoals} - ${pred.awayGoals}`
    })
    // Animate the cards briefly
    const els = document.querySelectorAll<HTMLElement>('.match-card')
    els.forEach((el, i) => {
      el.animate(
        [{ boxShadow: '0 0 0 2px rgba(245,197,66,0.7)' }, { boxShadow: '0 0 0 2px rgba(245,197,66,0)' }],
        { duration: 600, delay: i * 18, easing: 'ease-out', fill: 'both' },
      )
    })
  } catch (e) {
    console.error('Bulk prediction failed', e)
  } finally {
    bulkLoading.value = false
  }
}

async function savePrediction() {
  if (!predModal.value.matchId) return
  const { matchId, homeGoals, awayGoals } = predModal.value
  predModal.value.open = false
  // optimistic update
  const match = matches.value.find(m => m.id === matchId)
  if (match) match.pred = `${homeGoals} - ${awayGoals}`
  // persist to DB
  try {
    await fetchWithAuth(`${API_BASE_URL}/predictions/match`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ poolId: activePoolId.value, matchId, homeGoals, awayGoals }),
    })
  } catch (e) {
    console.error('Failed to save prediction', e)
  }
}

const recommendations = ref<Recommendation[]>([
  { type: 'info', text: 'Crea participantes, marca pagos y empieza a meter predicciones para probar el flujo.' },
  { type: 'info', text: 'Cuando conectemos API-Football, los partidos reales vendran del backend.' },
  { type: 'info', text: 'Los puntos arrancan a 0: ganador +2, exacto +2, goles +1 por equipo.' },
])

// API-Football sync status (se rellenara desde backend)
const apiSync = ref<ApiSyncState>({
  provider:    'API-Football',
  league:      1,
  season:      2026,
  mode:         'direct',
  cadence:      '30s en directo / 15min día partido',
  dailyLimit:   '100 req/día (free tier)',
  lastSync:     'Nunca',
  liveMatches:  0,
  status:       'error' as 'ok' | 'error' | 'syncing',
  activeSource: 'WC26_IR' as 'WC26_IR' | 'API_FOOTBALL',
  jobs: [
    { key: 'LIVE_FIXTURES',   label: 'Fixtures en directo',    lastRun: 'Pendiente', status: 'pending', provider: 'api-football' },
    { key: 'DAILY_FIXTURES',  label: 'Fixtures del día',       lastRun: 'Pendiente', status: 'pending', provider: 'api-football' },
    { key: 'ROUNDS',          label: 'Rondas del torneo',      lastRun: 'Pendiente', status: 'pending', provider: 'api-football' },
    { key: 'STANDINGS',       label: 'Clasificaciones',        lastRun: 'Pendiente', status: 'pending', provider: 'api-football' },
    { key: 'WC26_TEAMS',      label: '48 equipos 2026 🆓',    lastRun: 'Pendiente', status: 'pending', provider: 'wc26' },
    { key: 'WC26_FIXTURES',   label: 'Partidos 2026 🆓',      lastRun: 'Pendiente', status: 'pending', provider: 'wc26' },
    { key: 'WC26_GROUPS',     label: 'Grupos 2026 🆓',        lastRun: 'Pendiente', status: 'pending', provider: 'wc26' },
  ],
})

const allTabs = [
  { id: 'home'    as const, label: 'Inicio',   icon: Home,         adminOnly: false },
  { id: 'matches' as const, label: 'Partidos', icon: CalendarDays, adminOnly: false },
  { id: 'ranking' as const, label: 'Ranking',  icon: Trophy,       adminOnly: false },
  { id: 'prizes'  as const, label: 'Premios',  icon: Banknote,     adminOnly: false },
  { id: 'admin'   as const, label: 'Admin',    icon: Settings,     adminOnly: true  },
]
const tabs = computed(() =>
  allTabs.filter(t => !t.adminOnly || userRole.value === 'ADMIN')
)

// ──────────────────────────────────────────
//  HELPERS
// ──────────────────────────────────────────
function posLabel(pos: number) {
  return pos === 1 ? '🥇' : pos === 2 ? '🥈' : pos === 3 ? '🥉' : `${pos}º`
}

// ──────────────────────────────────────────
//  ANIMATIONS
// ──────────────────────────────────────────

/** Número roll-up usando rAF */
function animateCounter(el: HTMLElement, target: number, suffix = '') {
  const duration = 900
  const start = performance.now()
  function tick(now: number) {
    const p = Math.min((now - start) / duration, 1)
    const ease = 1 - Math.pow(1 - p, 4)           // easeOutQuart
    el.textContent = Math.round(ease * target) + suffix
    if (p < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

/** Entrada en cascada con WAAPI */
function animateCards(selector: string, staggerMs = 60) {
  const els = document.querySelectorAll<HTMLElement>(selector)
  els.forEach((el, i) => {
    el.animate(
      [{ opacity: '0', transform: 'translateY(22px)' }, { opacity: '1', transform: 'translateY(0)' }],
      { duration: 420, delay: i * staggerMs, easing: 'cubic-bezier(0.22,1,0.36,1)', fill: 'both' },
    )
  })
}

/** Barras de premios */
function animatePrizeBars() {
  document.querySelectorAll<HTMLElement>('.prize-row__bar').forEach((bar, i) => {
    const target = bar.dataset.pct ?? '0'
    bar.animate(
      [{ width: '0%' }, { width: `${target}%` }],
      { duration: 800, delay: i * 80, easing: 'cubic-bezier(0.16,1,0.3,1)', fill: 'both' },
    )
  })
}

/** Entrada de tab */
function animateTabIn() {
  nextTick(() => {
    animateCards('.tab-content > *', 55)
    if (activeTab.value === 'prizes') setTimeout(animatePrizeBars, 180)
  })
}

/** Hero al montar */
function animateHero() {
  document.querySelector('.hero-pitch')?.animate(
    [{ opacity: '0', transform: 'translateY(-12px)' }, { opacity: '1', transform: 'translateY(0)' }],
    { duration: 600, easing: 'cubic-bezier(0.22,1,0.36,1)', fill: 'both' },
  )
  nextTick(() => {
    const potEl   = document.querySelector<HTMLElement>('.js-counter-pot')
    const prizeEl = document.querySelector<HTMLElement>('.js-counter-prize')
    if (potEl)   animateCounter(potEl, selectedPool.value?.pot ?? 0, ' €')
    if (prizeEl) animateCounter(prizeEl, 0, ' €')
  })
}

/** Micro-bounce al cambiar porra */
function animatePoolSwitch() {
  document.querySelector('.scoreboard-card')?.animate(
    [{ transform: 'scale(0.97)', opacity: '0.6' }, { transform: 'scale(1)', opacity: '1' }],
    { duration: 320, easing: 'cubic-bezier(0.22,1,0.36,1)', fill: 'both' },
  )
  nextTick(() => {
    const potEl = document.querySelector<HTMLElement>('.js-counter-pot')
    if (potEl) animateCounter(potEl, selectedPool.value?.pot ?? 0, ' €')
  })
}

/** Flash verde en sync card */
function triggerSyncAnimation(jobKey: string) {
  apiSync.value.status = 'syncing'
  const job = apiSync.value.jobs.find(j => j.key === jobKey)
  if (job) job.status = 'syncing' as string

  const endpointByJob: Record<string, string> = {
    LIVE_FIXTURES:  '/admin/sports-sync/fixtures/live',
    DAILY_FIXTURES: '/admin/sports-sync/fixtures/today',
    ROUNDS:         '/admin/sports-sync/rounds',
    STANDINGS:      '/admin/sports-sync/standings',
    WC26_TEAMS:     '/admin/sports-sync/wc26/teams',
    WC26_FIXTURES:  '/admin/sports-sync/wc26/fixtures',
    WC26_GROUPS:    '/admin/sports-sync/wc26/groups',
    WC26_ALL:       '/admin/sports-sync/wc26/all',
  }

  fetchWithAuth(`${API_BASE_URL}${endpointByJob[jobKey]}`, { method: 'POST' })
    .then(() => loadApiSyncStatus())
    .catch(() => {
      apiSync.value.status = 'error'
      if (job) job.status = 'error'
    })
    .finally(() => {
    const card = document.querySelector<HTMLElement>(`.sync-card[data-key="${jobKey}"]`)
    card?.animate(
      [{ backgroundColor: 'rgba(46,204,113,0.28)' }, { backgroundColor: 'rgba(255,255,255,0.06)' }],
      { duration: 900, easing: 'ease-out', fill: 'both' },
    )
  })
}

async function loadDashboard() {
  if (!activePoolId.value) return
  const res = await fetchWithAuth(`${API_BASE_URL}/dashboard/${activePoolId.value}`)
  if (!res.ok) throw new Error(`Dashboard failed: ${res.status}`)
  const data = await res.json()
  pools.value = data.pools
  matches.value = data.matches
  ranking.value = data.ranking
  initialRanking.value = data.initialRanking
  prizeRows.value = data.prizeRows
  bracketRounds.value = data.bracketRounds
  pendingPayments.value = data.pendingPayments
  recommendations.value = data.recommendations
  if (pools.value.length > 0 && !activePool.value) activePool.value = pools.value[0].id
}

async function loadApiSyncStatus() {
  const res = await fetchWithAuth(`${API_BASE_URL}/admin/sports-sync/status`)
  if (!res.ok) throw new Error(`Sync status failed: ${res.status}`)
  const data = await res.json()
  apiSync.value = {
    provider: data.provider,
    league: data.league,
    season: data.season,
    mode: data.mode,
    cadence: `${data.livePollingSeconds}s en directo`,
    dailyLimit: '100 req/dia (free tier)',
    lastSync: 'Nunca',
    liveMatches: 0,
    status: data.configured ? 'ok' : 'error',
    activeSource: (data.activeSource ?? 'WC26_IR') as 'WC26_IR' | 'API_FOOTBALL',
    jobs: data.jobs.map((remoteJob: { key: string; label: string; status: string }) => ({
      key: remoteJob.key,
      label: remoteJob.label,
      lastRun: 'Pendiente',
      status: remoteJob.status.toLowerCase(),
      provider: remoteJob.key.startsWith('WC26_') ? 'wc26' : 'api-football',
    })),
  }
}

function fmtKickoff(iso: string | null): string {
  if (!iso) return 'Por confirmar'
  const d = new Date(iso)
  return d.toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Madrid' })
}

async function setActiveSource(source: 'WC26_IR' | 'API_FOOTBALL') {
  await fetchWithAuth(`${API_BASE_URL}/admin/sports-sync/source`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ source }),
  })
  apiSync.value.activeSource = source
  // reload dashboard to reflect new source
  await loadDashboard()
}

async function loadRemoteState() {
  try {
    await Promise.all([loadDashboard(), loadApiSyncStatus()])
  } catch (error) {
    console.error('No se pudo cargar estado remoto', error)
  }
}

// ──────────────────────────────────────────
//  LIFECYCLE
// ──────────────────────────────────────────
onMounted(async () => {
  if (authToken.value) {
    await loadMe()
    if (userPools.value.length > 0) {
      await loadRemoteState()
      animateHero()
      setTimeout(() => {
        animateCards('.pool-pill', 80)
        animateCards('.home-grid > *', 70)
        animateCards('.bracket-section', 0)
      }, 100)
    }
  } else {
    initGoogleAuth()
  }
})

watch(activeTab, async (tab) => {
  animateTabIn()
  if (tab === 'home') await loadDashboard()
  if (tab === 'admin') await loadMembers()
})

watch(activePool, () => animatePoolSwitch())
</script>

<template>

  <!-- ═══════════════ LOGIN ═══════════════ -->
  <div v-if="!authToken" class="login-screen">
    <div class="login-card" style="max-width:400px">
      <span class="login-ball">⚽</span>
      <h1>Mundia</h1>
      <p class="login-sub">Porra familiar · Mundial 2026</p>

      <!-- Tabs -->
      <div class="auth-tabs">
        <button :class="['auth-tab', { active: authMode === 'google' }]" @click="authMode = 'google'">Google</button>
        <button :class="['auth-tab', { active: authMode === 'login' }]" @click="authMode = 'login'">Email</button>
        <button :class="['auth-tab', { active: authMode === 'register' }]" @click="authMode = 'register'">Registrarse</button>
      </div>

      <!-- Google -->
      <div v-if="authMode === 'google'" id="g_signin_btn" class="login-google-btn"></div>

      <!-- Email login -->
      <div v-else-if="authMode === 'login'" class="invite-form" style="width:100%;text-align:left">
        <label class="invite-label">Email</label>
        <input v-model="emailForm.email" class="invite-input" type="email" placeholder="tu@email.com" autocomplete="email" @keyup.enter="emailLogin" />
        <label class="invite-label">Contraseña</label>
        <input v-model="emailForm.password" class="invite-input" type="password" placeholder="••••••••" autocomplete="current-password" @keyup.enter="emailLogin" />
        <p v-if="emailForm.error" class="invite-error">{{ emailForm.error }}</p>
        <button class="btn btn--primary" style="width:100%;margin-top:4px" :disabled="emailForm.loading" @click="emailLogin">
          {{ emailForm.loading ? 'Entrando…' : 'Entrar' }}
        </button>
      </div>

      <!-- Register -->
      <div v-else-if="authMode === 'register'" class="invite-form" style="width:100%;text-align:left">
        <label class="invite-label">Nombre</label>
        <input v-model="emailForm.displayName" class="invite-input" type="text" placeholder="Tu nombre" autocomplete="name" />
        <label class="invite-label">Email</label>
        <input v-model="emailForm.email" class="invite-input" type="email" placeholder="tu@email.com" autocomplete="email" />
        <label class="invite-label">Contraseña <span style="color:var(--muted);font-weight:400">(mín. 6 caracteres)</span></label>
        <input v-model="emailForm.password" class="invite-input" type="password" placeholder="••••••••" autocomplete="new-password" />
        <label class="invite-label">Código de porra <span style="color:var(--muted);font-weight:400">(opcional)</span></label>
        <input v-model="emailForm.inviteCode" class="invite-input" type="text" placeholder="MUNDIA-26" style="text-transform:uppercase" />
        <p v-if="emailForm.error" class="invite-error">{{ emailForm.error }}</p>
        <button class="btn btn--primary" style="width:100%;margin-top:4px" :disabled="emailForm.loading" @click="emailRegister">
          {{ emailForm.loading ? 'Creando cuenta…' : 'Crear cuenta' }}
        </button>
      </div>
    </div>
  </div>

  <!-- ═══════════════ ONBOARDING — sin porra ═══════════════ -->
  <div v-else-if="appReady && userPools.length === 0" class="login-screen">
    <div class="login-card" style="max-width:420px">
      <span class="login-ball">⚽</span>
      <h1>Bienvenido, {{ currentUser?.name?.split(' ')[0] }}</h1>
      <p class="login-sub">Crea tu porra o espera a que el admin te invite.</p>

      <div class="invite-form" style="width:100%;text-align:left">
        <label class="invite-label">Nombre de la porra</label>
        <input v-model="createPoolForm.name" class="invite-input" type="text" placeholder="Mundial Familia 2026" />
        <label class="invite-label">Descripción (opcional)</label>
        <input v-model="createPoolForm.description" class="invite-input" type="text" placeholder="Porra familiar del Mundial" />
        <label class="invite-label">Inscripción por jugador (€)</label>
        <input v-model.number="createPoolForm.entryFee" class="invite-input" type="number" min="0" step="1" />
        <p v-if="createPoolForm.error" class="invite-error">{{ createPoolForm.error }}</p>
      </div>

      <button
        class="btn btn--primary"
        style="width:100%;margin-top:8px"
        type="button"
        :disabled="createPoolForm.loading"
        @click="createPool"
      >
        {{ createPoolForm.loading ? 'Creando…' : '🏆 Crear mi porra' }}
      </button>

      <button class="btn btn--ghost" style="margin-top:4px" type="button" @click="signOut">
        Cambiar cuenta
      </button>
    </div>
  </div>

  <!-- ═══════════════ CARGANDO ═══════════════ -->
  <div v-else-if="authToken && !appReady" class="login-screen">
    <div class="login-card">
      <span class="login-ball">⚽</span>
      <p class="login-sub">Cargando…</p>
    </div>
  </div>

  <main v-else class="app-shell">

    <!-- ═══════════════ TOPBAR ═══════════════ -->
    <header class="topbar">
      <div class="brand">
        <span class="brand-ball">⚽</span>
        <div>
          <p class="eyebrow">Porra familiar</p>
          <h1>Mundia</h1>
        </div>
      </div>

      <div class="topbar-right">
        <div class="user-chip">
          <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" class="user-avatar" :alt="currentUser.name" />
          <span v-else class="user-avatar user-avatar--initials">{{ currentUser ? currentUser.name.slice(0,2).toUpperCase() : '?' }}</span>
          <span class="user-name">{{ currentUser?.name }}</span>
          <button class="btn btn--ghost btn--xs" type="button" @click="signOut" title="Cerrar sesión">✕</button>
        </div>
      </div>

      <div class="pool-switch" role="tablist" aria-label="Seleccionar porra">
        <button
          v-for="pool in pools"
          :key="pool.id"
          :class="['pool-pill', { active: activePool === pool.id }]"
          type="button"
          role="tab"
          :aria-selected="activePool === pool.id"
          @click="activePool = pool.id"
        >
          <span class="pool-pill__name">{{ pool.name }}</span>
          <span class="pool-pill__meta">
            <span :class="['badge', `badge--${pool.statusType}`]">{{ pool.status }}</span>
            <span class="pool-pill__paid">{{ pool.paid }}/{{ pool.members }} pagados · {{ pool.pot }} €</span>
          </span>
        </button>
      </div>
    </header>

    <!-- ═══════════════ HERO ESTADIO ═══════════════ -->
    <section class="hero-pitch" aria-label="Resumen de la porra">
      <div class="scoreboard-card">
        <div class="scoreboard-card__header">
          <span :class="['badge', `badge--${selectedPool.statusType}`]">{{ selectedPool.status }}</span>
          <span class="code-chip">{{ selectedPool.code }}</span>
        </div>
        <div class="scoreboard-card__stats">
          <div class="stat-box stat-box--gold">
            <p>Bote total</p>
            <strong class="js-counter-pot">{{ selectedPool.pot }} €</strong>
          </div>
          <div class="stat-box stat-box--green">
            <p>Premio posible</p>
            <strong class="js-counter-prize">0 €</strong>
          </div>
        </div>
        <div class="scoreboard-card__footer">
          <span><Users :size="12" /> {{ selectedPool.paid }} pagados</span>
          <span>
            <span class="pulse-dot"></span>
            0 vivos al pleno
          </span>
          <span><CalendarDays :size="12" /> 4 partidos abiertos</span>
        </div>
      </div>

      <div class="next-match-card">
        <p class="eyebrow"><Zap :size="12" /> Próximo partido</p>
        <div class="next-match-card__duel">
          <div class="team-block">
            <span class="fi fi-br flag-xl"></span>
            <strong>Brasil</strong>
          </div>
          <div class="vs-block">
            <span class="vs-label">VS</span>
            <span class="match-time">18:30</span>
          </div>
          <div class="team-block">
            <span class="fi fi-pt flag-xl"></span>
            <strong>Portugal</strong>
          </div>
        </div>
        <div class="next-match-card__pred">
          <span>Tu predicción</span>
          <strong>0 - 0</strong>
        </div>
        <button class="btn btn--primary" type="button" @click="matches[0] && openPredModal(matches[0])">
          <Pencil :size="15" /> Editar predicción
        </button>
      </div>
    </section>

    <!-- ═══════════════ CONTENIDO POR TAB ═══════════════ -->
    <div class="tab-content">

      <!-- ─── INICIO ─── -->
      <template v-if="activeTab === 'home'">

        <section class="bracket-section" aria-label="Cuadro del torneo">
          <div class="section-header">
            <Trophy :size="18" />
            <div>
              <h2>Tu cuadro del torneo</h2>
              <p class="muted">Predicción inicial vs. resultados reales</p>
            </div>
          </div>

          <!-- Fase de Grupos -->
          <template v-if="groupRounds.length">
            <p class="phase-label"><span class="fi fi-un flag-xs"></span> Fase de Grupos</p>
            <div class="groups-grid">
              <div v-for="round in groupRounds" :key="round.name" class="group-block">
                <p class="round-label">{{ round.name }}</p>
                <div class="round-matches">
                  <article
                    v-for="m in round.matches"
                    :key="`${round.name}-${m.home}`"
                    :class="['bracket-card', { 'bracket-card--done': m.done }]"
                  >
                    <div class="bracket-team">
                      <span :class="`fi fi-${m.homeFl} flag-sm`"></span>
                      <span class="bracket-team__name">{{ m.home }}</span>
                      <strong class="bracket-score">{{ m.pred.split('-')[0] }}</strong>
                    </div>
                    <div class="bracket-team">
                      <span :class="`fi fi-${m.awayFl} flag-sm`"></span>
                      <span class="bracket-team__name">{{ m.away }}</span>
                      <strong class="bracket-score">{{ m.pred.split('-')[1] }}</strong>
                    </div>
                    <div class="bracket-footer">
                      <span class="bracket-real">Real: {{ m.real }}</span>
                      <span v-if="m.done" class="bracket-winner">→ {{ m.winner }}</span>
                    </div>
                  </article>
                </div>
              </div>
            </div>
          </template>

          <!-- Eliminatorias -->
          <template v-if="knockoutRounds.length">
            <p class="phase-label"><ChevronRight :size="13" /> Eliminatorias</p>
            <div class="bracket-scroll-wrap">
              <div class="bracket-board">
                <div v-for="round in knockoutRounds" :key="round.name" class="bracket-round">
                  <p class="round-label">{{ round.name }}</p>
                  <div class="round-matches">
                    <article
                      v-for="m in round.matches"
                      :key="`${round.name}-${m.home}`"
                      :class="['bracket-card', { 'bracket-card--done': m.done }]"
                    >
                      <div class="bracket-team">
                        <span :class="`fi fi-${m.homeFl} flag-sm`"></span>
                        <span class="bracket-team__name">{{ m.home }}</span>
                        <strong class="bracket-score">{{ m.pred.split('-')[0] }}</strong>
                      </div>
                      <div class="bracket-team">
                        <span :class="`fi fi-${m.awayFl} flag-sm`"></span>
                        <span class="bracket-team__name">{{ m.away }}</span>
                        <strong class="bracket-score">{{ m.pred.split('-')[1] }}</strong>
                      </div>
                      <div class="bracket-footer">
                        <span class="bracket-real">Real: {{ m.real }}</span>
                        <span class="bracket-winner">→ {{ m.winner }}</span>
                      </div>
                    </article>
                  </div>
                </div>
                <div class="bracket-champion">
                  <Crown :size="24" />
                  <span class="eyebrow">Campeón</span>
                  <span class="fi fi-un flag-lg"></span>
                  <strong>Por decidir</strong>
                </div>
              </div>
            </div>
          </template>
        </section>

        <div class="home-grid">
          <article class="panel panel--prize">
            <div class="panel__header">
              <Sparkles :size="18" />
              <h2>Todavía puedes ganar</h2>
            </div>
            <p class="prize-amount">0 €</p>
            <p class="muted">Vas 1º · 0 puntos · premios pendientes</p>
            <ul class="reco-list">
              <li v-for="r in recommendations" :key="r.text" :class="`reco-item reco-item--${r.type}`">
                <CircleAlert v-if="r.type === 'danger'" :size="14" />
                <Check      v-else-if="r.type === 'success'" :size="14" />
                <Star       v-else :size="14" />
                <span>{{ r.text }}</span>
              </li>
            </ul>
          </article>

          <article class="panel panel--stats">
            <div class="panel__header">
              <TrendingUp :size="18" />
              <h2>Tu jornada</h2>
            </div>
            <div class="mini-stats">
              <div class="mini-stat"><span>Puntos</span><strong>0</strong></div>
              <div class="mini-stat"><span>Exactos</span><strong>0</strong></div>
              <div class="mini-stat"><span>Ganadores</span><strong>0</strong></div>
              <div class="mini-stat"><span>Posicion</span><strong>1º</strong></div>
            </div>
          </article>

          <article class="panel panel--initial">
            <div class="panel__header">
              <Medal :size="18" />
              <h2>Apuesta inicial</h2>
            </div>
            <p class="initial-pos">-</p>
            <p class="muted">0 pts · bonus pendiente</p>
            <div class="tag-row">
              <span class="tag tag--blue">0 exactos</span>
              <span class="tag tag--blue">0 ganadores</span>
              <span class="tag tag--gold">Pendiente</span>
            </div>
          </article>

          <article class="panel panel--fix">
            <div class="panel__header">
              <CircleAlert :size="18" />
              <h2>Corrección necesaria</h2>
            </div>
            <div class="fix-row">
              <div>
                <p class="fix-label">Cruce imposible · Cuartos</p>
                <h3>Alemania no puede ser 1ª de grupo</h3>
                <p class="muted">Cuando haya resultados reales, aqui apareceran los cambios recomendados.</p>
              </div>
              <button class="btn btn--icon" type="button" aria-label="Revisar cruce">
                <ChevronRight :size="20" />
              </button>
            </div>
          </article>
        </div>
      </template>

      <!-- ─── PARTIDOS ─── -->
      <template v-if="activeTab === 'matches'">

        <!-- Toolbar -->
        <div class="matches-toolbar">
          <p class="muted">{{ matches.length }} partidos · {{ matches.filter(m => m.pred !== '0 - 0').length }} predichos</p>
          <button
            class="btn btn--outline btn--sm"
            type="button"
            :disabled="bulkLoading"
            @click="bulkRandomPredictions"
          >
            <Sparkles :size="13" :class="{ 'spin': bulkLoading }" />
            {{ bulkLoading ? 'Prediciendo…' : 'Predecir todos al azar' }}
          </button>
        </div>

        <div class="matches-grid">
          <article v-for="m in matches" :key="m.id" class="match-card">
            <div class="match-card__top">
              <span :class="['badge', `badge--${m.statusType}`]">{{ m.status }}</span>
              <span class="match-kickoff">{{ fmtKickoff(m.kickoff) }}</span>
              <strong v-if="m.points !== null" class="pts-badge">+{{ m.points }} pts</strong>
            </div>
            <div class="match-card__duel">
              <div class="match-team">
                <span :class="`fi fi-${m.homeFl} flag-lg`"></span>
                <strong>{{ m.home }}</strong>
              </div>
              <div class="match-center">
                <div class="score-display">
                  <span>{{ m.pred }}</span>
                  <small>predicción</small>
                </div>
                <span class="vs-divider">·</span>
                <div class="score-display">
                  <span>{{ m.real }}</span>
                  <small>real</small>
                </div>
              </div>
              <div class="match-team">
                <span :class="`fi fi-${m.awayFl} flag-lg`"></span>
                <strong>{{ m.away }}</strong>
              </div>
            </div>
            <button
              v-if="m.statusType === 'open' || m.statusType === 'warning'"
              class="btn btn--primary btn--sm"
              type="button"
              @click="openPredModal(m)"
            >
              <Pencil :size="13" /> Editar predicción
            </button>
          </article>
        </div>
      </template>

      <!-- ─── RANKING ─── -->
      <template v-if="activeTab === 'ranking'">
        <div class="ranking-layout">
          <div class="podium" aria-label="Podio">
            <div
              v-for="person in ranking.slice(0, 3)"
              :key="person.name"
              :class="`podium-slot podium-slot--${person.pos}`"
            >
              <div class="podium-avatar">{{ person.avatar }}</div>
              <span class="podium-pos">{{ posLabel(person.pos) }}</span>
              <strong class="podium-name">{{ person.name }}</strong>
              <span class="podium-pts">{{ person.points }} pts</span>
              <span v-if="person.prize > 0" class="podium-prize">≈{{ person.prize }} €</span>
            </div>
          </div>

          <article class="panel ranking-table-panel">
            <div class="panel__header"><Trophy :size="18" /><h2>Clasificación general</h2></div>
            <div class="ranking-table">
              <div v-for="p in ranking" :key="p.name" :class="['ranking-row', { 'ranking-row--alive': p.alive }]">
                <span class="ranking-pos">{{ posLabel(p.pos) }}</span>
                <div class="ranking-avatar">{{ p.avatar }}</div>
                <div class="ranking-info">
                  <strong>{{ p.name }}</strong>
                  <small>{{ p.exact }} exactos · {{ p.winners }} ganadores</small>
                </div>
                <div class="ranking-right">
                  <strong>{{ p.points }} pts</strong>
                  <span :class="['delta', p.delta.startsWith('+') ? 'delta--up' : p.delta === '=' ? 'delta--eq' : 'delta--down']">{{ p.delta }}</span>
                </div>
                <span v-if="p.prize > 0" class="ranking-prize">≈{{ p.prize }} €</span>
                <span v-if="p.alive" class="tag tag--green tag--xs">🔥 Pleno vivo</span>
              </div>
            </div>
          </article>

          <article class="panel initial-table-panel">
            <div class="panel__header"><Star :size="18" /><h2>Apuesta inicial</h2></div>
            <div class="ranking-table">
              <div v-for="p in initialRanking" :key="p.name" class="ranking-row">
                <span class="ranking-pos">{{ posLabel(p.pos) }}</span>
                <div class="ranking-avatar">{{ p.name.slice(0,2).toUpperCase() }}</div>
                <div class="ranking-info">
                  <strong>{{ p.name }}</strong>
                  <small>{{ p.exact }} exactos · {{ p.winners }} ganadores</small>
                </div>
                <div class="ranking-right">
                  <strong>{{ p.points }} pts</strong>
                  <span class="tag tag--gold tag--xs">{{ p.bonus }}</span>
                </div>
              </div>
            </div>
          </article>
        </div>
      </template>

      <!-- ─── PREMIOS ─── -->
      <template v-if="activeTab === 'prizes'">
        <div class="prizes-layout">
          <article class="panel panel--prize">
            <div class="panel__header">
              <Crown :size="18" />
              <h2>Pleno de Ganadores</h2>
              <span class="badge badge--alive tag--xs">Vivo</span>
            </div>
            <p class="prize-amount">0 €</p>
            <p class="muted">Acertar todos los ganadores y avances. Los marcadores exactos <strong>no</strong> eliminan del pleno.</p>
            <div class="pleno-survivors">
              <div v-for="n in ['FE','DI','SO']" :key="n" class="survivor-avatar">{{ n }}</div>
              <span class="muted">0 participantes vivos</span>
            </div>
          </article>

          <article class="panel panel--simulator">
            <div class="panel__header"><Sparkles :size="18" /><h2>Simulador · Tu premio máximo</h2></div>
            <div class="simulator-grid">
              <div class="sim-box"><span>Posición actual</span><strong>2º</strong></div>
              <div class="sim-box"><span>Mejor posible</span><strong>1º</strong></div>
              <div class="sim-box sim-box--gold"><span>Premio actual</span><strong>0 €</strong></div>
              <div class="sim-box sim-box--green"><span>Premio maximo</span><strong>0 €</strong></div>
            </div>
            <div class="tag-row" style="margin-top:14px">
              <span class="tag tag--green">✓ Clasif. general</span>
              <span class="tag tag--green">✓ Pleno vivo</span>
              <span class="tag tag--green">✓ Exactos</span>
              <span class="tag tag--muted">✗ Campeón (perdiste)</span>
            </div>
          </article>

          <article class="panel prize-table-panel">
            <div class="panel__header"><Banknote :size="18" /><h2>Reparto activo · Bote {{ selectedPool.pot }} €</h2></div>
            <div class="prize-table">
              <div v-for="row in prizeRows" :key="row.label" class="prize-row">
                <div class="prize-row__bar" :data-pct="row.pct" style="width:0%"></div>
                <div class="prize-row__content">
                  <div class="prize-row__info">
                    <strong>{{ row.label }}</strong>
                    <small>{{ row.contenders }} candidatos · {{ row.pct }}%</small>
                  </div>
                  <span :class="['badge', `badge--${row.stateType}`]">{{ row.state }}</span>
                  <strong class="prize-row__amount">{{ row.amount }} €</strong>
                </div>
              </div>
            </div>
          </article>
        </div>
      </template>

      <!-- ─── ADMIN ─── -->
      <template v-if="activeTab === 'admin'">
        <div class="admin-grid">

          <!-- Participantes -->
          <article class="panel admin-wide-panel">
            <div class="panel__header">
              <Users :size="18" />
              <h2>Participantes</h2>
              <div class="admin-stats" style="margin-left:auto;margin-bottom:0;gap:8px">
                <div class="admin-stat"><span>Total</span><strong>{{ memberList.length }}</strong></div>
                <div class="admin-stat admin-stat--green"><span>Pagados</span><strong>{{ memberList.filter(m => m.paymentStatus === 'CONFIRMED').length }}</strong></div>
                <div class="admin-stat admin-stat--warn"><span>Pendientes</span><strong>{{ memberList.filter(m => m.paymentStatus === 'PENDING').length }}</strong></div>
              </div>
              <button class="btn btn--primary btn--sm" type="button" @click="inviteModal.open = true">
                <Users :size="13" /> Añadir
              </button>
            </div>

            <div class="members-list">
              <div v-if="!membersLoaded" class="muted" style="padding:8px 0;font-size:0.8rem">Cargando…</div>
              <div v-else-if="memberList.length === 0" class="muted" style="padding:8px 0;font-size:0.8rem">Sin participantes todavía</div>
              <div v-for="m in memberList" :key="m.memberId" class="member-row">
                <div class="ranking-avatar">{{ m.displayName.slice(0,2).toUpperCase() }}</div>
                <div class="member-info">
                  <strong>{{ m.displayName }}</strong>
                  <small>{{ m.email }}</small>
                </div>
                <span :class="['badge', m.role === 'ADMIN' ? 'badge--active' : 'badge--draft']">{{ m.role }}</span>
                <span :class="['badge', m.paymentStatus === 'CONFIRMED' ? 'badge--open' : m.paymentStatus === 'PENDING' ? 'badge--warning' : 'badge--closed']">
                  {{ m.paymentStatus === 'CONFIRMED' ? 'Pagado' : m.paymentStatus === 'PENDING' ? 'Pendiente' : 'Sin pago' }}
                </span>
                <button
                  v-if="m.role !== 'ADMIN'"
                  class="btn btn--sm btn--ghost"
                  style="color:var(--coral);border:1px solid rgba(251,113,133,0.25);background:var(--coral-dim)"
                  type="button"
                  :title="`Eliminar a ${m.displayName}`"
                  @click="removeMember(m.memberId, m.displayName)"
                >✕</button>
              </div>
            </div>
          </article>

          <!-- Confirmar pagos -->
          <article class="panel">
            <div class="panel__header"><Check :size="18" /><h2>Confirmar pagos</h2></div>
            <div v-if="pendingPayments.length === 0" class="muted" style="padding:8px 0;font-size:0.8rem">Sin pagos pendientes</div>
            <div class="pending-list">
              <div v-for="p in pendingPayments" :key="p.id" class="pending-row">
                <div class="ranking-avatar">{{ p.name.slice(0,2).toUpperCase() }}</div>
                <strong>{{ p.name }}</strong>
                <button class="btn btn--sm btn--success" type="button" @click="confirmPayment(p.id)">Marcar pagado</button>
              </div>
            </div>
          </article>

          <!-- Cargar resultado -->
          <article class="panel admin-results-panel">
            <div class="panel__header"><Pencil :size="18" /><h2>Cargar resultado real</h2></div>
            <div class="result-form">
              <select
                v-model="resultForm.matchId"
                class="result-select"
                aria-label="Seleccionar partido"
              >
                <option :value="null">— Selecciona un partido —</option>
                <option v-for="m in matches" :key="m.id" :value="m.id">
                  {{ m.home }} vs {{ m.away }}
                </option>
              </select>
              <div class="result-inputs">
                <input v-model.number="resultForm.homeGoals" class="score-input" type="number" min="0" max="20" aria-label="Goles local" />
                <span class="vs-label">–</span>
                <input v-model.number="resultForm.awayGoals" class="score-input" type="number" min="0" max="20" aria-label="Goles visitante" />
              </div>
              <p class="muted">Override manual — gana sobre la fuente activa y queda auditado.</p>
              <button
                class="btn btn--primary"
                type="button"
                :disabled="!resultForm.matchId || resultForm.loading"
                @click="setMatchResult"
              >{{ resultForm.loading ? 'Guardando…' : 'Guardar resultado' }}</button>
            </div>
          </article>

          <!-- Scoring -->
          <article class="panel">
            <div class="panel__header"><TrendingUp :size="18" /><h2>Puntuación</h2></div>
            <p class="muted" style="font-size:0.8rem;margin-bottom:12px">
              El scoring se calcula automáticamente al guardar un resultado o al sincronizar. Úsalo si los puntos están desactualizados.
            </p>
            <button
              class="btn btn--outline"
              style="width:100%"
              type="button"
              @click="fetchWithAuth(`${API_BASE_URL}/admin/scoring/recalculate`, { method: 'POST' }).then(() => loadDashboard())"
            >
              <RefreshCw :size="14" /> Recalcular puntos
            </button>
          </article>

          <!-- Configuración porra -->
          <article class="panel admin-wide-panel">
            <div class="panel__header"><Settings :size="18" /><h2>Configuración de la porra</h2></div>
            <div class="config-grid">
              <div class="config-item"><span>Inscripción</span><strong>10 €</strong></div>
              <div class="config-item"><span>Pleno activo</span><strong class="text-green">Sí</strong></div>
              <div class="config-item"><span>Bonus apuesta inicial</span><strong class="text-green">Sí</strong></div>
              <div class="config-item"><span>Predicciones visibles</span><strong class="text-muted">Al cerrar fase</strong></div>
            </div>
          </article>

          <!-- ★ API-Football Sync Panel ★ -->
          <article class="panel api-sync-panel">
            <div class="panel__header">
              <component
                :is="apiSync.status === 'ok' ? Wifi : apiSync.status === 'syncing' ? Activity : WifiOff"
                :size="18"
                :class="['sync-status-icon', `sync-status-icon--${apiSync.status}`]"
              />
              <h2>API-Football · Sincronización</h2>
              <span :class="['badge', apiSync.status === 'ok' ? 'badge--active' : apiSync.status === 'syncing' ? 'badge--warning' : 'badge--closed']">
                {{ apiSync.status === 'ok' ? 'Conectado' : apiSync.status === 'syncing' ? 'Sincronizando…' : 'Error' }}
              </span>
            </div>

            <!-- Fuente activa -->
            <div class="source-toggle">
              <span class="source-toggle__label">Fuente de datos activa</span>
              <div class="source-toggle__btns">
                <button
                  :class="['btn btn--sm source-btn', apiSync.activeSource === 'WC26_IR' ? 'source-btn--active' : '']"
                  type="button"
                  @click="setActiveSource('WC26_IR')"
                >
                  🌍 worldcup26.ir <span class="badge badge--active" style="font-size:0.65rem;padding:1px 5px">🆓</span>
                </button>
                <button
                  :class="['btn btn--sm source-btn', apiSync.activeSource === 'API_FOOTBALL' ? 'source-btn--active' : '']"
                  type="button"
                  @click="setActiveSource('API_FOOTBALL')"
                >
                  ⚽ API-Football
                </button>
              </div>
            </div>

            <!-- Meta info -->
            <div class="api-meta-grid">
              <div class="api-meta-item">
                <span>Proveedor</span>
                <strong>{{ apiSync.provider }}</strong>
              </div>
              <div class="api-meta-item">
                <span>Liga / Temporada</span>
                <strong>{{ apiSync.league }} / {{ apiSync.season }}</strong>
              </div>
              <div class="api-meta-item">
                <span>Modo</span>
                <strong class="text-green">{{ apiSync.mode }}</strong>
              </div>
              <div class="api-meta-item">
                <span>Cadencia</span>
                <strong>{{ apiSync.cadence }}</strong>
              </div>
              <div class="api-meta-item">
                <span>Límite diario</span>
                <strong>{{ apiSync.dailyLimit }}</strong>
              </div>
              <div class="api-meta-item">
                <span>Última sincronía</span>
                <strong class="text-green">{{ apiSync.lastSync }}</strong>
              </div>
              <div class="api-meta-item">
                <span>Partidos en directo</span>
                <strong :class="apiSync.liveMatches > 0 ? 'text-green' : ''">
                  {{ apiSync.liveMatches }}
                  <span v-if="apiSync.liveMatches > 0" class="pulse-dot pulse-dot--inline"></span>
                </strong>
              </div>
            </div>

            <!-- Jobs API-Football -->
            <p class="api-section-label">API-Football <span class="badge badge--closed" style="font-size:0.7rem;padding:1px 7px">plan de pago para 2026</span></p>
            <div class="sync-jobs-grid">
              <div
                v-for="job in apiSync.jobs.filter(j => j.provider === 'api-football')"
                :key="job.key"
                :data-key="job.key"
                :class="['sync-card', `sync-card--${job.status}`]"
              >
                <div class="sync-card__info">
                  <strong>{{ job.label }}</strong>
                  <small>{{ job.lastRun }}</small>
                </div>
                <button
                  class="btn btn--sm btn--sync"
                  type="button"
                  :aria-label="`Lanzar ${job.label}`"
                  @click="triggerSyncAnimation(job.key)"
                >
                  <RefreshCw :size="13" :class="{ 'spin': apiSync.status === 'syncing' }" />
                  Sync
                </button>
              </div>
            </div>

            <!-- Jobs worldcup26.ir -->
            <div class="api-section-header">
              <p class="api-section-label">worldcup26.ir <span class="badge badge--active" style="font-size:0.7rem;padding:1px 7px">🆓 sin key · datos reales 2026</span></p>
              <button
                class="btn btn--sm btn--sync btn--wc26-all"
                type="button"
                @click="triggerSyncAnimation('WC26_ALL')"
              >
                <RefreshCw :size="13" />
                Sync todo WC26
              </button>
            </div>
            <div class="sync-jobs-grid">
              <div
                v-for="job in apiSync.jobs.filter(j => j.provider === 'wc26')"
                :key="job.key"
                :data-key="job.key"
                :class="['sync-card', `sync-card--${job.status}`]"
              >
                <div class="sync-card__info">
                  <strong>{{ job.label }}</strong>
                  <small>{{ job.lastRun }}</small>
                </div>
                <button
                  class="btn btn--sm btn--sync"
                  type="button"
                  :aria-label="`Lanzar ${job.label}`"
                  @click="triggerSyncAnimation(job.key)"
                >
                  <RefreshCw :size="13" :class="{ 'spin': apiSync.status === 'syncing' }" />
                  Sync
                </button>
              </div>
            </div>

            <p class="muted api-note">
              La clave de API-Football vive solo en Spring Boot. worldcup26.ir no requiere key — datos reales del Mundial 2026 en tiempo real.
            </p>
          </article>

        </div>
      </template>

    </div><!-- /tab-content -->

    <!-- ═══════════════ NAV INFERIOR ═══════════════ -->
    <nav class="bottom-nav" aria-label="Navegación principal">
      <button
        v-for="tab in tabs"
        :key="tab.id"
        :class="['nav-item', { active: activeTab === tab.id }]"
        type="button"
        @click="activeTab = tab.id"
      >
        <component :is="tab.icon" :size="20" />
        <span>{{ tab.label }}</span>
      </button>
    </nav>

  </main>

  <!-- ═══════════════ MODAL PREDICCIÓN ═══════════════ -->
  <Teleport to="body">
    <div v-if="predModal.open" class="pred-backdrop" @click.self="predModal.open = false">
      <div class="pred-modal" role="dialog" aria-modal="true">

        <!-- Header -->
        <div class="pred-modal__header">
          <div class="pred-modal__teams">
            <span :class="`fi fi-${predModal.homeFl} flag-lg`"></span>
            <strong>{{ predModal.home }}</strong>
            <span class="pred-modal__vs">vs</span>
            <strong>{{ predModal.away }}</strong>
            <span :class="`fi fi-${predModal.awayFl} flag-lg`"></span>
          </div>
          <button class="pred-modal__close" type="button" @click="predModal.open = false">✕</button>
        </div>

        <!-- Score inputs -->
        <div class="pred-modal__score">
          <div class="pred-score-block">
            <span :class="`fi fi-${predModal.homeFl} flag-sm`"></span>
            <label>{{ predModal.home }}</label>
            <div class="pred-stepper">
              <button type="button" @click="predModal.homeGoals = Math.max(0, predModal.homeGoals - 1)">−</button>
              <span class="pred-stepper__val">{{ predModal.homeGoals }}</span>
              <button type="button" @click="predModal.homeGoals = Math.min(3, predModal.homeGoals + 1)">+</button>
            </div>
          </div>
          <span class="pred-dash">—</span>
          <div class="pred-score-block">
            <span :class="`fi fi-${predModal.awayFl} flag-sm`"></span>
            <label>{{ predModal.away }}</label>
            <div class="pred-stepper">
              <button type="button" @click="predModal.awayGoals = Math.max(0, predModal.awayGoals - 1)">−</button>
              <span class="pred-stepper__val">{{ predModal.awayGoals }}</span>
              <button type="button" @click="predModal.awayGoals = Math.min(3, predModal.awayGoals + 1)">+</button>
            </div>
          </div>
        </div>

        <!-- AI badge when predicted -->
        <p v-if="predModal.aiSource" class="pred-ai-badge">
          <Sparkles :size="12" />
          {{ predModal.aiSource.startsWith('gpt') ? 'Predicción generada por ChatGPT' : 'Predicción aleatoria (IA no disponible)' }}
        </p>

        <!-- Actions -->
        <div class="pred-modal__actions">
          <button
            class="btn btn--outline btn--sm pred-btn-ai"
            type="button"
            :disabled="predModal.aiLoading"
            @click="askAiPrediction"
          >
            <Sparkles :size="13" :class="{ 'spin': predModal.aiLoading }" />
            {{ predModal.aiLoading ? 'Preguntando a la IA…' : 'Predecir con IA' }}
          </button>
          <button class="btn btn--primary" type="button" @click="savePrediction">
            <Check :size="14" /> Guardar predicción
          </button>
        </div>

      </div>
    </div>
  </Teleport>

  <!-- ═══════════════ MODAL INVITAR ═══════════════ -->
  <Teleport to="body">
    <div v-if="inviteModal.open" class="pred-backdrop" @click.self="inviteModal.open = false">
      <div class="pred-modal" role="dialog" aria-modal="true" style="max-width:360px">
        <div class="pred-modal__header">
          <div class="pred-modal__teams"><Users :size="18" /><strong>Añadir participante</strong></div>
          <button class="pred-modal__close" type="button" @click="inviteModal.open = false">✕</button>
        </div>
        <div class="invite-form">
          <label class="invite-label">Nombre</label>
          <input
            v-model="inviteModal.name"
            class="invite-input"
            type="text"
            placeholder="Diego García"
            autocomplete="name"
          />
          <label class="invite-label">Email</label>
          <input
            v-model="inviteModal.email"
            class="invite-input"
            type="email"
            placeholder="diego@ejemplo.com"
            autocomplete="email"
          />
          <p v-if="inviteModal.error" class="invite-error">{{ inviteModal.error }}</p>
        </div>
        <div class="pred-modal__actions">
          <button class="btn btn--outline btn--sm" type="button" @click="inviteModal.open = false">Cancelar</button>
          <button
            class="btn btn--primary"
            type="button"
            :disabled="inviteModal.loading || !inviteModal.name || !inviteModal.email"
            @click="addMember"
          >
            <Check :size="14" /> {{ inviteModal.loading ? 'Añadiendo…' : 'Añadir' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>

</template>
