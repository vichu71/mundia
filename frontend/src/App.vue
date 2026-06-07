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
type TabId = 'home' | 'matches' | 'bracket' | 'ranking' | 'prizes' | 'admin'

type Pool = { id: number; name: string; code: string; status: string; statusType: string; userRole: string; members: number; paid: number; pot: number }
type Match = { id: number; home: string; away: string; homeFl: string; awayFl: string; pred: string; real: string; points: number | null; status: string; statusType: string; note: string; kickoff: string | null; source: string | null; roundName: string | null; stage: string | null }
type RankingRow = { pos: number; name: string; avatar: string; points: number; exact: number; winners: number; prize: number; delta: string; alive: boolean }
type InitialRankingRow = { pos: number; name: string; points: number; exact: number; winners: number; bonus: string }
type PrizeRow = { label: string; amount: number; state: string; stateType: string; contenders: number; pct: number }
type BracketMatch = { home: string; homeFl: string; away: string; awayFl: string; pred: string; real: string; winner: string; done: boolean }
type TeamStanding = { name: string; flag: string; played: number; won: number; drawn: number; lost: number; gf: number; ga: number; points: number }
type GroupStanding = { roundName: string; teams: TeamStanding[] }
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
    body: JSON.stringify({ credential: response.credential, accessCode: emailForm.value.accessCode || null }),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    emailForm.value.error = err.message ?? 'Error al entrar con Google'
    return
  }
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
  email: '', password: '', displayName: '', inviteCode: '', accessCode: '',
  loading: false, error: '',
})

async function emailLogin() {
  emailForm.value.loading = true
  emailForm.value.error = ''
  try {
    const res = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: emailForm.value.email, password: emailForm.value.password, accessCode: emailForm.value.accessCode || null }),
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
        accessCode: emailForm.value.accessCode || null,
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
const showHelp    = ref(false)

// ─── Simulator ───────────────────────────────────────────────────────────────
type SimDayInfo = { day: number; date: string; matches: number; done: boolean }
type SimStatus  = { simDay: number; currentDate: string | null; totalDays: number; simUsers: number; days: SimDayInfo[] }

const sim = ref<SimStatus | null>(null)
const simLoading = ref(false)
const simUserCount = ref(5)

async function loadSimStatus() {
  if (!activePoolId.value) return
  const res = await fetchWithAuth(`${API_BASE_URL}/admin/sim/status/${activePoolId.value}`)
  if (res.ok) sim.value = await res.json()
}

async function simCreateUsers() {
  simLoading.value = true
  try {
    await fetchWithAuth(`${API_BASE_URL}/admin/sim/users/${activePoolId.value}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ count: simUserCount.value }),
    })
    await Promise.all([loadSimStatus(), loadDashboard(), loadMembers()])
    membersLoaded.value = true
  } finally { simLoading.value = false }
}

async function simAdvanceDay() {
  simLoading.value = true
  try {
    await fetchWithAuth(`${API_BASE_URL}/admin/sim/advance/${activePoolId.value}`, { method: 'POST' })
    await Promise.all([loadSimStatus(), loadDashboard(), loadInitialBetStatus()])
  } finally { simLoading.value = false }
}

async function simReset() {
  if (!confirm('¿Borrar toda la simulación? Se eliminarán usuarios simulados y todos los resultados.')) return
  simLoading.value = true
  try {
    await fetchWithAuth(`${API_BASE_URL}/admin/sim/reset/${activePoolId.value}`, { method: 'DELETE' })
    await Promise.all([loadSimStatus(), loadDashboard(), loadMembers()])
    membersLoaded.value = true
  } finally { simLoading.value = false }
}

async function simFullTournament() {
  if (!confirm('¿Simular el torneo completo hasta la Final? Esto avanzará todos los días y generará todas las eliminatorias.')) return
  simLoading.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/sim/full/${activePoolId.value}`, { method: 'POST' })
    if (!res.ok) { alert('Error en simulación completa'); return }
    await Promise.all([loadSimStatus(), loadDashboard(), loadMembers(), loadInitialBetStatus()])
    membersLoaded.value = true
    activeTab.value = 'bracket'
  } finally { simLoading.value = false }
}

async function simGenRound32() {
  simLoading.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/sim/knockout/round32/${activePoolId.value}`, { method: 'POST' })
    if (!res.ok) { const e = await res.json().catch(() => ({})); alert(e.detail ?? 'Error generando Round of 32'); return }
    await Promise.all([loadSimStatus(), loadDashboard()])
    activeTab.value = 'bracket'
  } finally { simLoading.value = false }
}

const KNOCKOUT_ROUNDS = [
  { current: 'Round of 32',   next: 'Round of 16',   sortOrder: 11, dates: ['2026-07-10 18:00','2026-07-10 21:00','2026-07-11 18:00','2026-07-11 21:00','2026-07-12 18:00','2026-07-12 21:00','2026-07-13 18:00','2026-07-13 21:00'] },
  { current: 'Round of 16',   next: 'Quarter-finals', sortOrder: 12, dates: ['2026-07-17 18:00','2026-07-17 21:00','2026-07-18 18:00','2026-07-18 21:00'] },
  { current: 'Quarter-finals',next: 'Semi-finals',    sortOrder: 13, dates: ['2026-07-21 21:00','2026-07-22 21:00'] },
  { current: 'Semi-finals',   next: 'Final',          sortOrder: 14, dates: ['2026-07-25 21:00'] },
]

async function simGenNextRound(round: typeof KNOCKOUT_ROUNDS[0]) {
  simLoading.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/admin/sim/knockout/next/${activePoolId.value}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ currentRound: round.current, nextRound: round.next, sortOrder: String(round.sortOrder), dates: round.dates.join(',') }),
    })
    if (!res.ok) { const e = await res.json().catch(() => ({})); alert(e.detail ?? 'Error generando ronda'); return }
    await Promise.all([loadSimStatus(), loadDashboard()])
    activeTab.value = 'bracket'
  } finally { simLoading.value = false }
}

// ──────────────────────────────────────────
//  DATA
// ──────────────────────────────────────────
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

const pools = ref<Pool[]>([])

const EMPTY_POOL: Pool = { id: 0, name: '', code: '', status: '', statusType: '', userRole: '', members: 0, paid: 0, pot: 0 }
const selectedPool   = computed(() => pools.value.find((p) => p.id === activePool.value) ?? pools.value[0] ?? EMPTY_POOL)
const nextMatch      = computed(() =>
  matches.value.find(m => m.statusType === 'open' || m.statusType === 'warning') ?? matches.value[0] ?? null
)
const myRanking      = computed(() =>
  ranking.value.find(r => r.name === currentUser.value?.name) ?? ranking.value[0] ?? null
)
const pendingCount   = computed(() =>
  matches.value.filter(m => (m.statusType === 'open' || m.statusType === 'warning') && m.pred === '? - ?').length
)
const champion = computed(() => {
  const final = bracketRounds.value.find(r => r.name === 'Final')?.matches[0]
  if (!final || !final.done) return null
  // winner field holds the team name; match its flag from home/away
  if (final.winner === final.home) return { name: final.home, flag: final.homeFl }
  if (final.winner === final.away) return { name: final.away, flag: final.awayFl }
  return { name: final.winner, flag: 'un' }
})

const matches = ref<Match[]>([
  { id: 1, home: 'Espana',    away: 'Alemania',  homeFl: 'es',     awayFl: 'de',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Partido de prueba.', kickoff: null, source: null, roundName: 'Group A', stage: 'GROUP_STAGE' },
  { id: 2, home: 'Brasil',    away: 'Portugal',  homeFl: 'br',     awayFl: 'pt',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Pendiente de sincronizar.', kickoff: null, source: null, roundName: 'Group A', stage: 'GROUP_STAGE' },
  { id: 3, home: 'Argentina', away: 'Francia',   homeFl: 'ar',     awayFl: 'fr',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Editable.', kickoff: null, source: null, roundName: 'Group B', stage: 'GROUP_STAGE' },
  { id: 4, home: 'Inglaterra',away: 'Italia',    homeFl: 'gb-eng', awayFl: 'it',     pred: '0 - 0', real: 'Pend.', points: null, status: 'Abierto', statusType: 'open', note: 'Sin puntos todavia.', kickoff: null, source: null, roundName: 'Group B', stage: 'GROUP_STAGE' },
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
const groupStandings = ref<GroupStanding[]>([])

// ─── Apuesta inicial ──────────────────────────────────────────────────────────
const initialBetStatus  = ref<{ hasInitialBet: boolean; predictedMatches: number } | null>(null)
const showInitialBet    = ref(false)
const initialPreds      = ref<Record<number, { home: number; away: number }>>({})
const initialBetSaving  = ref(false)
const initialBetSaved   = ref(false)

async function loadInitialBetStatus() {
  if (!activePoolId.value) return
  const res = await fetchWithAuth(`${API_BASE_URL}/predictions/initial/status/${activePoolId.value}`)
  if (res.ok) initialBetStatus.value = await res.json()
}

function openInitialBet() {
  // Pre-fill with current LIVE predictions
  matches.value.forEach(m => {
    const parts = m.pred.replace(/\s/g, '').split('-')
    initialPreds.value[m.id] = {
      home: parseInt(parts[0] ?? '0') || 0,
      away: parseInt(parts[1] ?? '0') || 0,
    }
  })
  showInitialBet.value = true
}

async function saveInitialBet() {
  initialBetSaving.value = true
  try {
    for (const [matchIdStr, pred] of Object.entries(initialPreds.value)) {
      await fetchWithAuth(`${API_BASE_URL}/predictions/initial`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          poolId: activePoolId.value,
          matchId: parseInt(matchIdStr),
          homeGoals: pred.home,
          awayGoals: pred.away,
        }),
      })
    }
    initialBetSaved.value = true
    await loadInitialBetStatus()
    setTimeout(() => { showInitialBet.value = false; initialBetSaved.value = false }, 1500)
  } finally {
    initialBetSaving.value = false
  }
}

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

async function deletePool() {
  const pool = selectedPool.value
  if (!confirm(`¿Eliminar la porra "${pool.name}"? Dejará de mostrarse para todos los participantes.`)) return
  const res = await fetchWithAuth(`${API_BASE_URL}/admin/pools/${activePoolId.value}`, { method: 'DELETE' })
  if (!res.ok) { alert('No se pudo eliminar la porra'); return }
  await loadMe()
  activePool.value = userPools.value[0]?.id ?? null
  if (userPools.value.length > 0) await loadRemoteState()
  activeTab.value = 'home'
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
  aiReasoning: string | null
}
const predModal = ref<PredModal>({
  open: false, matchId: null,
  home: '', away: '', homeFl: 'un', awayFl: 'un',
  homeGoals: 0, awayGoals: 0,
  aiLoading: false, aiSource: null, aiReasoning: null,
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
    aiLoading: false, aiSource: null, aiReasoning: null,
  }
}

async function askAiPrediction() {
  if (!predModal.value.matchId) return
  predModal.value.aiLoading = true
  predModal.value.aiSource = null
  predModal.value.aiReasoning = null
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/ai/${predModal.value.matchId}`, { method: 'POST' })
    const data = await res.json()
    if (data.error) throw new Error(data.error)
    predModal.value.homeGoals = data.homeGoals
    predModal.value.awayGoals = data.awayGoals
    predModal.value.aiSource = data.source
    predModal.value.aiReasoning = data.reasoning ?? null
  } catch (e) {
    console.error('AI prediction failed', e)
  } finally {
    predModal.value.aiLoading = false
  }
}

// ─── Grupos colapsables ──────────────────────────────────────────────────────
const collapsedGroups = ref<Set<string>>(new Set())

type MatchGroup = { roundName: string; stage: string; matches: Match[]; predicted: number }

const matchGroups = computed<MatchGroup[]>(() => {
  const map = new Map<string, MatchGroup>()
  for (const m of matches.value) {
    const key = m.roundName ?? 'Sin grupo'
    if (!map.has(key)) {
      map.set(key, { roundName: key, stage: m.stage ?? 'GROUP_STAGE', matches: [], predicted: 0 })
    }
    const g = map.get(key)!
    g.matches.push(m)
    if (m.pred !== '? - ?') g.predicted++
  }
  return Array.from(map.values())
})

function getStandings(roundName: string): TeamStanding[] {
  return groupStandings.value.find(g => g.roundName === roundName)?.teams ?? []
}

function toggleGroup(roundName: string) {
  if (collapsedGroups.value.has(roundName)) {
    collapsedGroups.value.delete(roundName)
  } else {
    collapsedGroups.value.add(roundName)
  }
  // trigger reactivity
  collapsedGroups.value = new Set(collapsedGroups.value)
}

function isCollapsed(roundName: string) {
  return collapsedGroups.value.has(roundName)
}

// Colapsar todos los grupos de fase de grupos por defecto al cargar partidos
watch(matches, (newMatches) => {
  if (newMatches.length > 0 && collapsedGroups.value.size === 0) {
    const groups = new Set<string>()
    for (const m of newMatches) {
      if (m.stage === 'GROUP_STAGE' && m.roundName) groups.add(m.roundName)
    }
    collapsedGroups.value = groups
  }
}, { once: true })

const predModalEl = ref<HTMLElement | null>(null)

function focusTrap(e: KeyboardEvent, _containerClass: string) {
  const container = (e.currentTarget as HTMLElement)
  const focusable = Array.from(container.querySelectorAll<HTMLElement>(
    'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
  ))
  if (!focusable.length) return
  const first = focusable[0]
  const last  = focusable[focusable.length - 1]
  if (e.shiftKey) {
    if (document.activeElement === first) { last.focus(); }
    else { focusable[focusable.indexOf(document.activeElement as HTMLElement) - 1]?.focus() }
  } else {
    if (document.activeElement === last) { first.focus(); }
    else { focusable[focusable.indexOf(document.activeElement as HTMLElement) + 1]?.focus() }
  }
}

// Auto-focus al primer botón al abrir el modal
watch(() => predModal.value.open, (open) => {
  if (open) nextTick(() => predModalEl.value?.querySelector<HTMLElement>('button')?.focus())
})

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
  { id: 'bracket' as const, label: 'Cuadro',   icon: Crown,        adminOnly: false },
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
  groupStandings.value = (data.groupStandings ?? []).map((g: any) => ({
    roundName: g.roundName,
    teams: g.teams.map((t: any) => ({
      name: t.name, flag: t.flag,
      played: t.played, won: t.won, drawn: t.drawn, lost: t.lost,
      gf: t.gf, ga: t.ga, points: t.points,
    })),
  }))
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

const CUTOFF_MINUTES = 60
const now = ref(Date.now())
onMounted(() => { setInterval(() => { now.value = Date.now() }, 30_000) })

function cutoffMs(iso: string | null): number | null {
  if (!iso) return null
  return new Date(iso).getTime() - CUTOFF_MINUTES * 60_000
}

function isPredictionClosed(iso: string | null): boolean {
  const c = cutoffMs(iso)
  return c !== null && now.value >= c
}

function fmtCountdown(iso: string | null): string | null {
  const c = cutoffMs(iso)
  if (c === null) return null
  const diff = c - now.value
  if (diff <= 0) return 'Cerrado'
  if (diff > 24 * 3600_000) return null  // más de 24h, no mostrar
  const h = Math.floor(diff / 3600_000)
  const m = Math.floor((diff % 3600_000) / 60_000)
  if (h > 0) return `Cierra en ${h}h ${m}min`
  return `Cierra en ${m}min`
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
    await Promise.all([loadDashboard(), loadApiSyncStatus(), loadInitialBetStatus()])
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

  // Refresca el dashboard cada 5 minutos si la app está abierta
  setInterval(async () => {
    if (authToken.value && activePoolId.value) {
      await loadDashboard()
    }
  }, 5 * 60 * 1000)
})

watch(activeTab, async (tab) => {
  animateTabIn()
  if (tab === 'home') await loadDashboard()
  if (tab === 'admin') await Promise.all([loadMembers(), loadSimStatus()])
})

watch(activePool, async () => {
  animatePoolSwitch()
  await loadDashboard()
})
</script>

<template>

  <!-- ═══════════════ LOGIN ═══════════════ -->
  <div v-if="!authToken" class="login-screen">
    <div class="login-card" style="max-width:400px">
      <img src="/logo.png" class="login-logo" alt="Mundia" />
      <h1>Mundia</h1>
      <p class="login-sub">Porra familiar · Mundial 2026</p>

      <!-- Tabs -->
      <div class="auth-tabs">
        <button :class="['auth-tab', { active: authMode === 'google' }]" @click="authMode = 'google'">Google</button>
        <button :class="['auth-tab', { active: authMode === 'login' }]" @click="authMode = 'login'">Email</button>
        <button :class="['auth-tab', { active: authMode === 'register' }]" @click="authMode = 'register'">Registrarse</button>
      </div>

      <!-- Código de acceso global (campo compartido por todos los tabs) -->
      <div class="invite-form" style="width:100%;text-align:left;margin-bottom:4px">
        <label class="invite-label">🔑 Código de acceso</label>
        <input v-model="emailForm.accessCode" class="invite-input" type="password" placeholder="••••••••" autocomplete="off" />
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
      <img src="/logo.png" class="login-logo" alt="Mundia" />
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
      <img src="/logo.png" class="login-logo" alt="Mundia" />
      <p class="login-sub">Cargando…</p>
    </div>
  </div>

  <main v-else class="app-shell">

    <!-- ═══════════════ TOPBAR ═══════════════ -->
    <header class="topbar">
      <!-- Fila 1: brand + user -->
      <div class="topbar-row topbar-row--main">
        <div class="brand">
          <img src="/logo.png" class="brand-logo" alt="Mundia" />
          <div>
            <p class="eyebrow">Porra familiar</p>
            <h1>Mundia</h1>
          </div>
        </div>
        <div class="user-chip">
          <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" class="user-avatar" :alt="currentUser.name" />
          <span v-else class="user-avatar user-avatar--initials">{{ currentUser ? currentUser.name.slice(0,2).toUpperCase() : '?' }}</span>
          <span class="user-name">{{ currentUser?.name }}</span>
          <button class="btn btn--ghost btn--xs" type="button" @click="signOut" title="Cerrar sesión">✕</button>
        </div>
      </div>

      <!-- Fila 2: selector de porra -->
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
        <template v-if="nextMatch">
          <div class="next-match-card__duel">
            <div class="team-block">
              <span :class="`fi fi-${nextMatch.homeFl} flag-xl`"></span>
              <strong>{{ nextMatch.home }}</strong>
            </div>
            <div class="vs-block">
              <span class="vs-label">VS</span>
              <span class="match-time">{{ fmtKickoff(nextMatch.kickoff) }}</span>
            </div>
            <div class="team-block">
              <span :class="`fi fi-${nextMatch.awayFl} flag-xl`"></span>
              <strong>{{ nextMatch.away }}</strong>
            </div>
          </div>
          <div class="next-match-card__pred">
            <span>Tu predicción</span>
            <strong>{{ nextMatch.pred }}</strong>
          </div>
          <button class="btn btn--primary" type="button" @click="openPredModal(nextMatch)">
            <Pencil :size="15" /> Editar predicción
          </button>
        </template>
        <p v-else class="muted">Sin partidos pendientes</p>
      </div>
    </section>

    <!-- ═══════════════ CONTENIDO POR TAB ═══════════════ -->
    <div class="tab-content">

      <!-- ─── INICIO ─── -->
      <template v-if="activeTab === 'home'">

        <!-- Banner alerta partidos que cierran hoy sin predecir -->
        <div v-if="pendingCount > 0 && matches.some(m => fmtCountdown(m.kickoff) !== null && m.pred === '? - ?')" class="home-alert-banner">
          <CircleAlert :size="18" />
          <span>
            Tienes <strong>{{ matches.filter(m => fmtCountdown(m.kickoff) !== null && m.pred === '? - ?').length }}</strong>
            partido{{ matches.filter(m => fmtCountdown(m.kickoff) !== null && m.pred === '? - ?').length > 1 ? 's' : '' }}
            que cierran hoy sin predecir
          </span>
          <button class="btn btn--sm btn--primary" type="button" @click="activeTab = 'matches'">
            Predecir ahora
          </button>
        </div>

        <!-- Banner apuesta inicial -->
        <div v-if="initialBetStatus && !initialBetStatus.hasInitialBet" class="initial-bet-banner">
          <Star :size="18" />
          <div class="initial-bet-banner__text">
            <strong>Haz tu apuesta inicial</strong>
            <span>Predice todo el torneo antes de que empiece. Esta predicción es inmutable y genera su propio ranking.</span>
          </div>
          <button class="btn btn--primary btn--sm" type="button" @click="openInitialBet">
            Hacer apuesta
          </button>
        </div>
        <div v-else-if="initialBetStatus?.hasInitialBet" class="initial-bet-banner initial-bet-banner--done">
          <Check :size="18" />
          <div class="initial-bet-banner__text">
            <strong>Apuesta inicial guardada</strong>
            <span>{{ initialBetStatus.predictedMatches }} partidos predichos · inmutable</span>
          </div>
          <button class="btn btn--ghost btn--sm" type="button" @click="openInitialBet">
            Ver
          </button>
        </div>

        <!-- Stat banner -->
        <div class="home-banner">
          <div class="home-banner__stat">
            <span class="home-banner__label">Tu posición</span>
            <strong class="home-banner__val home-banner__val--pos">{{ myRanking ? posLabel(myRanking.pos) : '—' }}</strong>
          </div>
          <div class="home-banner__divider"></div>
          <div class="home-banner__stat">
            <span class="home-banner__label">Puntos</span>
            <strong class="home-banner__val">{{ myRanking?.points ?? 0 }}</strong>
          </div>
          <div class="home-banner__divider"></div>
          <div class="home-banner__stat">
            <span class="home-banner__label">Tu premio</span>
            <strong :class="['home-banner__val', myRanking && myRanking.prize > 0 ? 'home-banner__val--gold' : '']">
              {{ myRanking && myRanking.prize > 0 ? myRanking.prize + ' €' : '—' }}
            </strong>
          </div>
          <div class="home-banner__divider"></div>
          <div class="home-banner__stat">
            <span class="home-banner__label">Bote</span>
            <strong class="home-banner__val home-banner__val--gold">{{ selectedPool.pot }} €</strong>
          </div>
          <div class="home-banner__divider"></div>
          <div class="home-banner__stat">
            <span class="home-banner__label">Participantes</span>
            <strong class="home-banner__val">{{ selectedPool.paid }}/{{ selectedPool.members }}</strong>
          </div>
        </div>

        <!-- Dos tarjetas acción -->
        <div class="home-actions">

          <!-- Próximo partido -->
          <article class="panel home-action-card" v-if="nextMatch">
            <div class="panel__header">
              <Zap :size="16" />
              <h2>Próximo partido</h2>
              <span class="match-kickoff" style="margin-left:auto">{{ fmtKickoff(nextMatch.kickoff) }}</span>
            </div>
            <div class="home-match-duel">
              <div class="team-block">
                <span :class="`fi fi-${nextMatch.homeFl} flag-xl`"></span>
                <strong>{{ nextMatch.home }}</strong>
              </div>
              <div class="vs-block">
                <span class="vs-label">VS</span>
                <span :class="['badge', `badge--${nextMatch.statusType}`]">{{ nextMatch.status }}</span>
              </div>
              <div class="team-block">
                <span :class="`fi fi-${nextMatch.awayFl} flag-xl`"></span>
                <strong>{{ nextMatch.away }}</strong>
              </div>
            </div>
            <div class="next-match-card__pred">
              <span>Tu predicción</span>
              <strong>{{ nextMatch.pred }}</strong>
            </div>
            <button class="btn btn--primary" type="button" @click="openPredModal(nextMatch)">
              <Pencil :size="14" /> Editar predicción
            </button>
          </article>

          <!-- Pendientes -->
          <article class="panel home-action-card home-action-card--pending">
            <div class="panel__header">
              <CalendarDays :size="16" />
              <h2>Predicciones</h2>
            </div>
            <div class="home-pending">
              <div v-if="pendingCount > 0" class="home-pending__alert">
                <CircleAlert :size="28" class="home-pending__icon" />
                <p class="home-pending__num">{{ pendingCount }}</p>
                <p class="home-pending__label">partidos sin<br>predecir</p>
              </div>
              <div v-else class="home-pending__ok">
                <Check :size="28" class="home-pending__icon--ok" />
                <p class="home-pending__label">¡Todo predicho!</p>
              </div>
            </div>
            <button class="btn btn--outline" type="button" @click="activeTab = 'matches'">
              <CalendarDays :size="14" /> Ver partidos
            </button>
          </article>
        </div>

        <!-- Top 3 ranking -->
        <article class="panel home-ranking-panel">
          <div class="panel__header">
            <Trophy :size="16" />
            <h2>Clasificación</h2>
            <button class="btn btn--ghost btn--sm" style="margin-left:auto" type="button" @click="activeTab = 'ranking'">
              Ver todo <ChevronRight :size="13" />
            </button>
          </div>
          <div class="ranking-table">
            <div
              v-for="p in ranking.slice(0, 5)"
              :key="p.name"
              :class="['ranking-row', { 'ranking-row--alive': p.alive }, { 'ranking-row--me': p.name === currentUser?.name }]"
            >
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
            </div>
          </div>
        </article>

      </template>

      <!-- ─── PARTIDOS ─── -->
      <template v-if="activeTab === 'matches'">

        <!-- Toolbar -->
        <div class="matches-toolbar">
          <p class="muted">{{ matches.length }} partidos · {{ matches.filter(m => m.pred !== '? - ?').length }} predichos</p>
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

        <!-- Grupos colapsables -->
        <div class="match-groups">
          <section v-for="group in matchGroups" :key="group.roundName" class="match-group">

            <!-- Cabecera del grupo -->
            <button
              class="match-group__header"
              type="button"
              @click="toggleGroup(group.roundName)"
            >
              <span class="match-group__chevron" :class="{ 'match-group__chevron--open': !isCollapsed(group.roundName) }">
                <ChevronRight :size="15" />
              </span>
              <span class="match-group__name">{{ group.roundName }}</span>
              <span class="match-group__badge" :class="group.stage === 'GROUP_STAGE' ? 'badge--draft' : 'badge--active'">
                {{ group.stage === 'GROUP_STAGE' ? 'Fase de grupos' : 'Eliminatoria' }}
              </span>
              <span class="match-group__progress">
                {{ group.predicted }}/{{ group.matches.length }}
                <span class="match-group__progress-bar">
                  <span
                    class="match-group__progress-fill"
                    :style="{ width: group.matches.length ? (group.predicted / group.matches.length * 100) + '%' : '0%' }"
                  ></span>
                </span>
              </span>
            </button>

            <!-- Mini clasificación (solo visible cuando colapsado y hay datos) -->
            <div
              v-if="isCollapsed(group.roundName) && getStandings(group.roundName).length > 0"
              class="match-group__standings"
            >
              <div
                v-for="(team, idx) in getStandings(group.roundName)"
                :key="team.name"
                :class="['standing-row', idx < 2 ? 'standing-row--qualify' : '']"
              >
                <span class="standing-pos">{{ idx + 1 }}</span>
                <span :class="`fi fi-${team.flag} flag-xs`"></span>
                <span class="standing-name">{{ team.name }}</span>
                <span class="standing-stats">
                  {{ team.played }}J &nbsp;{{ team.won }}G {{ team.drawn }}E {{ team.lost }}P
                  &nbsp;{{ team.gf }}-{{ team.ga }}
                </span>
                <strong class="standing-pts">{{ team.points }}pts</strong>
              </div>
            </div>

            <!-- Partidos del grupo -->
            <div v-show="!isCollapsed(group.roundName)" class="match-group__body">
              <div class="matches-grid">
                <article v-for="m in group.matches" :key="m.id" class="match-card">
                  <div class="match-card__top">
                    <span :class="['badge', isPredictionClosed(m.kickoff) ? 'badge--closed' : `badge--${m.statusType}`]">
                      {{ isPredictionClosed(m.kickoff) ? 'Cerrado' : m.status }}
                    </span>
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
                  <span v-if="fmtCountdown(m.kickoff)" class="match-countdown">
                    ⏱ {{ fmtCountdown(m.kickoff) }}
                  </span>
                  <button
                    v-if="m.statusType === 'open' || m.statusType === 'warning'"
                    class="btn btn--sm"
                    :class="isPredictionClosed(m.kickoff) ? 'btn--outline' : 'btn--primary'"
                    type="button"
                    :disabled="isPredictionClosed(m.kickoff)"
                    @click="openPredModal(m)"
                  >
                    <Pencil :size="13" /> {{ isPredictionClosed(m.kickoff) ? 'Predicción cerrada' : 'Editar predicción' }}
                  </button>
                </article>
              </div>
            </div>

          </section>
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
              <div v-for="p in ranking" :key="p.name" :class="['ranking-row', { 'ranking-row--alive': p.alive }, { 'ranking-row--me': p.name === currentUser?.name }]">
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
                <div class="ranking-prize-col">
                  <span :class="['ranking-prize-total', p.prize > 0 ? 'ranking-prize-total--won' : '']">
                    {{ p.prize > 0 ? p.prize + ' €' : '—' }}
                  </span>
                  <span v-if="p.alive" class="tag tag--green tag--xs">🔥 Pleno</span>
                </div>
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

      <!-- ─── CUADRO ─── -->
      <template v-if="activeTab === 'bracket'">
        <div class="bracket-tab">

          <!-- Fase de grupos -->
          <div v-if="bracketRounds.filter(r => r.name.startsWith('Group')).length" class="bracket-tab__section">
            <div class="bracket-tab__phase-header">
              <span class="fi fi-un flag-xs"></span>
              <h2>Fase de Grupos</h2>
            </div>
            <div class="bracket-groups-grid">
              <div
                v-for="round in bracketRounds.filter(r => r.name.startsWith('Group'))"
                :key="round.name"
                class="bracket-group-card"
              >
                <p class="bracket-group-card__title">{{ round.name }}</p>
                <div class="bracket-group-card__matches">
                  <div
                    v-for="m in round.matches"
                    :key="`${round.name}-${m.home}`"
                    :class="['bracket-mini-match', { 'bracket-mini-match--done': m.done }]"
                  >
                    <div class="bracket-mini-team">
                      <span :class="`fi fi-${m.homeFl} flag-xs`"></span>
                      <span>{{ m.home }}</span>
                      <strong>{{ m.pred.split('-')[0] }}</strong>
                    </div>
                    <div class="bracket-mini-team">
                      <span :class="`fi fi-${m.awayFl} flag-xs`"></span>
                      <span>{{ m.away }}</span>
                      <strong>{{ m.pred.split('-')[1] }}</strong>
                    </div>
                    <div v-if="m.done" class="bracket-mini-result">
                      {{ m.real }} · <span class="bracket-winner">{{ m.winner }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Eliminatorias -->
          <div v-if="bracketRounds.filter(r => !r.name.startsWith('Group')).length" class="bracket-tab__section">
            <div class="bracket-tab__phase-header">
              <ChevronRight :size="16" />
              <h2>Eliminatorias</h2>
            </div>
            <div class="bracket-knockout">
              <div
                v-for="round in bracketRounds.filter(r => !r.name.startsWith('Group'))"
                :key="round.name"
                class="bracket-knockout__round"
              >
                <p class="bracket-knockout__round-label">{{ round.name }}</p>
                <div class="bracket-knockout__matches">
                  <div
                    v-for="m in round.matches"
                    :key="`${round.name}-${m.home}`"
                    :class="['bracket-ko-card',
                      { 'bracket-ko-card--done': m.done },
                      { 'bracket-ko-card--final': round.name === 'Final' }]"
                  >
                    <div :class="['bracket-ko-team',
                      { 'bracket-ko-team--winner': m.done && m.winner === m.home },
                      { 'bracket-ko-team--champion': round.name === 'Final' && m.done && m.winner === m.home }]">
                      <span :class="`fi fi-${m.homeFl} flag-sm`"></span>
                      <span>{{ m.home }}</span>
                      <span v-if="round.name === 'Final' && m.done && m.winner === m.home" class="champion-crown">👑</span>
                      <strong v-if="m.done">{{ m.real.split('-')[0] }}</strong>
                    </div>
                    <div :class="['bracket-ko-team',
                      { 'bracket-ko-team--winner': m.done && m.winner === m.away },
                      { 'bracket-ko-team--champion': round.name === 'Final' && m.done && m.winner === m.away }]">
                      <span :class="`fi fi-${m.awayFl} flag-sm`"></span>
                      <span>{{ m.away }}</span>
                      <span v-if="round.name === 'Final' && m.done && m.winner === m.away" class="champion-crown">👑</span>
                      <strong v-if="m.done">{{ m.real.split('-')[1] }}</strong>
                    </div>
                    <div v-if="!m.done" class="bracket-ko-pending">Por jugar</div>
                  </div>
                </div>
              </div>

              <!-- Campeón -->
              <div class="bracket-champion">
                <Crown :size="28" />
                <span class="eyebrow">Campeón</span>
                <template v-if="champion">
                  <span :class="`fi fi-${champion.flag} flag-lg`"></span>
                  <strong>{{ champion.name }}</strong>
                </template>
                <template v-else>
                  <span class="fi fi-un flag-lg"></span>
                  <strong>Por decidir</strong>
                </template>
              </div>
            </div>
          </div>

          <!-- Sin datos -->
          <div v-if="!bracketRounds.length" class="bracket-tab__empty">
            <Crown :size="40" style="color:var(--gold);opacity:0.4" />
            <p>El cuadro aparecerá aquí cuando avance el torneo.</p>
          </div>

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
            <div style="margin-top:16px;padding-top:16px;border-top:1px solid var(--border)">
              <button
                class="btn btn--sm"
                style="color:var(--coral);border:1px solid rgba(251,113,133,0.3);background:var(--coral-dim)"
                type="button"
                @click="deletePool"
              >
                🗑 Eliminar esta porra
              </button>
              <p class="muted" style="font-size:0.72rem;margin-top:8px">La porra dejará de mostrarse. Los datos se conservan en la base de datos.</p>
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

          <!-- ★ Simulador ★ -->
          <article class="panel admin-wide-panel sim-panel">
            <div class="panel__header">
              <Zap :size="18" style="color:var(--gold)" />
              <h2>Simulador de torneo</h2>
              <span class="badge badge--warning" style="font-size:0.65rem">DEV</span>
            </div>

            <div v-if="sim" class="sim-body">

              <!-- Setup usuarios -->
              <div class="sim-section">
                <p class="sim-section__label">👥 Usuarios simulados</p>
                <div class="sim-row">
                  <div class="sim-count-btns">
                    <button v-for="n in [3,5,8,10]" :key="n"
                      :class="['btn btn--sm', simUserCount === n ? 'btn--primary' : 'btn--outline']"
                      type="button" @click="simUserCount = n">{{ n }}</button>
                  </div>
                  <button class="btn btn--sm btn--primary" type="button"
                    :disabled="simLoading" @click="simCreateUsers">
                    <Users :size="13" /> Crear usuarios
                  </button>
                  <span class="sim-users-count">
                    {{ sim.simUsers }} activos
                  </span>
                </div>
              </div>

              <!-- Avanzar día -->
              <div class="sim-section">
                <p class="sim-section__label">📅 Progreso del torneo</p>
                <div class="sim-day-bar">
                  <div class="sim-day-bar__fill" :style="{ width: sim.totalDays ? (sim.simDay / sim.totalDays * 100) + '%' : '0%' }"></div>
                </div>
                <div class="sim-row" style="margin-top:8px">
                  <span class="sim-day-info">
                    <strong>Día {{ sim.simDay }}</strong> de {{ sim.totalDays }}
                    <span v-if="sim.currentDate" class="muted"> · {{ sim.currentDate }}</span>
                  </span>
                  <button
                    class="btn btn--primary"
                    type="button"
                    :disabled="simLoading || sim.simDay >= sim.totalDays"
                    @click="simAdvanceDay"
                  >
                    <Zap :size="14" :class="{ spin: simLoading }" />
                    {{ simLoading ? 'Simulando…' : sim.simDay >= sim.totalDays ? 'Completado' : '▶ Simular día ' + (sim.simDay + 1) }}
                  </button>
                </div>
              </div>

              <!-- Timeline de días -->
              <div class="sim-days-scroll">
                <div
                  v-for="d in sim.days"
                  :key="d.day"
                  :class="['sim-day-chip', {
                    'sim-day-chip--done': d.done,
                    'sim-day-chip--current': d.day === sim.simDay + 1
                  }]"
                >
                  <span class="sim-day-chip__day">D{{ d.day }}</span>
                  <span class="sim-day-chip__date">{{ d.date.slice(5) }}</span>
                  <span class="sim-day-chip__matches">{{ d.matches }}p</span>
                </div>
              </div>

              <!-- Simular torneo completo -->
              <div class="sim-section">
                <button
                  class="btn btn--primary"
                  style="width:100%;background:linear-gradient(135deg,var(--gold),var(--purple))"
                  type="button"
                  :disabled="simLoading"
                  @click="simFullTournament"
                >
                  <Zap :size="15" :class="{ spin: simLoading }" />
                  {{ simLoading ? 'Simulando torneo…' : '⚡ Simular torneo completo' }}
                </button>
              </div>

              <!-- Eliminatorias -->
              <div class="sim-section">
                <p class="sim-section__label">🏆 Eliminatorias paso a paso</p>
                <div class="sim-row" style="flex-wrap:wrap">
                  <button
                    class="btn btn--sm btn--outline"
                    type="button"
                    :disabled="simLoading || sim.simDay < sim.totalDays"
                    @click="simGenRound32"
                    :title="sim.simDay < sim.totalDays ? 'Completa la fase de grupos primero' : ''"
                  >
                    ⚡ Generar Round of 32
                  </button>
                  <button
                    v-for="round in KNOCKOUT_ROUNDS"
                    :key="round.next"
                    class="btn btn--sm btn--outline"
                    type="button"
                    :disabled="simLoading"
                    @click="simGenNextRound(round)"
                  >
                    → {{ round.next }}
                  </button>
                </div>
              </div>

              <!-- Reset -->
              <div class="sim-section" style="margin-top:4px">
                <button
                  class="btn btn--sm"
                  style="color:var(--coral);border:1px solid rgba(251,113,133,0.3);background:var(--coral-dim);width:100%"
                  type="button"
                  :disabled="simLoading"
                  @click="simReset"
                >
                  🗑 Borrar simulación completa
                </button>
              </div>

            </div>
            <div v-else class="muted" style="font-size:0.8rem">Cargando estado del simulador…</div>
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
      <button class="nav-item nav-item--help" type="button" @click="showHelp = true" aria-label="Ayuda">
        <span class="help-icon">?</span>
        <span>Ayuda</span>
      </button>
    </nav>

  </main>

  <!-- ═══════════════ MODAL PREDICCIÓN ═══════════════ -->
  <Teleport to="body">
    <div v-if="predModal.open" class="pred-backdrop" @click.self="predModal.open = false" @keydown.esc="predModal.open = false">
      <div class="pred-modal" role="dialog" aria-modal="true" aria-label="Editar predicción" @keydown.tab.prevent="focusTrap($event, 'pred-modal')" ref="predModalEl">

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
              <button type="button" @click="predModal.homeGoals = Math.min(4, predModal.homeGoals + 1)">+</button>
            </div>
          </div>
          <span class="pred-dash">—</span>
          <div class="pred-score-block">
            <span :class="`fi fi-${predModal.awayFl} flag-sm`"></span>
            <label>{{ predModal.away }}</label>
            <div class="pred-stepper">
              <button type="button" @click="predModal.awayGoals = Math.max(0, predModal.awayGoals - 1)">−</button>
              <span class="pred-stepper__val">{{ predModal.awayGoals }}</span>
              <button type="button" @click="predModal.awayGoals = Math.min(4, predModal.awayGoals + 1)">+</button>
            </div>
          </div>
        </div>

        <!-- AI badge when predicted -->
        <div v-if="predModal.aiSource" class="pred-ai-block">
          <p class="pred-ai-badge">
            <Sparkles :size="12" />
            {{ predModal.aiSource.startsWith('gpt') ? 'Predicción generada por ChatGPT' : 'Predicción aleatoria (IA no disponible)' }}
          </p>
          <p v-if="predModal.aiReasoning" class="pred-ai-reasoning">{{ predModal.aiReasoning }}</p>
        </div>

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
    <div v-if="inviteModal.open" class="pred-backdrop" @click.self="inviteModal.open = false" @keydown.esc="inviteModal.open = false">
      <div class="pred-modal" role="dialog" aria-modal="true" aria-label="Añadir participante" style="max-width:360px" @keydown.tab.prevent="focusTrap($event, 'pred-modal')">
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

  <!-- ═══════════════ MODAL APUESTA INICIAL ═══════════════ -->
  <Teleport to="body">
    <div v-if="showInitialBet" class="pred-backdrop help-backdrop" @click.self="showInitialBet = false" @keydown.esc="showInitialBet = false">
      <div class="help-modal initial-bet-modal" role="dialog" aria-modal="true" aria-label="Apuesta inicial">

        <div class="help-modal__header">
          <div class="help-title">
            <span class="help-title__ball">⭐</span>
            <div>
              <h2>Apuesta inicial</h2>
              <p class="muted">Inmutable · Se guarda para siempre</p>
            </div>
          </div>
          <button class="pred-modal__close" type="button" @click="showInitialBet = false">✕</button>
        </div>

        <!-- Saved confirmation -->
        <div v-if="initialBetSaved" class="initial-saved-msg">
          <Check :size="28" />
          <strong>¡Apuesta guardada!</strong>
        </div>

        <div v-else>
          <!-- Info -->
          <p class="initial-bet-info">
            Predice <strong>todos los partidos</strong> del torneo. Esta predicción no se puede cambiar una vez cerrado cada partido — es tu apuesta inicial permanente.
          </p>

          <!-- Matches by group -->
          <div class="initial-bet-groups">
            <div v-for="group in matchGroups" :key="group.roundName" class="initial-bet-group">
              <p class="initial-bet-group__title">{{ group.roundName }}
                <span class="badge badge--draft" style="font-size:0.6rem">{{ group.stage === 'GROUP_STAGE' ? 'Grupos' : 'Eliminatoria' }}</span>
              </p>
              <div class="initial-bet-matches">
                <div v-for="m in group.matches" :key="m.id" class="initial-bet-row">
                  <div class="initial-bet-row__teams">
                    <span :class="`fi fi-${m.homeFl} flag-xs`"></span>
                    <span class="initial-bet-row__name">{{ m.home }}</span>
                  </div>
                  <div class="pred-stepper pred-stepper--sm">
                    <button type="button" @click="initialPreds[m.id] = { home: Math.max(0, (initialPreds[m.id]?.home ?? 0) - 1), away: initialPreds[m.id]?.away ?? 0 }">−</button>
                    <span class="pred-stepper__val pred-stepper__val--sm">{{ initialPreds[m.id]?.home ?? 0 }}</span>
                    <button type="button" @click="initialPreds[m.id] = { home: Math.min(4, (initialPreds[m.id]?.home ?? 0) + 1), away: initialPreds[m.id]?.away ?? 0 }">+</button>
                  </div>
                  <span class="initial-bet-row__dash">—</span>
                  <div class="pred-stepper pred-stepper--sm">
                    <button type="button" @click="initialPreds[m.id] = { home: initialPreds[m.id]?.home ?? 0, away: Math.max(0, (initialPreds[m.id]?.away ?? 0) - 1) }">−</button>
                    <span class="pred-stepper__val pred-stepper__val--sm">{{ initialPreds[m.id]?.away ?? 0 }}</span>
                    <button type="button" @click="initialPreds[m.id] = { home: initialPreds[m.id]?.home ?? 0, away: Math.min(4, (initialPreds[m.id]?.away ?? 0) + 1) }">+</button>
                  </div>
                  <div class="initial-bet-row__teams initial-bet-row__teams--right">
                    <span class="initial-bet-row__name">{{ m.away }}</span>
                    <span :class="`fi fi-${m.awayFl} flag-xs`"></span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="pred-modal__actions" style="margin-top:16px">
            <button class="btn btn--outline btn--sm" type="button" @click="showInitialBet = false">Cancelar</button>
            <button
              class="btn btn--primary"
              type="button"
              :disabled="initialBetSaving || initialBetStatus?.hasInitialBet"
              @click="saveInitialBet"
            >
              <Check :size="14" />
              {{ initialBetSaving ? 'Guardando…' : initialBetStatus?.hasInitialBet ? 'Ya guardada' : 'Guardar apuesta inicial' }}
            </button>
          </div>
        </div>

      </div>
    </div>
  </Teleport>

  <!-- ═══════════════ MODAL AYUDA ═══════════════ -->
  <Teleport to="body">
    <div v-if="showHelp" class="pred-backdrop help-backdrop" @click.self="showHelp = false" @keydown.esc="showHelp = false">
      <div class="help-modal" role="dialog" aria-modal="true" aria-label="Ayuda">

        <div class="help-modal__header">
          <div class="help-title">
            <img src="/logo.png" class="help-title__logo" alt="Mundia" />
            <div>
              <h2>Cómo funciona Mundia</h2>
              <p class="muted">Todo lo que necesitas saber</p>
            </div>
          </div>
          <button class="pred-modal__close" type="button" @click="showHelp = false">✕</button>
        </div>

        <div class="help-sections">

          <div class="help-section">
            <div class="help-section__icon help-section__icon--blue">🎯</div>
            <div>
              <h3>¿Cómo funciona?</h3>
              <p>Predice el resultado de cada partido antes de que empiece. Cuantos más aciertos, más puntos y más opciones de ganar el bote.</p>
            </div>
          </div>

          <div class="help-section">
            <div class="help-section__icon help-section__icon--gold">⭐</div>
            <div>
              <h3>¿Cómo consigo puntos?</h3>
              <div class="help-points">
                <div class="help-point">
                  <span class="help-point__val">+2</span>
                  <span>Aciertas el ganador o el empate</span>
                </div>
                <div class="help-point">
                  <span class="help-point__val">+2</span>
                  <span>Aciertas el resultado exacto</span>
                </div>
                <div class="help-point">
                  <span class="help-point__val">+1</span>
                  <span>Aciertas los goles del local</span>
                </div>
                <div class="help-point">
                  <span class="help-point__val">+1</span>
                  <span>Aciertas los goles del visitante</span>
                </div>
                <div class="help-point help-point--max">
                  <span class="help-point__val">6</span>
                  <span>Máximo por partido</span>
                </div>
              </div>
            </div>
          </div>

          <div class="help-section">
            <div class="help-section__icon help-section__icon--coral">⏱</div>
            <div>
              <h3>¿Cuándo cierran las predicciones?</h3>
              <p><strong>60 minutos antes</strong> de cada partido. Después ya no puedes cambiarla, ¡así que no te despistes!</p>
            </div>
          </div>

          <div class="help-section">
            <div class="help-section__icon help-section__icon--gold">🏆</div>
            <div>
              <h3>El Pleno de Ganadores</h3>
              <p>Si aciertas <strong>todos los ganadores</strong> del torneo desde el principio, te llevas el <strong>75% del bote</strong>. Se pierde en cuanto fallas uno.</p>
            </div>
          </div>

          <div class="help-section">
            <div class="help-section__icon help-section__icon--green">💶</div>
            <div>
              <h3>¿Es obligatorio pagar?</h3>
              <p>No. La porra puede ser solo por diversión, sin bote. Si hay inscripción, el admin confirma quién ha pagado.</p>
            </div>
          </div>

          <div class="help-section">
            <div class="help-section__icon help-section__icon--purple">👤</div>
            <div>
              <h3>¿Cómo me registro?</h3>
              <p>Con tu cuenta de Google o con email y contraseña. Si el admin ya te ha añadido, entra con el mismo email.</p>
              <p style="margin-top:6px;padding:8px 10px;border-radius:8px;background:rgba(251,191,36,0.08);border:1px solid rgba(251,191,36,0.2);color:var(--gold);font-size:0.78rem;line-height:1.5;">
                ⏩ Si el Mundial ya ha empezado, puedes unirte igualmente. Los partidos ya jugados contarán como 0 puntos — competirás desde el momento en que entres.
              </p>
            </div>
          </div>

          <div class="help-section">
            <div class="help-section__icon help-section__icon--blue">🔧</div>
            <div>
              <h3>¿Quién es el admin?</h3>
              <p>El que crea la porra. Se encarga de añadir participantes, confirmar pagos <em>(si los hay)</em> y gestionar la porra. Los resultados se cargan automáticamente — el admin solo interviene si falla la fuente de datos.</p>
            </div>
          </div>

        </div>

        <button class="btn btn--primary" style="width:100%;margin-top:4px" type="button" @click="showHelp = false">
          ¡Entendido, a predecir! 🚀
        </button>

      </div>
    </div>
  </Teleport>

</template>
