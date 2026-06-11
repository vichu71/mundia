<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
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
  Lock,
  Pencil,
  RefreshCw,
  Settings,
  Sparkles,
  Star,
  Trash2,
  Trophy,
  TrendingUp,
  Users,
  Wifi,
  WifiOff,
  X,
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
type BracketMatch = { matchId: number; home: string; homeFl: string; away: string; awayFl: string; pred: string; real: string; winner: string; done: boolean }
type TeamStanding = { name: string; flag: string; played: number; won: number; drawn: number; lost: number; gf: number; ga: number; points: number }
type GroupStanding = { roundName: string; teams: TeamStanding[] }
type BracketRound = { name: string; matches: BracketMatch[] }
type Recommendation = { type: 'danger' | 'success' | 'info'; text: string }
type PendingPayment = { id: number; name: string }
type Team = { id: number; name: string; flag: string }
type ChampionPick = {
  hasChampionPick: boolean
  teamId: number | null
  teamName: string | null
  flag: string | null
  pickerCount: number
  totalPlayers: number
  pickersPct: number
  championPrize: number
  teamAlive: boolean
}
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

// ─── Settings ────────────────────────────────────────────────────────────────
type Theme = 'dark' | 'light' | 'auto'
const showSettings   = ref(false)
const settingsTheme  = ref<Theme>((localStorage.getItem('mundia-theme') as Theme) ?? 'dark')
const settingsCompact = ref(localStorage.getItem('mundia-compact') === 'true')
const settingsName   = ref('')
const settingsNameSaving = ref(false)

function applyTheme(t: Theme) {
  const el = document.documentElement
  if (t === 'auto') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    el.setAttribute('data-theme', prefersDark ? 'dark' : 'light')
  } else {
    el.setAttribute('data-theme', t)
  }
}

function applyCompact(c: boolean) {
  document.documentElement.setAttribute('data-compact', String(c))
}

function setTheme(t: Theme) {
  settingsTheme.value = t
  localStorage.setItem('mundia-theme', t)
  applyTheme(t)
  syncPreferences()
}

function setCompact(c: boolean) {
  settingsCompact.value = c
  localStorage.setItem('mundia-compact', String(c))
  applyCompact(c)
  syncPreferences()
}

// Persiste tema/compacta en la cuenta del usuario — localStorage es solo caché
// por dispositivo; el servidor hace que las preferencias sigan al usuario.
function syncPreferences() {
  if (!authToken.value) return
  fetchWithAuth(`${API_BASE_URL}/me/preferences`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ theme: settingsTheme.value, compact: settingsCompact.value }),
  }).catch(() => {})
}

async function saveDisplayName() {
  const name = settingsName.value.trim()
  if (!name || name === currentUser.value?.name) return
  settingsNameSaving.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/me/display-name`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ displayName: name }),
    })
    if (res.ok) {
      if (currentUser.value) currentUser.value.name = name
    }
  } finally {
    settingsNameSaving.value = false
  }
}

const themeOptions: Array<{ value: Theme; icon: string; label: string }> = [
  { value: 'dark',  icon: '🌙', label: 'Oscuro' },
  { value: 'light', icon: '☀️', label: 'Claro'  },
  { value: 'auto',  icon: '💻', label: 'Sistema' },
]

// Apply persisted preferences on load
applyTheme(settingsTheme.value)
applyCompact(settingsCompact.value)

const authToken   = ref<string | null>(localStorage.getItem(JWT_KEY))
const currentUser = ref<CurrentUser | null>(null)
const userPools   = ref<UserPool[]>([])
const appReady    = ref(false)   // true once /me loaded
const isSuperAdmin = computed(() => currentUser.value?.email === 'victor.huecas@gmail.com')

// Pantalla de selección de porra
const showPoolSelector    = ref(false)
const showCreateInSelector = ref(false)
const joinCodeInput   = ref('')
const joinCodeError   = ref('')
const joinCodeLoading = ref(false)

async function joinPoolWithCode() {
  const code = joinCodeInput.value.trim().toUpperCase()
  if (!code) return
  joinCodeError.value = ''
  joinCodeLoading.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/pools/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ inviteCode: code }),
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      joinCodeError.value = err.message ?? 'Código no encontrado'
      return
    }
    const data = await res.json()
    joinCodeInput.value = ''
    await loadMe()
    await selectPool(data.poolId)
  } catch {
    joinCodeError.value = 'Error de conexión'
  } finally {
    joinCodeLoading.value = false
  }
}

const activePoolId = computed(() => userPools.value.find(p => p.id === activePool.value)?.id ?? userPools.value[0]?.id ?? null)
const userRole     = computed(() => userPools.value.find(p => p.id === activePoolId.value)?.role ?? null)

async function selectPool(poolId: number) {
  activePool.value = poolId
  showPoolSelector.value = false
  await loadRemoteState()
  animateHero()
}

function fetchWithAuth(url: string, init: RequestInit = {}): Promise<Response> {
  const headers: Record<string, string> = { ...(init.headers as Record<string, string> ?? {}) }
  if (authToken.value) headers['Authorization'] = `Bearer ${authToken.value}`
  return fetch(url, { ...init, headers })
}

async function readApiError(res: Response, fallback: string): Promise<string> {
  const err = await res.json().catch(() => ({}))
  const detail = err.message ?? err.detail ?? err.error
  return detail ? `${fallback}: ${detail}` : `${fallback} (${res.status})`
}

async function loadMe() {
  const res = await fetchWithAuth(`${API_BASE_URL}/me`)
  if (!res.ok) { signOut(); return }
  const data = await res.json()
  currentUser.value = { name: data.name, email: data.email, avatarUrl: data.avatarUrl }
  settingsName.value = data.name
  // Preferencias de la cuenta: mandan sobre el caché local del dispositivo
  if (data.prefTheme) {
    settingsTheme.value = data.prefTheme
    localStorage.setItem('mundia-theme', data.prefTheme)
    applyTheme(data.prefTheme)
  }
  if (data.prefCompact != null) {
    settingsCompact.value = data.prefCompact
    localStorage.setItem('mundia-compact', String(data.prefCompact))
    applyCompact(data.prefCompact)
  }
  userPools.value = data.pools ?? []
  appReady.value = true
  if (userPools.value.length === 1) {
    // Una sola porra → entrar directamente sin selector
    activePool.value = userPools.value[0].id
    await loadRemoteState()
  } else if (userPools.value.length > 1) {
    // Varias porras → mostrar selector
    activePool.value = null
    showPoolSelector.value = true
  }
}

async function handleGoogleCredential(response: { credential: string }) {
  const res = await fetch(`${API_BASE_URL}/auth/google`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ credential: response.credential, accessCode: emailForm.value.accessCode || null }),
  })
  if (!res.ok) {
    emailForm.value.error = await readApiError(res, 'Error al entrar con Google')
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
      emailForm.value.error = await readApiError(res, 'Email o contraseña incorrectos')
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
      emailForm.value.error = await readApiError(res, 'Error al registrarse')
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
const createPoolForm = ref({ description: '', entryFee: 10, loading: false, error: '' })

async function createPool() {
  createPoolForm.value.loading = true
  createPoolForm.value.error = ''
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/pools`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: '',
        description: createPoolForm.value.description,
        entryFeeCents: Math.round(createPoolForm.value.entryFee * 100),
        currency: 'EUR',
      }),
    })
    if (!res.ok) { createPoolForm.value.error = 'Error al crear la porra'; return }
    const created = await res.json() as { id: number }
    createPoolForm.value.description = ''
    showCreateInSelector.value = false
    await loadMe()
    // Aterrizar en la porra recién creada (loadMe deja activePool=null si hay >1)
    activePool.value = created.id
    showPoolSelector.value = false
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
  { current: 'Semi-finals',   next: 'Final',          sortOrder: 15, dates: ['2026-07-25 21:00'] },
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
const ACCESS_CODE_ENABLED = import.meta.env.VITE_ACCESS_CODE_ENABLED === 'true'

const pools = ref<Pool[]>([])

const EMPTY_POOL: Pool = { id: 0, name: '', code: '', status: '', statusType: '', userRole: '', members: 0, paid: 0, pot: 0 }
const selectedPool   = computed(() => pools.value.find((p) => p.id === activePool.value) ?? pools.value[0] ?? EMPTY_POOL)
const nextMatch      = computed(() =>
  // Próximo partido = el siguiente que AÚN se puede predecir (corte no pasado).
  // Un partido en juego/cerrado no es "próximo" y no debe ofrecer editar predicción.
  matches.value.find(m => !isPredictionClosed(m.kickoff) && (m.statusType === 'open' || m.statusType === 'warning'))
  ?? matches.value.find(m => m.statusType === 'open' || m.statusType === 'warning')
  ?? matches.value[0] ?? null
)
const myRanking      = computed(() =>
  ranking.value.find(r => r.name === currentUser.value?.name) ?? ranking.value[0] ?? null
)
const pendingCount   = computed(() =>
  // Solo cuenta partidos que aún se pueden predecir (corte no pasado). Uno en juego
  // o cerrado ya no es "pendiente": no se puede hacer nada con él.
  matches.value.filter(m =>
    (m.statusType === 'open' || m.statusType === 'warning') &&
    m.pred === '? - ?' &&
    !isPredictionClosed(m.kickoff)
  ).length
)
// Partidos sin predecir cuyo corte aún no ha pasado y cierran dentro de 24h.
// (fmtCountdown devuelve 'Cerrado' si ya pasó el corte → se excluye; null si falta >24h.)
const closingSoonUnpredicted = computed(() =>
  matches.value.filter(m => {
    if (m.pred !== '? - ?') return false
    const c = fmtCountdown(m.kickoff)
    return c !== null && c !== 'Cerrado'
  })
)
const champion = computed(() => {
  const final = bracketRounds.value.find(r => r.name === 'Final')?.matches[0]
  if (!final || !final.done) return null
  // winner field holds the team name; match its flag from home/away
  if (final.winner === final.home) return { name: final.home, flag: final.homeFl }
  if (final.winner === final.away) return { name: final.away, flag: final.awayFl }
  return { name: final.winner, flag: 'un' }
})

type BracketPair = { top: BracketMatch; bottom: BracketMatch | null }
type BracketLevel = { name: string; pairs: BracketPair[]; slotH: number }
type TeamInfo = { name: string; flag: string }

// WC2026 R32 bracket: 12 fixed group-stage slots + 4 slots for best 3rd-place teams
// Each entry: [homeGroup, homeRank, awayGroup, awayRank] (rank 1=winner, 2=runner-up)
const WC26_R32: Array<[string, 1|2, string, 1|2]> = [
  ['A',1,'B',2], ['C',1,'D',2], ['E',1,'F',2], ['G',1,'H',2],
  ['I',1,'J',2], ['K',1,'L',2],
  ['B',1,'A',2], ['D',1,'C',2], ['F',1,'E',2], ['H',1,'G',2],
  ['J',1,'I',2], ['L',1,'K',2],
  // last 4 filled with best 3rd-place teams (pairs 1v2, 3v4, 5v6, 7v8)
]

// WC2026 group composition (static, known before tournament)
// Groups A-L with 4 teams each → used for cascade when live data not yet available
const WC26_GROUPS: Record<string, Array<{name: string; flag: string}>> = {
  A: [{name:'Mexico',flag:'mx'},{name:'Ecuador',flag:'ec'},{name:'Venezuela',flag:'ve'},{name:'Bolivia',flag:'bo'}],
  B: [{name:'USA',flag:'us'},{name:'Panama',flag:'pa'},{name:'Paraguay',flag:'py'},{name:'Cuba',flag:'cu'}],
  C: [{name:'Argentina',flag:'ar'},{name:'Peru',flag:'pe'},{name:'Chile',flag:'cl'},{name:'Guatemala',flag:'gt'}],
  D: [{name:'Brazil',flag:'br'},{name:'Colombia',flag:'co'},{name:'Uruguay',flag:'uy'},{name:'Costa Rica',flag:'cr'}],
  E: [{name:'Spain',flag:'es'},{name:'Morocco',flag:'ma'},{name:'Senegal',flag:'sn'},{name:'Niger',flag:'ne'}],
  F: [{name:'Portugal',flag:'pt'},{name:'Croatia',flag:'hr'},{name:'Turkey',flag:'tr'},{name:'Uzbekistan',flag:'uz'}],
  G: [{name:'Germany',flag:'de'},{name:'Netherlands',flag:'nl'},{name:'Ukraine',flag:'ua'},{name:'Angola',flag:'ao'}],
  H: [{name:'France',flag:'fr'},{name:'Belgium',flag:'be'},{name:'Serbia',flag:'rs'},{name:'Indonesia',flag:'id'}],
  I: [{name:'England',flag:'gb-eng'},{name:'Switzerland',flag:'ch'},{name:'Egypt',flag:'eg'},{name:'South Africa',flag:'za'}],
  J: [{name:'Canada',flag:'ca'},{name:'Australia',flag:'au'},{name:'Saudi Arabia',flag:'sa'},{name:'Honduras',flag:'hn'}],
  K: [{name:'Japan',flag:'jp'},{name:'South Korea',flag:'kr'},{name:'Nigeria',flag:'ng'},{name:'New Zealand',flag:'nz'}],
  L: [{name:'Iran',flag:'ir'},{name:'Tunisia',flag:'tn'},{name:'Ghana',flag:'gh'},{name:'Trinidad & Tobago',flag:'tt'}],
}

function parsePredScore(pred: string): [number, number] | null {
  const parts = pred.split(/\s*[-–]\s*/)
  if (parts.length !== 2) return null
  const h = parseInt(parts[0]), a = parseInt(parts[1])
  return isNaN(h) || isNaN(a) ? null : [h, a]
}

type PredTeamStat = TeamInfo & { pts: number; gf: number; ga: number; played: number; won: number; drawn: number; lost: number }

function computeGroupStandingsFromPreds(): Map<string, Array<PredTeamStat>> {
  const result = new Map<string, Array<PredTeamStat>>()
  const groupMatches = matches.value.filter(m => m.stage === 'GROUP_STAGE')
  // initialise team entries
  for (const m of groupMatches) {
    const g = m.roundName?.replace(/^Group\s+/i, '') ?? ''
    if (!g) continue
    if (!result.has(g)) result.set(g, [])
    const arr = result.get(g)!
    if (!arr.find(t => t.name === m.home)) arr.push({ name: m.home, flag: m.homeFl, pts: 0, gf: 0, ga: 0, played: 0, won: 0, drawn: 0, lost: 0 })
    if (!arr.find(t => t.name === m.away)) arr.push({ name: m.away, flag: m.awayFl, pts: 0, gf: 0, ga: 0, played: 0, won: 0, drawn: 0, lost: 0 })
  }
  // compute from prediction (or real result if available)
  for (const m of groupMatches) {
    const g = m.roundName?.replace(/^Group\s+/i, '') ?? ''
    const src = (m.real && m.real !== 'Pend.' && !m.real.includes('?')) ? m.real : m.pred
    const score = parsePredScore(src ?? '')
    if (!score) continue
    const [hg, ag] = score
    const arr = result.get(g)
    if (!arr) continue
    const home = arr.find(t => t.name === m.home)
    const away = arr.find(t => t.name === m.away)
    if (!home || !away) continue
    home.gf += hg; home.ga += ag; home.played++
    away.gf += ag; away.ga += hg; away.played++
    if (hg > ag) { home.pts += 3; home.won++; away.lost++ }
    else if (ag > hg) { away.pts += 3; away.won++; home.lost++ }
    else { home.pts += 1; away.pts += 1; home.drawn++; away.drawn++ }
  }
  // Only keep groups where at least one match has a real prediction
  for (const [g, arr] of result.entries()) {
    const gMatches = groupMatches.filter(m => m.roundName?.replace(/^Group\s+/i, '') === g)
    const hasPred = gMatches.some(m => {
      const p = m.pred ?? ''
      return p !== '' && p !== '? - ?' && p !== '?-?' && p !== '?'
    })
    if (!hasPred) { result.delete(g); continue }
    arr.sort((a, b) => (b.pts - a.pts) || ((b.gf - b.ga) - (a.gf - a.ga)) || (b.gf - a.gf))
  }
  return result
}

// Group standings computed from predictions (for mini clasificacion in Partidos tab)
const predStandingsMap = computed(() => computeGroupStandingsFromPreds())

function getPredStandings(roundName: string): PredTeamStat[] {
  // roundName from backend is e.g. "Group A"; key in map is "A"
  const key = roundName.replace(/^Group\s+/i, '')
  return predStandingsMap.value.get(key) ?? []
}

function bracketMatchWinner(m: BracketMatch): TeamInfo | null {
  if (m.done) {
    // Use real score to determine home/away winner — avoids 'Pendiente' winner field
    // (the winner field can be 'Pendiente' if the DB team name is the placeholder)
    const realScore = parsePredScore(m.real)
    if (realScore) {
      const [hg, ag] = realScore
      return hg >= ag ? { name: m.home, flag: m.homeFl } : { name: m.away, flag: m.awayFl }
    }
    // Fallback: use winner field only if it matches injected home/away name
    if (m.winner && m.winner !== 'Pendiente' && m.winner !== '?') {
      const flag = m.winner === m.home ? m.homeFl : m.awayFl
      return { name: m.winner, flag }
    }
  }
  const score = parsePredScore(m.pred)
  if (!score) return null
  const [hg, ag] = score
  // Knockout: draws resolved in extra time → home team advances
  if (hg >= ag) return { name: m.home, flag: m.homeFl }
  return { name: m.away, flag: m.awayFl }
}

function injectTeam(m: BracketMatch, predicted: Map<number, { home: TeamInfo; away: TeamInfo }>): BracketMatch {
  const p = predicted.get(m.matchId)
  if (!p) return m
  // Replace team if it's unknown (Pendiente or ?) so cascade can propagate winners
  const replaceHome = m.home === 'Pendiente' || m.home === '?'
  const replaceAway = m.away === 'Pendiente' || m.away === '?'
  if (!replaceHome && !replaceAway) return m
  return {
    ...m,
    home:   replaceHome ? p.home.name : m.home,
    homeFl: replaceHome ? p.home.flag : m.homeFl,
    away:   replaceAway ? p.away.name : m.away,
    awayFl: replaceAway ? p.away.flag : m.awayFl,
  }
}

const bracketLevels = computed((): BracketLevel[] => {
  // 'Third place' queda fuera del árbol: una ronda extra de 1 partido rompería
  // el emparejamiento del cascade (se muestra solo en la pestaña Partidos)
  const rounds = bracketRounds.value
    .filter(r => !r.name.toLowerCase().startsWith('group') && r.name !== 'Third place')
    .sort((a, b) => b.matches.length - a.matches.length)
  if (!rounds.length) return []
  const maxMatches = rounds[0].matches.length
  const BASE = 54

  // Build predicted team map: matchId → {home, away}
  const predicted = new Map<number, { home: TeamInfo; away: TeamInfo }>()

  // R32 from group standings (live pred data), falling back to WC26_GROUPS (static pre-tournament)
  const standings = computeGroupStandingsFromPreds()
  // Only use static WC26_GROUPS fallback when there are NO group stage matches in DB
  // (local dev / pre-sync state). Once real fixtures exist, show '?' until user predicts.
  const hasGroupMatches = matches.value.some(m => m.stage === 'GROUP_STAGE')
  const getTeam = (g: string, rank: 1|2): TeamInfo => {
    const arr = standings.get(g)
    const t = arr?.[rank - 1]
    if (t) return { name: t.name, flag: t.flag }
    if (!hasGroupMatches) {
      const staticTeam = WC26_GROUPS[g]?.[rank - 1]
      if (staticTeam) return { name: staticTeam.name, flag: staticTeam.flag }
    }
    return { name: '?', flag: 'un' }
  }
  if (rounds[0]?.matches.length === 16) {
    // Fixed 12 slots
    WC26_R32.forEach(([hg, hr, ag, ar], i) => {
      const m = rounds[0].matches[i]
      if (m) predicted.set(m.matchId, { home: getTeam(hg, hr), away: getTeam(ag, ar) })
    })
    // 4 slots for best 3rd-place teams (slots 12-15)
    const all3rds: Array<TeamInfo & { pts: number; gf: number; ga: number }> = []
    for (const arr of standings.values()) if (arr[2]) all3rds.push(arr[2])
    all3rds.sort((a, b) => (b.pts - a.pts) || ((b.gf - b.ga) - (a.gf - a.ga)))
    const best8 = all3rds.slice(0, 8)
    for (let i = 0; i < 4; i++) {
      const m = rounds[0].matches[12 + i]
      if (m) predicted.set(m.matchId, {
        home: best8[i * 2]     ? { name: best8[i * 2].name,     flag: best8[i * 2].flag }     : { name: '?', flag: 'un' },
        away: best8[i * 2 + 1] ? { name: best8[i * 2 + 1].name, flag: best8[i * 2 + 1].flag } : { name: '?', flag: 'un' },
      })
    }
  }

  // Cascade R16 → QF → SF → Final
  for (let ri = 1; ri < rounds.length; ri++) {
    const prev = rounds[ri - 1].matches.map(m => injectTeam(m, predicted))
    const curr = rounds[ri].matches
    for (let i = 0; i < curr.length; i++) {
      const m1 = prev[i * 2], m2 = prev[i * 2 + 1]
      if (!m1 || !m2) continue
      const w1 = bracketMatchWinner(m1), w2 = bracketMatchWinner(m2)
      predicted.set(curr[i].matchId, {
        home: w1 ?? { name: '?', flag: 'un' },
        away: w2 ?? { name: '?', flag: 'un' },
      })
    }
  }

  return rounds.map(r => {
    const slotH = Math.round((BASE * maxMatches) / r.matches.length)
    const pairs: BracketPair[] = []
    for (let i = 0; i < r.matches.length; i += 2) {
      const top    = injectTeam(r.matches[i],           predicted)
      const bottom = r.matches[i + 1] ? injectTeam(r.matches[i + 1], predicted) : null
      pairs.push({ top, bottom })
    }
    return { name: r.name, pairs, slotH }
  })
})

const ROUND_LABELS: Record<string, string> = {
  'Round of 32': 'R32',
  'Round of 16': 'Octavos',
  'Quarterfinals': 'Cuartos',
  'Quarter-finals': 'Cuartos',
  'Semifinals': 'Semis',
  'Semi-finals': 'Semis',
  'Third place': '3er puesto',
  'Final': 'Final',
}
function roundLabel(name: string): string {
  return ROUND_LABELS[name] ?? name.replace(/^Group\s+/i, 'Grupo ')
}

// Equipos previstos por el cascade del cuadro, indexados por matchId.
// Permite mostrar en Partidos los equipos predichos cuando el partido real
// aún tiene placeholders ('Pendiente').
const injectedTeamsByMatchId = computed(() => {
  const map = new Map<number, BracketMatch>()
  for (const level of bracketLevels.value) {
    for (const pair of level.pairs) {
      map.set(pair.top.matchId, pair.top)
      if (pair.bottom) map.set(pair.bottom.matchId, pair.bottom)
    }
  }
  return map
})

type DisplayTeams = { home: string; homeFl: string; away: string; awayFl: string; predicted: boolean }

function displayTeams(m: Match): DisplayTeams {
  const unknownHome = m.home === 'Pendiente' || m.home === '?'
  const unknownAway = m.away === 'Pendiente' || m.away === '?'
  if (!unknownHome && !unknownAway) {
    return { home: m.home, homeFl: m.homeFl, away: m.away, awayFl: m.awayFl, predicted: false }
  }
  const inj = injectedTeamsByMatchId.value.get(m.id)
  const home = unknownHome ? (inj && inj.home !== 'Pendiente' ? inj.home : '?') : m.home
  const homeFl = unknownHome ? (inj && inj.home !== 'Pendiente' ? inj.homeFl : 'un') : m.homeFl
  const away = unknownAway ? (inj && inj.away !== 'Pendiente' ? inj.away : '?') : m.away
  const awayFl = unknownAway ? (inj && inj.away !== 'Pendiente' ? inj.awayFl : 'un') : m.awayFl
  return { home, homeFl, away, awayFl, predicted: home !== '?' || away !== '?' }
}

// ─── Bracket Builder ────────────────────────────────────────────────────────
const showBracketBuilder = ref(false)
const bracketSaving = ref(false)
// Scores keyed by "levelIndex_slotIndex"
const bScores = ref<Record<string, { h: number|null; a: number|null }>>({})

type BSlot = {
  matchId: number; home: string; homeFl: string; away: string; awayFl: string
  h: number|null; a: number|null; winner: 'home'|'away'|null; locked: boolean
}

const bracketBuilderState = computed((): BSlot[][] => {
  const levels = bracketLevels.value
  if (!levels.length) return []
  const result: BSlot[][] = []
  for (let li = 0; li < levels.length; li++) {
    const slots: BSlot[] = []
    const flatMatches = levels[li].pairs.flatMap(p => p.bottom ? [p.top, p.bottom] : [p.top])
    for (let si = 0; si < flatMatches.length; si++) {
      const m = flatMatches[si]
      const sc = bScores.value[`${li}_${si}`] ?? { h: null, a: null }
      let home = m.home, homeFl = m.homeFl, away = m.away, awayFl = m.awayFl, locked = false
      if (li > 0) {
        const prev = result[li - 1]
        const hw = prev?.[si * 2]; const aw = prev?.[si * 2 + 1]
        if (hw?.winner === 'home')       { home = hw.home;  homeFl = hw.homeFl }
        else if (hw?.winner === 'away')  { home = hw.away;  homeFl = hw.awayFl }
        else                             { home = '?'; homeFl = 'un'; locked = true }
        if (aw?.winner === 'home')       { away = aw.home;  awayFl = aw.homeFl }
        else if (aw?.winner === 'away')  { away = aw.away;  awayFl = aw.awayFl }
        else                             { away = '?'; awayFl = 'un'; locked = true }
      }
      const winner: 'home'|'away'|null =
        sc.h !== null && sc.a !== null ? sc.h > sc.a ? 'home' : sc.a > sc.h ? 'away' : null : null
      slots.push({ matchId: m.matchId, home, homeFl, away, awayFl, h: sc.h, a: sc.a, winner, locked })
    }
    result.push(slots)
  }
  return result
})

const bbFinalWinner = computed(() => {
  const last = bracketBuilderState.value.at(-1)
  if (!last?.length) return null
  const s = last[0]
  if (!s.winner) return null
  return s.winner === 'home' ? { name: s.home, flag: s.homeFl } : { name: s.away, flag: s.awayFl }
})

const bbChampionMismatch = computed(() =>
  !!(bbFinalWinner.value && championPick.value?.hasChampionPick &&
     bbFinalWinner.value.name !== championPick.value.teamName)
)

function bSetScore(li: number, si: number, side: 'h'|'a', raw: string) {
  const key = `${li}_${si}`
  const cur = bScores.value[key] ?? { h: null, a: null }
  const val = raw === '' ? null : Math.max(0, Math.min(20, parseInt(raw) || 0))
  bScores.value = { ...bScores.value, [key]: { ...cur, [side]: val } }
}


async function saveBracketPreds() {
  if (!activePoolId.value) return
  bracketSaving.value = true
  try {
    const allSlots = bracketBuilderState.value.flat()
    const toSave = allSlots.filter(s => s.h !== null && s.a !== null && s.matchId)
    await Promise.all(toSave.map(s =>
      fetchWithAuth(`${API_BASE_URL}/predictions/initial`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ poolId: activePoolId.value, matchId: s.matchId, homeGoals: s.h, awayGoals: s.a }),
      })
    ))
    showBracketBuilder.value = false
    await loadDashboard()
  } finally {
    bracketSaving.value = false
  }
}

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
const initialBetStatus = ref<{ submitted: boolean; serverGroupsTotal: number } | null>(null)

async function loadInitialBetStatus() {
  if (!activePoolId.value) return
  const res = await fetchWithAuth(`${API_BASE_URL}/predictions/initial/status/${activePoolId.value}`)
  if (res.ok) {
    const data = await res.json()
    initialBetStatus.value = {
      submitted: !!data.submitted,
      serverGroupsTotal: data.groupsTotal ?? 0,
    }
  }
}

// Progreso local de grupos (reactivo, sin llamadas al servidor)
const initialBetGroupsDone = computed(() =>
  matches.value.filter(m => m.stage === 'GROUP_STAGE' && m.pred && m.pred !== '? - ?').length
)
const initialBetGroupsTotal = computed(() => {
  // El total exigible son los partidos que aún puedes (o ya pudiste) predecir:
  // excluye los que se cerraron sin predicción, porque ya no se pueden rellenar
  // y bloquearían el guardado para siempre. Así done/total cuadra con el botón.
  // Alcanzable = ya predicho, o el corte aún no ha pasado. Usamos isPredictionClosed
  // (corte real por kickoff) y NO statusType: el backend deja en 'open' un partido ya
  // en juego, lo que mantenía el total en 72 y el botón bloqueado para siempre.
  const reachable = matches.value.filter(m =>
    m.stage === 'GROUP_STAGE' &&
    ((m.pred && m.pred !== '? - ?') || !isPredictionClosed(m.kickoff))
  ).length
  // Antes de cargar datos locales, cae al total del servidor (72).
  if (reachable === 0) return initialBetStatus.value?.serverGroupsTotal ?? 0
  return reachable
})
// Nota: initialBetKnockoutDone / initialBetKnockoutTotal / canLockInitialBet / showInitialBetForced
// se declaran más abajo, tras bracketRounds, para evitar TDZ.

const lockingInitialBet = ref(false)
async function lockInitialBet() {
  if (!activePoolId.value || lockingInitialBet.value) return
  lockingInitialBet.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/initial/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ poolId: activePoolId.value }),
    })
    if (res.ok) {
      initialBetStatus.value = { submitted: true, serverGroupsTotal: initialBetStatus.value?.serverGroupsTotal ?? 0 }
    }
  } catch (e) { console.error('Failed to lock initial bet', e) }
  finally { lockingInitialBet.value = false }
}

// ─── Predicción del Campeón ───────────────────────────────────────────────
const championPick      = ref<ChampionPick | null>(null)
const allTeams          = ref<Team[]>([])
const showChampionPicker = ref(false)
const championPickerSearch = ref('')
const championSaving    = ref(false)

// Equipos WC2026 garantizados (fallback si el backend no los devuelve)
const WC2026_TEAMS: Team[] = [
  { id: -1,  name: 'Argentina',      flag: 'ar' },
  { id: -2,  name: 'Australia',      flag: 'au' },
  { id: -3,  name: 'Belgium',        flag: 'be' },
  { id: -4,  name: 'Brazil',         flag: 'br' },
  { id: -5,  name: 'Cameroon',       flag: 'cm' },
  { id: -6,  name: 'Canada',         flag: 'ca' },
  { id: -7,  name: 'Chile',          flag: 'cl' },
  { id: -8,  name: 'Colombia',       flag: 'co' },
  { id: -9,  name: 'Costa Rica',     flag: 'cr' },
  { id: -10, name: 'Croatia',        flag: 'hr' },
  { id: -11, name: 'Ecuador',        flag: 'ec' },
  { id: -12, name: 'Egypt',          flag: 'eg' },
  { id: -13, name: 'England',        flag: 'gb-eng' },
  { id: -14, name: 'France',         flag: 'fr' },
  { id: -15, name: 'Germany',        flag: 'de' },
  { id: -16, name: 'Ghana',          flag: 'gh' },
  { id: -17, name: 'Honduras',       flag: 'hn' },
  { id: -18, name: 'Indonesia',      flag: 'id' },
  { id: -19, name: 'Iran',           flag: 'ir' },
  { id: -20, name: 'Japan',          flag: 'jp' },
  { id: -21, name: 'Kenya',          flag: 'ke' },
  { id: -22, name: 'Mali',           flag: 'ml' },
  { id: -23, name: 'Mexico',         flag: 'mx' },
  { id: -24, name: 'Morocco',        flag: 'ma' },
  { id: -25, name: 'Netherlands',    flag: 'nl' },
  { id: -26, name: 'New Zealand',    flag: 'nz' },
  { id: -27, name: 'Nigeria',        flag: 'ng' },
  { id: -28, name: 'Panama',         flag: 'pa' },
  { id: -29, name: 'Paraguay',       flag: 'py' },
  { id: -30, name: 'Peru',           flag: 'pe' },
  { id: -31, name: 'Portugal',       flag: 'pt' },
  { id: -32, name: 'Saudi Arabia',   flag: 'sa' },
  { id: -33, name: 'Senegal',        flag: 'sn' },
  { id: -34, name: 'Serbia',         flag: 'rs' },
  { id: -35, name: 'Slovenia',       flag: 'si' },
  { id: -36, name: 'South Africa',   flag: 'za' },
  { id: -37, name: 'South Korea',    flag: 'kr' },
  { id: -38, name: 'Spain',          flag: 'es' },
  { id: -39, name: 'Switzerland',    flag: 'ch' },
  { id: -40, name: 'TBD Conmebol',   flag: 'un' },
  { id: -41, name: 'TBD OFC',        flag: 'un' },
  { id: -42, name: 'Trinidad & Tobago', flag: 'tt' },
  { id: -43, name: 'Tunisia',        flag: 'tn' },
  { id: -44, name: 'Turkey',         flag: 'tr' },
  { id: -45, name: 'Ukraine',        flag: 'ua' },
  { id: -46, name: 'Uruguay',        flag: 'uy' },
  { id: -47, name: 'USA',            flag: 'us' },
  { id: -48, name: 'Venezuela',      flag: 've' },
]

const filteredTeams = computed(() => {
  // Deduplicate by flag code (country_code), keep first occurrence per flag
  const raw = allTeams.value.length > 0 ? allTeams.value : WC2026_TEAMS
  const seen = new Set<string>()
  const deduped = raw.filter(t => {
    const key = t.flag ?? 'un'
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
  const q = championPickerSearch.value.toLowerCase()
  return q ? deduped.filter(t => t.name.toLowerCase().includes(q)) : deduped
})

async function loadChampionStatus() {
  if (!activePoolId.value) return
  const res = await fetchWithAuth(`${API_BASE_URL}/predictions/champion/status/${activePoolId.value}`)
  if (res.ok) championPick.value = await res.json()
}

async function loadAllTeams() {
  if (allTeams.value.length > 0) return
  const res = await fetchWithAuth(`${API_BASE_URL}/teams`)
  if (res.ok) allTeams.value = await res.json()
}

async function openChampionPicker() {
  await loadAllTeams()
  championPickerSearch.value = ''
  showChampionPicker.value = true
}

async function saveChampionPick(teamId: number) {
  if (!activePoolId.value) return
  championSaving.value = true
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/champion`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ poolId: activePoolId.value, teamId }),
    })
    if (res.ok) {
      await loadChampionStatus()
      showChampionPicker.value = false
    }
  } finally {
    championSaving.value = false
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
  const res = await fetchWithAuth(`${API_BASE_URL}/admin/members/${memberId}`, { method: 'DELETE' })
  if (!res.ok) {
    alert(await readApiError(res, `No se pudo eliminar a ${name}`))
    return
  }
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
  await Promise.all([loadDashboard(), loadMembers()])
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

const bracketRounds = ref<BracketRound[]>([])

// ─── Progreso apuesta inicial (cuadro) — declarado AQUÍ para evitar TDZ con bracketRounds ─────
const knockoutRounds = computed(() =>
  // 'Third place' no forma parte de la apuesta inicial de 31 partidos
  bracketRounds.value.filter(r => !r.name.toLowerCase().startsWith('group') && r.name !== 'Third place')
)
const initialBetKnockoutDone = computed(() =>
  // Only check that a score prediction exists (teams may be 'Pendiente' pre-tournament)
  knockoutRounds.value.flatMap(r => r.matches)
    .filter(m => m.pred && m.pred !== '-' && m.pred !== '' && m.pred !== '?-?').length
)
const initialBetKnockoutTotal = computed(() =>
  knockoutRounds.value.flatMap(r => r.matches).length || 31
)
const canLockInitialBet = computed(() => {
  // Un partido solo bloquea el guardado si SIGUE siendo predecible (abierto) y está sin
  // predecir. Los que ya han arrancado no se pueden rellenar (pasó el corte de 60 min),
  // así que no deben impedir guardar la apuesta — si no, un partido en juego deja al
  // jugador sin poder guardar nunca.
  const isUnfilled = (p: string | null | undefined) =>
    !p || p === '? - ?' || p === '?-?' || p === '-' || p === ''

  // Grupos: predecible mientras el corte no haya pasado. Usamos isPredictionClosed
  // (corte real por kickoff) y NO statusType: el backend deja en 'open' un partido ya
  // en juego, lo que bloqueaba el guardado para siempre.
  const groupsPending = matches.value.filter(
    m => m.stage === 'GROUP_STAGE' &&
         !isPredictionClosed(m.kickoff) &&
         isUnfilled(m.pred)
  ).length
  // Cuadro: predecible mientras no se haya jugado (done = partido ya disputado).
  const knockoutPending = knockoutRounds.value.flatMap(r => r.matches).filter(
    m => !m.done && isUnfilled(m.pred)
  ).length
  return groupsPending === 0 && knockoutPending === 0
})
const showInitialBetForced = computed(() =>
  appReady.value && !!activePoolId.value && initialBetStatus.value !== null && !initialBetStatus.value.submitted
)

// La apuesta inicial es OPCIONAL: el aviso se puede cerrar y jugar día a día.
// (Antes te forzaba a la pestaña de partidos al entrar; ya no.)
const initialBetDismissed = ref(false)

// Winner predicho de la Final (el equipo que el cascade dice que ganará)
const predFinalWinner = computed((): TeamInfo | null => {
  const finalLevel = bracketLevels.value.find(l => l.name === 'Final')
  if (!finalLevel) return null
  const finalMatch = finalLevel.pairs[0]?.top
  if (!finalMatch) return null
  return bracketMatchWinner(finalMatch)
})

// Auto-sync campeón: cuando el cascade predice un ganador de la Final, guardarlo automáticamente
let _lastAutoChampion = ''
async function syncAutoChampion(winner: TeamInfo | null) {
  if (!winner || winner.name === '?' || winner.name === 'Pendiente') return
  if (winner.name === _lastAutoChampion) return
  if (!activePoolId.value) return
  // Cargar equipos si aún no están disponibles
  if (allTeams.value.length === 0) {
    try {
      const r = await fetchWithAuth(`${API_BASE_URL}/teams`)
      if (r.ok) allTeams.value = await r.json()
    } catch { /* ignorar */ }
  }
  const team = allTeams.value.find(t => t.name === winner.name)
  if (!team) return
  _lastAutoChampion = winner.name
  try {
    await fetchWithAuth(`${API_BASE_URL}/predictions/champion`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ poolId: activePoolId.value, teamId: team.id }),
    })
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/champion/status/${activePoolId.value}`)
    if (res.ok) championPick.value = await res.json()
  } catch (e) {
    console.error('Auto-champion sync failed', e)
  }
}
watch(predFinalWinner, syncAutoChampion)

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

function isGroupMatchLocked(m: Match): boolean {
  // Los grupos se congelan al guardar la apuesta inicial; sin ella,
  // manda solo el cutoff de 1h antes del partido (isPredictionClosed).
  return !!initialBetStatus.value?.submitted && m.stage === 'GROUP_STAGE'
}

function openPredModal(m: Match) {
  if (isGroupMatchLocked(m)) return
  const parts = m.pred.replace(/\s/g,'').split('-')
  const t = displayTeams(m)
  predModal.value = {
    open: true,
    matchId: m.id,
    home: t.home, away: t.away,
    homeFl: t.homeFl, awayFl: t.awayFl,
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
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/ai/${predModal.value.matchId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      // En cruces la BD tiene 'Pendiente': mandamos los equipos que ve el usuario (cascade)
      body: JSON.stringify({ home: predModal.value.home, away: predModal.value.away }),
    })
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

// "Ver partidos": ir a Partidos y abrir/centrar el primer partido que aún se puede
// predecir. Si no queda ninguno pendiente, simplemente cambia de pestaña.
function goToPendingMatch() {
  activeTab.value = 'matches'
  const pend = matches.value.find(m =>
    (m.statusType === 'open' || m.statusType === 'warning') &&
    m.pred === '? - ?' &&
    !isPredictionClosed(m.kickoff)
  )
  if (!pend?.roundName) return
  collapsedGroups.value.delete(pend.roundName)
  collapsedGroups.value = new Set(collapsedGroups.value)
  nextTick(() => {
    document.querySelector(`[data-round="${pend.roundName}"]`)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

// Colapsar todas las secciones (grupos y eliminatorias) por defecto al cargar partidos
watch(matches, (newMatches) => {
  if (newMatches.length > 0 && collapsedGroups.value.size === 0) {
    const groups = new Set<string>()
    for (const m of newMatches) {
      if (m.roundName) groups.add(m.roundName)
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
const bulkRandomUsed = ref(false)  // solo se puede usar una vez por sesión
const deleteLoading = ref(false)

async function deleteAllPredictions() {
  if (!activePoolId.value) return
  deleteLoading.value = true
  try {
    await fetchWithAuth(`${API_BASE_URL}/predictions/all?poolId=${activePoolId.value}`, { method: 'DELETE' })
    // También borrar el campeón elegido
    await fetchWithAuth(`${API_BASE_URL}/predictions/champion?poolId=${activePoolId.value}`, { method: 'DELETE' })
    championPick.value = null
    _lastAutoChampion = ''
    // Resetear el botón de azar para que se pueda volver a usar
    bulkRandomUsed.value = false
    initialBetStatus.value = { submitted: false, serverGroupsTotal: initialBetStatus.value?.serverGroupsTotal ?? 0 }
    await loadDashboard()
  } finally {
    deleteLoading.value = false
  }
}

async function bulkRandomPredictions() {
  if (initialBetStatus.value?.submitted) return   // apuesta inicial ya enviada
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
    // Apply each prediction to the local matches array and bracket rounds
    const predMap = new Map(data.map((p: { matchId: number; homeGoals: number; awayGoals: number }) => [p.matchId, p]))
    data.forEach((pred: { matchId: number; homeGoals: number; awayGoals: number }) => {
      const m = matches.value.find(x => x.id === pred.matchId)
      if (m) m.pred = `${pred.homeGoals} - ${pred.awayGoals}`
    })
    // Update bracket knockout match preds so the cascade computes correctly
    for (const round of bracketRounds.value) {
      for (const bm of round.matches) {
        const p = predMap.get(bm.matchId)
        if (p) bm.pred = `${p.homeGoals}-${p.awayGoals}`
      }
    }
    // Animate the cards briefly
    const els = document.querySelectorAll<HTMLElement>('.match-card')
    els.forEach((el, i) => {
      el.animate(
        [{ boxShadow: '0 0 0 2px rgba(245,197,66,0.7)' }, { boxShadow: '0 0 0 2px rgba(245,197,66,0)' }],
        { duration: 600, delay: i * 18, easing: 'ease-out', fill: 'both' },
      )
    })
    await Promise.all([loadDashboard(), loadChampionStatus()])
    bulkRandomUsed.value = true
  } catch (e) {
    console.error('Bulk prediction failed', e)
  } finally {
    bulkLoading.value = false
  }
}

// ── Bracket knockout prediction modal ────────────────────────────────────────
const bracketPredModal = ref<{
  open: boolean; matchId: number; home: string; homeFl: string
  away: string; awayFl: string; homeGoals: number; awayGoals: number; saving: boolean
  aiLoading: boolean; aiSource: string | null; aiReasoning: string | null
}>({ open: false, matchId: 0, home: '', homeFl: 'un', away: '', awayFl: 'un', homeGoals: 0, awayGoals: 0, saving: false, aiLoading: false, aiSource: null, aiReasoning: null })

function isBracketMatchEditable(m: BracketMatch): boolean {
  if (m.done) return false
  // Los equipos deben estar confirmados (no son placeholders)
  const teamsKnown = m.home !== 'Pendiente' && m.home !== '?' && m.away !== 'Pendiente' && m.away !== '?'
  return teamsKnown
}

function openBracketPred(m: BracketMatch) {
  if (!isBracketMatchEditable(m)) return
  const existing = parsePredScore(m.pred)
  bracketPredModal.value = {
    open: true, matchId: m.matchId,
    home: m.home, homeFl: m.homeFl,
    away: m.away, awayFl: m.awayFl,
    homeGoals: existing ? existing[0] : 1,
    awayGoals: existing ? existing[1] : 0,
    saving: false,
    aiLoading: false, aiSource: null, aiReasoning: null,
  }
}

async function askAiBracketPrediction() {
  if (!bracketPredModal.value.matchId) return
  bracketPredModal.value.aiLoading = true
  bracketPredModal.value.aiSource = null
  bracketPredModal.value.aiReasoning = null
  try {
    const res = await fetchWithAuth(`${API_BASE_URL}/predictions/ai/${bracketPredModal.value.matchId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ home: bracketPredModal.value.home, away: bracketPredModal.value.away }),
    })
    const data = await res.json()
    if (data.error) throw new Error(data.error)
    bracketPredModal.value.homeGoals = data.homeGoals
    bracketPredModal.value.awayGoals = data.awayGoals
    bracketPredModal.value.aiSource = data.source
    bracketPredModal.value.aiReasoning = data.reasoning ?? null
  } catch (e) {
    console.error('AI bracket prediction failed', e)
  } finally {
    bracketPredModal.value.aiLoading = false
  }
}

async function saveBracketPred() {
  const { matchId, homeGoals, awayGoals } = bracketPredModal.value
  bracketPredModal.value.saving = true
  // optimistic update in bracketRounds
  for (const round of bracketRounds.value) {
    const m = round.matches.find(m => m.matchId === matchId)
    if (m) { m.pred = `${homeGoals}-${awayGoals}`; break }
  }
  bracketPredModal.value.open = false
  try {
    await fetchWithAuth(`${API_BASE_URL}/predictions/match`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ poolId: activePoolId.value, matchId, homeGoals, awayGoals }),
    })
  } catch (e) {
    console.error('Failed to save bracket prediction', e)
  } finally {
    bracketPredModal.value.saving = false
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
    await loadDashboard()
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
  // Sync campeón con el ganador predicho de la Final (en caso de que ya estuviera predicho)
  await nextTick()
  syncAutoChampion(predFinalWinner.value)
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
  if (userRole.value !== 'ADMIN') {
    apiSync.value.status = 'error'
    return
  }
  const res = await fetchWithAuth(`${API_BASE_URL}/admin/sports-sync/status`)
  if (!res.ok) throw new Error(await readApiError(res, 'No se pudo cargar la sincronización'))
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

// ── Auto-reload on new deploy ─────────────────────────────────────────────────
let _buildVersion: string | null = null
let _versionTimer: ReturnType<typeof setInterval> | null = null

async function checkVersion() {
  try {
    const res = await fetch(`/version.json?_=${Date.now()}`)
    if (!res.ok) return
    const { v } = await res.json()
    if (_buildVersion === null) { _buildVersion = v; return }
    if (v !== _buildVersion) window.location.reload()
  } catch { /* offline o dev — ignorar */ }
}

onMounted(() => {
  checkVersion()
  _versionTimer = setInterval(checkVersion, 5 * 60 * 1000) // cada 5 min
})

onUnmounted(() => { if (_versionTimer) clearInterval(_versionTimer) })

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
    const tasks = [loadDashboard(), loadInitialBetStatus(), loadChampionStatus()]
    if (userRole.value === 'ADMIN') tasks.push(loadApiSyncStatus())
    await Promise.all(tasks)
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
      // El campeón se sincroniza automáticamente desde la Final predicha (syncAutoChampion)
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
      <p class="login-sub">Mundial 2026</p>

      <!-- Tabs -->
      <div class="auth-tabs">
        <button :class="['auth-tab', { active: authMode === 'google' }]" @click="authMode = 'google'">Google</button>
        <button :class="['auth-tab', { active: authMode === 'login' }]" @click="authMode = 'login'">Email</button>
        <button :class="['auth-tab', { active: authMode === 'register' }]" @click="authMode = 'register'">Registrarse</button>
      </div>

      <!-- Código de acceso global (solo si está habilitado en el build) -->
      <div v-if="ACCESS_CODE_ENABLED" class="invite-form" style="width:100%;text-align:left;margin-bottom:4px">
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

      <!-- Unirse con código -->
      <p class="login-sub">¿Tienes un código de invitación?</p>
      <div style="width:100%">
        <input
          v-model="joinCodeInput"
          class="invite-input"
          type="text"
          placeholder="Código de invitación (ej: A3F7K2X9)"
          style="text-transform:uppercase;letter-spacing:0.08em;width:100%;box-sizing:border-box"
          @keyup.enter="joinPoolWithCode"
        />
        <p v-if="joinCodeError" class="invite-error">{{ joinCodeError }}</p>
        <button
          class="btn btn--primary"
          style="width:100%;margin-top:6px"
          type="button"
          :disabled="!joinCodeInput.trim() || joinCodeLoading"
          @click="joinPoolWithCode"
        >
          {{ joinCodeLoading ? 'Uniéndose…' : '🔗 Unirse a una porra' }}
        </button>
      </div>

      <div class="pool-selector__divider" style="width:100%;margin:12px 0">o</div>

      <!-- Crear porra nueva -->
      <p class="login-sub" style="margin:0 0 8px">Crea tu propia porra</p>
      <div class="invite-form" style="width:100%;text-align:left">
        <label class="invite-label">Descripción (opcional)</label>
        <input v-model="createPoolForm.description" class="invite-input" type="text" placeholder="Porra familiar del Mundial" />
        <label class="invite-label">Inscripción por jugador (€)</label>
        <input v-model.number="createPoolForm.entryFee" class="invite-input" type="number" min="0" step="1" />
        <p v-if="createPoolForm.error" class="invite-error">{{ createPoolForm.error }}</p>
      </div>
      <button
        class="btn btn--outline"
        style="width:100%;margin-top:6px"
        type="button"
        :disabled="createPoolForm.loading"
        @click="createPool"
      >
        {{ createPoolForm.loading ? 'Creando…' : '🏆 Crear mi porra' }}
      </button>

      <button class="btn btn--ghost" style="margin-top:8px" type="button" @click="signOut">
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
            <h1>Mundia</h1>
          </div>
        </div>
        <div class="user-chip">
          <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" class="user-avatar" :alt="currentUser.name" />
          <span v-else class="user-avatar user-avatar--initials">{{ currentUser ? currentUser.name.slice(0,2).toUpperCase() : '?' }}</span>
          <span class="user-name">{{ currentUser?.name }}</span>
          <button class="btn--settings" type="button" @click="showPoolSelector = true; showCreateInSelector = false" title="Mis porras">
            <Trophy :size="15" />
          </button>
          <button class="btn--settings" type="button" @click="showSettings = true" title="Ajustes">
            <Settings :size="15" />
          </button>
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

      <!-- Fila 1: Campeón centrado -->
      <div class="champion-hero-card" :class="{ 'champion-hero-card--empty': !championPick?.hasChampionPick, 'champion-hero-card--dead': championPick?.hasChampionPick && !championPick.teamAlive }">
        <template v-if="championPick?.hasChampionPick">
          <div class="champion-hero-card__flag-wrap">
            <span :class="`fi fi-${championPick.flag} champion-hero-flag`"></span>
            <span :class="['champion-alive-dot', championPick.teamAlive ? 'champion-alive-dot--on' : 'champion-alive-dot--off']"></span>
          </div>
          <div class="champion-hero-card__body">
            <p class="champion-hero-card__eyebrow"><Crown :size="11" /> Mi campeón</p>
            <h3 class="champion-hero-card__name">{{ championPick.teamName }}</h3>
            <p :class="['champion-hero-card__status', championPick.teamAlive ? 'champion-hero-card__status--alive' : 'champion-hero-card__status--dead']">
              {{ championPick.teamAlive ? '🔥 Vivo en el torneo' : '💀 Eliminado' }}
            </p>
          </div>
          <div class="champion-hero-card__stats">
            <div class="champion-hero-stat">
              <span class="champion-hero-stat__val">{{ championPick.pickersPct }}%</span>
              <span class="champion-hero-stat__label">lo eligieron</span>
            </div>
            <div v-if="championPick.championPrize > 0" class="champion-hero-stat champion-hero-stat--prize">
              <span class="champion-hero-stat__val">{{ championPick.championPrize }} €</span>
              <span class="champion-hero-stat__label">si gana</span>
            </div>
          </div>
          <button v-if="!showInitialBetForced" class="champion-hero-card__change" type="button" @click="openChampionPicker">
            <Pencil :size="11" /> Cambiar
          </button>
        </template>
        <template v-else>
          <Crown :size="32" class="champion-hero-card__crown-icon" />
          <p class="champion-hero-card__cta-title">Se asignará al guardar tu apuesta</p>
          <p class="champion-hero-card__cta-sub">El ganador de tu Final predicha será tu campeón</p>
        </template>
      </div>

      <!-- Fila 2: Scoreboard + Próximo partido -->
      <div class="hero-bottom-row">
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

        <!-- Próximo partido -->
        <div class="next-match-card">
          <p class="eyebrow"><Zap :size="12" /> Próximo partido</p>
          <template v-if="nextMatch">
            <div class="next-match-card__duel">
              <div class="team-block">
                <span :class="`fi fi-${displayTeams(nextMatch).homeFl} flag-xl`"></span>
                <strong>{{ displayTeams(nextMatch).home }}</strong>
              </div>
              <div class="vs-block">
                <span class="vs-label">VS</span>
                <span class="match-time">{{ fmtKickoff(nextMatch.kickoff) }}</span>
              </div>
              <div class="team-block">
                <span :class="`fi fi-${displayTeams(nextMatch).awayFl} flag-xl`"></span>
                <strong>{{ displayTeams(nextMatch).away }}</strong>
              </div>
            </div>
            <div class="next-match-card__pred">
              <span>Tu predicción</span>
              <strong>{{ nextMatch.pred }}</strong>
            </div>
            <button
              class="btn btn--sm"
              :class="(isPredictionClosed(nextMatch.kickoff) || isGroupMatchLocked(nextMatch)) ? 'btn--outline' : 'btn--primary'"
              type="button"
              :disabled="isPredictionClosed(nextMatch.kickoff) || isGroupMatchLocked(nextMatch)"
              @click="openPredModal(nextMatch)"
            >
              <Lock v-if="isPredictionClosed(nextMatch.kickoff) || isGroupMatchLocked(nextMatch)" :size="15" />
              <Pencil v-else :size="15" />
              {{ isPredictionClosed(nextMatch.kickoff) ? 'Predicción cerrada' : isGroupMatchLocked(nextMatch) ? 'Fase de grupos bloqueada' : 'Editar predicción' }}
            </button>
          </template>
          <p v-else class="muted">Sin partidos pendientes</p>
        </div>
      </div>

    </section>

    <!-- ═══════════════ APUESTA INICIAL FORZADA — PANEL ═══════════════ -->
    <div v-if="showInitialBetForced && !initialBetDismissed" class="initial-onboarding-panel">
      <div class="initial-onboarding-panel__info">
        <Star :size="15" />
        <span>Apuesta inicial <strong>(opcional)</strong> · Predice todo de golpe y compite por el premio a la mejor apuesta inicial. Si lo prefieres, ciérralo y predice día a día.</span>
        <button
          type="button"
          @click="initialBetDismissed = true"
          aria-label="Cerrar aviso"
          style="margin-left:auto;background:none;border:none;color:inherit;cursor:pointer;font-size:1rem;opacity:0.7;flex-shrink:0"
        >✕</button>
      </div>
      <div class="initial-onboarding-panel__body">
        <div class="initial-onboarding-panel__progress">
          <!-- Step 1: Grupos — only show when group stage matches exist -->
          <template v-if="initialBetGroupsTotal > 0">
            <button
              :class="['prog-step', { 'prog-step--done': initialBetGroupsDone >= initialBetGroupsTotal, 'prog-step--active': activeTab === 'matches' }]"
              type="button" @click="activeTab = 'matches'"
            >
              <span class="prog-step__num">1</span>
              <span>Grupos</span>
              <span class="prog-step__count">{{ initialBetGroupsDone }}/{{ initialBetGroupsTotal }}</span>
            </button>
            <span class="step-arrow">→</span>
          </template>
          <!-- Step 2 (or only step): Cuadro eliminatorio -->
          <button
            :class="['prog-step', { 'prog-step--done': initialBetKnockoutDone >= initialBetKnockoutTotal && initialBetKnockoutTotal > 0, 'prog-step--active': activeTab === 'bracket' }]"
            type="button" @click="activeTab = 'bracket'"
          >
            <span class="prog-step__num">{{ initialBetGroupsTotal > 0 ? 2 : 1 }}</span>
            <span>Cuadro</span>
            <span class="prog-step__count">{{ initialBetKnockoutDone }}/{{ initialBetKnockoutTotal }}</span>
          </button>
        </div>
        <div class="initial-onboarding-panel__actions">
          <button
            :class="['btn', 'btn--random-all', 'btn--sm', { 'btn--random-all--used': bulkRandomUsed }]"
            type="button" :disabled="bulkLoading || bulkRandomUsed"
            @click="bulkRandomPredictions"
          >
            <Sparkles :size="13" />
            {{ bulkLoading ? 'Prediciendo…' : bulkRandomUsed ? '✓ Al azar' : 'Predecir al azar' }}
          </button>
          <button
            class="btn btn--primary btn--sm"
            type="button"
            :disabled="!canLockInitialBet || lockingInitialBet"
            @click="lockInitialBet"
          >
            <Lock :size="13" />
            {{ lockingInitialBet ? 'Guardando…' : 'Guardar apuesta' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ═══════════════ CONTENIDO POR TAB ═══════════════ -->
    <div class="tab-content">

      <!-- ─── INICIO ─── -->
      <template v-if="activeTab === 'home'">

        <!-- Banner alerta partidos que cierran hoy sin predecir -->
        <div v-if="closingSoonUnpredicted.length > 0" class="home-alert-banner">
          <CircleAlert :size="18" />
          <span>
            Tienes <strong>{{ closingSoonUnpredicted.length }}</strong>
            partido{{ closingSoonUnpredicted.length > 1 ? 's' : '' }}
            que cierra{{ closingSoonUnpredicted.length > 1 ? 'n' : '' }} hoy sin predecir
          </span>
          <button class="btn btn--sm btn--primary" type="button" @click="activeTab = 'matches'">
            Predecir ahora
          </button>
        </div>

        <!-- Banner apuesta inicial (solo cuando está guardada) -->
        <div v-if="initialBetStatus?.submitted" class="initial-bet-banner initial-bet-banner--done">
          <Check :size="18" />
          <div class="initial-bet-banner__text">
            <strong>Apuesta inicial guardada</strong>
            <span>Predicciones iniciales bloqueadas · inmutable</span>
          </div>
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
                <span :class="`fi fi-${displayTeams(nextMatch).homeFl} flag-xl`"></span>
                <strong>{{ displayTeams(nextMatch).home }}</strong>
              </div>
              <div class="vs-block">
                <span class="vs-label">VS</span>
                <span :class="['badge', `badge--${nextMatch.statusType}`]">{{ nextMatch.status }}</span>
              </div>
              <div class="team-block">
                <span :class="`fi fi-${displayTeams(nextMatch).awayFl} flag-xl`"></span>
                <strong>{{ displayTeams(nextMatch).away }}</strong>
              </div>
            </div>
            <div class="next-match-card__pred">
              <span>Tu predicción</span>
              <strong>{{ nextMatch.pred }}</strong>
            </div>
            <button
              class="btn btn--sm"
              :class="(isPredictionClosed(nextMatch.kickoff) || isGroupMatchLocked(nextMatch)) ? 'btn--outline' : 'btn--primary'"
              type="button"
              :disabled="isPredictionClosed(nextMatch.kickoff) || isGroupMatchLocked(nextMatch)"
              @click="openPredModal(nextMatch)"
            >
              <Lock v-if="isPredictionClosed(nextMatch.kickoff) || isGroupMatchLocked(nextMatch)" :size="14" />
              <Pencil v-else :size="14" />
              {{ isPredictionClosed(nextMatch.kickoff) ? 'Predicción cerrada' : isGroupMatchLocked(nextMatch) ? 'Fase de grupos bloqueada' : 'Editar predicción' }}
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
            <button class="btn btn--outline" type="button" @click="goToPendingMatch">
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
            v-if="!initialBetStatus?.submitted"
            :class="['btn', 'btn--random-all', { 'btn--random-all--used': bulkRandomUsed }]"
            type="button"
            :disabled="bulkLoading || bulkRandomUsed"
            @click="bulkRandomPredictions"
            :title="bulkRandomUsed ? 'Ya usaste la predicción aleatoria' : 'Predecir todos los partidos al azar de una vez'"
          >
            <Sparkles :size="15" :class="{ 'spin': bulkLoading }" />
            {{ bulkLoading ? 'Prediciendo…' : bulkRandomUsed ? '✓ Ya predicho al azar' : 'Predecir todos al azar' }}
          </button>
          <button
            v-if="false"
            class="btn btn--ghost btn--sm btn--danger"
            type="button"
            :disabled="deleteLoading"
            @click="deleteAllPredictions"
          >
            <Trash2 :size="13" />
            {{ deleteLoading ? 'Borrando…' : 'Eliminar predicciones' }}
          </button>
        </div>

        <!-- Grupos colapsables -->
        <div class="match-groups">
          <section v-for="group in matchGroups" :key="group.roundName" class="match-group" :data-round="group.roundName">

            <!-- Cabecera del grupo -->
            <button
              class="match-group__header"
              type="button"
              @click="toggleGroup(group.roundName)"
            >
              <span class="match-group__chevron" :class="{ 'match-group__chevron--open': !isCollapsed(group.roundName) }">
                <ChevronRight :size="15" />
              </span>
              <span class="match-group__name">{{ roundLabel(group.roundName) }}</span>
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

            <!-- Mini clasificación (solo visible cuando colapsado y hay predicciones).
                 También abre el grupo: en móvil es donde cae el dedo. -->
            <div
              v-if="isCollapsed(group.roundName) && getPredStandings(group.roundName).length > 0"
              class="match-group__standings"
              role="button"
              tabindex="0"
              @click="toggleGroup(group.roundName)"
              @keydown.enter="toggleGroup(group.roundName)"
            >
              <div
                v-for="(team, idx) in getPredStandings(group.roundName)"
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
                <strong class="standing-pts">{{ team.pts }}pts</strong>
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
                      <span :class="`fi fi-${displayTeams(m).homeFl} flag-lg`"></span>
                      <strong>{{ displayTeams(m).home }}</strong>
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
                      <span :class="`fi fi-${displayTeams(m).awayFl} flag-lg`"></span>
                      <strong>{{ displayTeams(m).away }}</strong>
                    </div>
                  </div>
                  <span v-if="m.stage !== 'GROUP_STAGE' && displayTeams(m).predicted && (m.home === 'Pendiente' || m.away === 'Pendiente')" class="muted" style="font-size:11px">
                    Equipos según tu predicción del cuadro
                  </span>
                  <span v-if="fmtCountdown(m.kickoff)" class="match-countdown">
                    ⏱ {{ fmtCountdown(m.kickoff) }}
                  </span>
                  <button
                    v-if="m.statusType === 'open' || m.statusType === 'warning'"
                    class="btn btn--sm"
                    :class="(isPredictionClosed(m.kickoff) || isGroupMatchLocked(m)) ? 'btn--outline' : 'btn--primary'"
                    type="button"
                    :disabled="isPredictionClosed(m.kickoff) || isGroupMatchLocked(m)"
                    @click="openPredModal(m)"
                  >
                    <Lock v-if="isGroupMatchLocked(m)" :size="13" />
                    <Pencil v-else :size="13" />
                    {{ isPredictionClosed(m.kickoff) ? 'Predicción cerrada' : isGroupMatchLocked(m) ? 'Fase de grupos bloqueada' : 'Editar predicción' }}
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

          <!-- Árbol de eliminatorias -->
          <div v-if="bracketLevels.length" class="bracket-tab__section">
            <div class="bracket-tab__phase-header">
              <ChevronRight :size="16" />
              <h2>Eliminatorias</h2>
            </div>
            <div class="bracket-tree-wrap">
              <div class="bracket-tree">

                <div v-for="(level, li) in bracketLevels" :key="level.name" class="bracket-col">
                  <p class="bracket-col__label">{{ roundLabel(level.name) }}</p>
                  <div class="bracket-col__body">
                    <div
                      v-for="(pair, pi) in level.pairs"
                      :key="pi"
                      :class="['bracket-pair', li < bracketLevels.length - 1 && pair.bottom ? 'bracket-pair--active' : '']"
                    >
                      <!-- top match -->
                      <div class="bracket-slot" :style="{ height: level.slotH + 'px' }">
                        <div
                          :class="['bracket-mc', { 'bracket-mc--done': pair.top.done, 'bracket-mc--pred': !pair.top.done && pair.top.pred !== '?-?', 'bracket-mc--final': level.name === 'Final', 'bracket-mc--editable': isBracketMatchEditable(pair.top) }]"
                          @click="openBracketPred(pair.top)"
                        >
                          <div :class="['bracket-tm', { 'bracket-tm--win': pair.top.done && pair.top.winner === pair.top.home }]">
                            <span :class="`fi fi-${pair.top.homeFl} flag-xs`"></span>
                            <span class="bracket-tm__name">{{ pair.top.home }}</span>
                            <strong v-if="pair.top.done" class="bracket-tm__score">{{ pair.top.real.split('-')[0] }}</strong>
                            <span v-else-if="pair.top.pred !== '?-?'" class="bracket-tm__pred">{{ pair.top.pred.split('-')[0] }}</span>
                          </div>
                          <div :class="['bracket-tm', { 'bracket-tm--win': pair.top.done && pair.top.winner === pair.top.away }]">
                            <span :class="`fi fi-${pair.top.awayFl} flag-xs`"></span>
                            <span class="bracket-tm__name">{{ pair.top.away }}</span>
                            <strong v-if="pair.top.done" class="bracket-tm__score">{{ pair.top.real.split('-')[1] }}</strong>
                            <span v-else-if="pair.top.pred !== '?-?'" class="bracket-tm__pred">{{ pair.top.pred.split('-')[1] }}</span>
                          </div>
                        </div>
                      </div>
                      <!-- bottom match -->
                      <div v-if="pair.bottom" class="bracket-slot" :style="{ height: level.slotH + 'px' }">
                        <div
                          :class="['bracket-mc', { 'bracket-mc--done': pair.bottom.done, 'bracket-mc--pred': !pair.bottom.done && pair.bottom.pred !== '?-?', 'bracket-mc--final': level.name === 'Final', 'bracket-mc--editable': isBracketMatchEditable(pair.bottom) }]"
                          @click="openBracketPred(pair.bottom)"
                        >
                          <div :class="['bracket-tm', { 'bracket-tm--win': pair.bottom.done && pair.bottom.winner === pair.bottom.home }]">
                            <span :class="`fi fi-${pair.bottom.homeFl} flag-xs`"></span>
                            <span class="bracket-tm__name">{{ pair.bottom.home }}</span>
                            <strong v-if="pair.bottom.done" class="bracket-tm__score">{{ pair.bottom.real.split('-')[0] }}</strong>
                            <span v-else-if="pair.bottom.pred !== '?-?'" class="bracket-tm__pred">{{ pair.bottom.pred.split('-')[0] }}</span>
                          </div>
                          <div :class="['bracket-tm', { 'bracket-tm--win': pair.bottom.done && pair.bottom.winner === pair.bottom.away }]">
                            <span :class="`fi fi-${pair.bottom.awayFl} flag-xs`"></span>
                            <span class="bracket-tm__name">{{ pair.bottom.away }}</span>
                            <strong v-if="pair.bottom.done" class="bracket-tm__score">{{ pair.bottom.real.split('-')[1] }}</strong>
                            <span v-else-if="pair.bottom.pred !== '?-?'" class="bracket-tm__pred">{{ pair.bottom.pred.split('-')[1] }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Trophy column -->
                <div class="bracket-col bracket-col--trophy">
                  <p class="bracket-col__label">Campeón</p>
                  <div
                    class="bracket-slot bracket-slot--trophy"
                    :style="{ height: (bracketLevels[bracketLevels.length - 1]?.slotH ?? 54) + 'px' }"
                  >
                    <div :class="['bracket-trophy-card', { 'bracket-trophy-card--pred': !champion && (predFinalWinner?.name && predFinalWinner.name !== '?' && predFinalWinner.name !== 'Pendiente' || championPick?.hasChampionPick) }]">
                      <Crown :size="22" style="color:var(--gold)" />
                      <template v-if="champion">
                        <span :class="`fi fi-${champion.flag} flag-sm`"></span>
                        <strong>{{ champion.name }}</strong>
                      </template>
                      <template v-else-if="predFinalWinner && predFinalWinner.name !== '?' && predFinalWinner.name !== 'Pendiente'">
                        <span :class="`fi fi-${predFinalWinner.flag} flag-sm`"></span>
                        <strong>{{ predFinalWinner.name }}</strong>
                        <span class="bracket-trophy-pred-badge">pred.</span>
                      </template>
                      <template v-else-if="championPick?.hasChampionPick">
                        <span :class="`fi fi-${championPick.flag} flag-sm`"></span>
                        <strong>{{ championPick.teamName }}</strong>
                        <span class="bracket-trophy-pred-badge">pred.</span>
                      </template>
                      <span v-else class="bracket-trophy-tbd">Por decidir</span>
                    </div>
                  </div>
                </div>

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
          <article v-if="isSuperAdmin" class="panel admin-wide-panel sim-panel">
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
              <button type="button" @click="predModal.homeGoals++">+</button>
            </div>
          </div>
          <span class="pred-dash">—</span>
          <div class="pred-score-block">
            <span :class="`fi fi-${predModal.awayFl} flag-sm`"></span>
            <label>{{ predModal.away }}</label>
            <div class="pred-stepper">
              <button type="button" @click="predModal.awayGoals = Math.max(0, predModal.awayGoals - 1)">−</button>
              <span class="pred-stepper__val">{{ predModal.awayGoals }}</span>
              <button type="button" @click="predModal.awayGoals++">+</button>
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

  <!-- (modal apuesta inicial eliminado — flujo guiado inline) -->

  <!-- ═══════════════ MODAL CAMPEÓN ═══════════════ -->
  <Teleport to="body">
    <div v-if="showChampionPicker" class="pred-backdrop help-backdrop" @click.self="showChampionPicker = false" @keydown.esc="showChampionPicker = false">
      <div class="help-modal champion-picker-modal" role="dialog" aria-modal="true" aria-label="Elige tu campeón">

        <div class="help-modal__header">
          <div class="help-title">
            <span class="help-title__ball">🏆</span>
            <div>
              <h2>¿Quién ganará el Mundial?</h2>
              <p class="muted">Tu apuesta por el campeón · se puede cambiar</p>
            </div>
          </div>
          <button class="pred-modal__close" type="button" @click="showChampionPicker = false">✕</button>
        </div>

        <!-- Search -->
        <div class="champion-picker-search">
          <input
            v-model="championPickerSearch"
            class="invite-input"
            type="search"
            placeholder="Buscar selección…"
            autocomplete="off"
          />
        </div>

        <!-- Current pick reminder -->
        <div v-if="championPick?.hasChampionPick" class="champion-current-pick">
          <span :class="`fi fi-${championPick.flag} flag-sm`"></span>
          <span>Tienes elegido: <strong>{{ championPick.teamName }}</strong></span>
        </div>

        <!-- Team grid -->
        <div class="champion-team-grid">
          <button
            v-for="team in filteredTeams"
            :key="team.id"
            :class="['champion-team-btn', { 'champion-team-btn--selected': championPick?.teamId === team.id }]"
            type="button"
            :disabled="championSaving"
            @click="saveChampionPick(team.id)"
          >
            <span :class="`fi fi-${team.flag} champion-flag`"></span>
            <span class="champion-team-btn__name">{{ team.name }}</span>
            <span v-if="championPick?.teamId === team.id" class="champion-team-btn__check">✓</span>
          </button>
        </div>

        <p v-if="filteredTeams.length === 0" class="champion-no-results">
          No hay selecciones que coincidan con "{{ championPickerSearch }}"
        </p>

      </div>
    </div>
  </Teleport>

  <!-- ═══════════════ BRACKET BUILDER ═══════════════ -->
  <Teleport to="body">
    <div v-if="showBracketBuilder" class="pred-backdrop bb-backdrop" @click.self="showBracketBuilder = false" @keydown.esc="showBracketBuilder = false">
      <div class="bb-modal" role="dialog" aria-modal="true" aria-label="Predecir eliminatorias">

        <div class="bb-header">
          <div class="bb-header__title">
            <ChevronRight :size="16" style="color:var(--blue)" />
            <strong>Predice las eliminatorias</strong>
          </div>
          <p class="bb-header__sub">Rellena los marcadores — los ganadores avanzan automáticamente</p>
          <button class="modal-close" @click="showBracketBuilder = false">✕</button>
        </div>

        <!-- Champion mismatch warning -->
        <div v-if="bbChampionMismatch" class="bb-warning">
          ⚠️ Tu campeón elegido (<strong>{{ championPick?.teamName }}</strong>) no llega a la Final según esta predicción.
          <button class="bb-warning__link" @click="showBracketBuilder = false; openChampionPicker()">Cambiar campeón</button>
        </div>
        <div v-else-if="bbFinalWinner && championPick?.hasChampionPick" class="bb-ok">
          ✓ <strong>{{ bbFinalWinner.name }}</strong> gana la Final — coincide con tu campeón elegido
        </div>

        <!-- Bracket tree -->
        <div class="bb-scroll">
          <div class="bb-tree">
            <div v-for="(level, li) in bracketBuilderState" :key="li" class="bb-col">
              <p class="bb-col__label">{{ roundLabel(bracketLevels[li]?.name ?? '') }}</p>
              <div class="bb-col__body">
                <div
                  v-for="(slot, si) in level"
                  :key="si"
                  class="bb-slot"
                  :style="{ height: bracketLevels[li]?.slotH + 'px' }"
                  :class="{ 'bb-slot--top': si % 2 === 0, 'bb-slot--bottom': si % 2 === 1 }"
                >
                  <div :class="['bb-match', { 'bb-match--locked': slot.locked, 'bb-match--done': slot.winner }]">
                    <!-- Home row -->
                    <div :class="['bb-team', { 'bb-team--win': slot.winner === 'home', 'bb-team--lose': slot.winner === 'away' }]">
                      <span :class="`fi fi-${slot.homeFl} flag-xs`"></span>
                      <span class="bb-team__name">{{ slot.home }}</span>
                      <input
                        class="bb-score-input"
                        type="number" min="0" max="20"
                        :disabled="slot.locked"
                        :value="slot.h ?? ''"
                        @input="bSetScore(li, si, 'h', ($event.target as HTMLInputElement).value)"
                        @focus="($event.target as HTMLInputElement).select()"
                        placeholder="–"
                      />
                    </div>
                    <!-- Away row -->
                    <div :class="['bb-team', { 'bb-team--win': slot.winner === 'away', 'bb-team--lose': slot.winner === 'home' }]">
                      <span :class="`fi fi-${slot.awayFl} flag-xs`"></span>
                      <span class="bb-team__name">{{ slot.away }}</span>
                      <input
                        class="bb-score-input"
                        type="number" min="0" max="20"
                        :disabled="slot.locked"
                        :value="slot.a ?? ''"
                        @input="bSetScore(li, si, 'a', ($event.target as HTMLInputElement).value)"
                        @focus="($event.target as HTMLInputElement).select()"
                        placeholder="–"
                      />
                    </div>
                    <div v-if="slot.locked" class="bb-locked-label">Por decidir</div>
                    <div v-else-if="!slot.winner && slot.h !== null && slot.a !== null" class="bb-draw-label">Empate — elige ganador</div>
                  </div>
                  <!-- Connector arm (only if next level exists) -->
                  <div v-if="li < bracketBuilderState.length - 1" class="bb-arm" :class="si % 2 === 0 ? 'bb-arm--top' : 'bb-arm--bottom'"></div>
                </div>
              </div>
            </div>

            <!-- Trophy -->
            <div class="bb-col bb-col--trophy">
              <p class="bb-col__label">Campeón</p>
              <div class="bb-trophy-slot" :style="{ height: (bracketLevels.at(-1)?.slotH ?? 54) + 'px' }">
                <div :class="['bb-trophy', { 'bb-trophy--known': !!bbFinalWinner }]">
                  <Crown :size="20" style="color:var(--gold)" />
                  <template v-if="bbFinalWinner">
                    <span :class="`fi fi-${bbFinalWinner.flag} flag-sm`"></span>
                    <strong>{{ bbFinalWinner.name }}</strong>
                  </template>
                  <span v-else class="muted" style="font-size:0.65rem">Por decidir</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="bb-footer">
          <button class="btn btn--ghost" @click="showBracketBuilder = false">Cancelar</button>
          <button class="btn btn--primary" :disabled="bracketSaving" @click="saveBracketPreds">
            {{ bracketSaving ? 'Guardando…' : 'Guardar predicción' }}
          </button>
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
            <div class="help-section__icon help-section__icon--coral">📋</div>
            <div>
              <h3>Apuesta inicial — obligatoria</h3>
              <p>Antes de que empiece el torneo debes predecir <strong>todos los partidos</strong>: fase de grupos, 32avos, octavos, cuartos, semifinales y la final. Es tu apuesta de partida y queda guardada como registro permanente.</p>
              <p style="margin-top:6px;padding:8px 10px;border-radius:8px;background:rgba(251,191,36,0.08);border:1px solid rgba(251,191,36,0.2);color:var(--gold);font-size:0.78rem;line-height:1.5;">
                🏆 El ganador de tu Final predicha se convierte automáticamente en tu campeón.
              </p>
            </div>
          </div>

          <div class="help-section">
            <div class="help-section__icon help-section__icon--blue">🔒</div>
            <div>
              <h3>¿Cuándo puedo predecir o cambiar?</h3>
              <p>No hace falta predecirlo todo de golpe: puedes jugar <strong>día a día</strong>, prediciendo cada partido antes de que empiece. Estas son las reglas:</p>
              <div class="help-points" style="margin-top:8px">
                <div class="help-point">
                  <span class="help-point__val" style="font-size:0.7rem;min-width:64px">Grupos</span>
                  <span>Editables hasta que guardes tu apuesta inicial (ahí se congelan). Si no la guardas, puedes cambiarlos hasta 1h antes de cada partido</span>
                </div>
                <div class="help-point">
                  <span class="help-point__val" style="font-size:0.7rem;min-width:64px">Cruces</span>
                  <span>Editables hasta 1h antes de cada partido — los corriges según se van clasificando los equipos reales</span>
                </div>
                <div class="help-point">
                  <span class="help-point__val" style="font-size:0.7rem;min-width:64px">Inicial</span>
                  <span>Opcional: una foto de tu cuadro al arrancar, solo para el premio «Mejor apuesta inicial». Si no la haces, juegas y puntúas igual</span>
                </div>
              </div>
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

  <!-- ═══════════════ SETTINGS DRAWER ═══════════════ -->
  <Teleport to="body">
    <div v-if="showSettings" class="settings-backdrop" @click.self="showSettings = false">
      <div class="settings-drawer">

        <!-- Header -->
        <div class="settings-drawer__header">
          <div class="settings-drawer__title">
            <Settings :size="16" />
            Ajustes
          </div>
          <button class="btn--settings" @click="showSettings = false" style="border:none;background:none">
            <X :size="16" />
          </button>
        </div>

        <div class="settings-drawer__body">

          <!-- Tema -->
          <div class="settings-section">
            <div class="settings-section__label">Tema</div>
            <div class="theme-options">
              <button
                v-for="opt in themeOptions"
                :key="opt.value"
                :class="['theme-btn', settingsTheme === opt.value && 'theme-btn--active']"
                @click="setTheme(opt.value)"
              >
                <span class="theme-btn__icon">{{ opt.icon }}</span>
                <span class="theme-btn__label">{{ opt.label }}</span>
              </button>
            </div>
          </div>

          <!-- Vista compacta -->
          <div class="settings-section">
            <div class="settings-section__label">Visualización</div>
            <label class="settings-toggle-row" style="cursor:pointer">
              <div class="settings-toggle-row__info">
                <strong>Vista compacta</strong>
                <span>Tarjetas más pequeñas, más partidos en pantalla</span>
              </div>
              <label class="toggle-switch">
                <input type="checkbox" :checked="settingsCompact" @change="setCompact(($event.target as HTMLInputElement).checked)" />
                <span class="toggle-switch__track"></span>
              </label>
            </label>
          </div>

          <!-- Perfil -->
          <div class="settings-section">
            <div class="settings-section__label">Perfil</div>
            <div class="settings-profile">
              <div class="settings-profile__avatar">
                <div class="settings-avatar-img">
                  <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" :alt="currentUser.name" />
                  <span v-else>{{ currentUser?.name.slice(0,2).toUpperCase() }}</span>
                </div>
                <div class="settings-avatar-info">
                  <strong>{{ currentUser?.name }}</strong>
                  <span>{{ currentUser?.email }}</span>
                </div>
              </div>
              <div class="settings-name-form">
                <input
                  v-model="settingsName"
                  class="settings-name-input"
                  placeholder="Nombre a mostrar"
                  maxlength="40"
                  @keydown.enter="saveDisplayName"
                />
                <button
                  class="settings-btn-save"
                  :disabled="settingsNameSaving || settingsName.trim() === currentUser?.name"
                  @click="saveDisplayName"
                >
                  {{ settingsNameSaving ? '…' : 'Guardar' }}
                </button>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  </Teleport>

  <!-- ═══════════════ POOL SELECTOR ═══════════════ -->
  <Teleport to="body">
    <div v-if="showPoolSelector" class="pred-backdrop pool-selector-backdrop">
      <div class="pool-selector-card">
        <div class="pool-selector__header">
          <Trophy :size="22" style="color:var(--gold)" />
          <h2>Mis porras</h2>
          <button v-if="activePool" class="pred-modal__close" type="button" @click="showPoolSelector = false">✕</button>
        </div>

        <div class="pool-selector__list">
          <button
            v-for="pool in userPools"
            :key="pool.id"
            class="pool-selector__item"
            type="button"
            @click="selectPool(pool.id)"
          >
            <div class="pool-selector__item-name">{{ pool.name }}</div>
            <div class="pool-selector__item-meta">
              <span class="badge badge--open">{{ pool.role === 'ADMIN' ? 'Admin' : 'Jugador' }}</span>
              <span>Código: {{ pool.code }}</span>
            </div>
          </button>
        </div>

        <div class="pool-selector__divider">o</div>

        <!-- Unirse con código -->
        <div class="pool-selector__join">
          <input
            v-model="joinCodeInput"
            class="invite-input"
            type="text"
            placeholder="Código de invitación (ej: A3F7K2X9)"
            style="text-transform:uppercase;letter-spacing:0.08em"
            @keyup.enter="joinPoolWithCode"
          />
          <p v-if="joinCodeError" class="invite-error">{{ joinCodeError }}</p>
          <button class="btn btn--primary" style="width:100%;margin-top:6px" type="button" :disabled="!joinCodeInput.trim() || joinCodeLoading" @click="joinPoolWithCode">
            {{ joinCodeLoading ? 'Uniéndose…' : '🔗 Unirse a una porra' }}
          </button>
        </div>

        <div class="pool-selector__divider">o</div>

        <template v-if="!showCreateInSelector">
          <button class="btn btn--ghost" style="width:100%" type="button" @click="showCreateInSelector = true">
            ➕ Crear nueva porra
          </button>
        </template>
        <template v-else>
          <div class="invite-form" style="text-align:left;width:100%">
            <label class="invite-label">Descripción (opcional)</label>
            <input v-model="createPoolForm.description" class="invite-input" type="text" placeholder="Porra familiar del Mundial" style="width:100%;box-sizing:border-box" />
            <label class="invite-label">Inscripción por jugador (€)</label>
            <input v-model.number="createPoolForm.entryFee" class="invite-input" type="number" min="0" step="1" style="width:100%;box-sizing:border-box" />
            <p v-if="createPoolForm.error" class="invite-error">{{ createPoolForm.error }}</p>
            <div style="display:flex;gap:8px;margin-top:8px">
              <button class="btn btn--ghost" style="flex:1" type="button" @click="showCreateInSelector = false">Cancelar</button>
              <button class="btn btn--outline" style="flex:2" type="button" :disabled="createPoolForm.loading" @click="createPool">
                {{ createPoolForm.loading ? 'Creando…' : '🏆 Crear mi porra' }}
              </button>
            </div>
          </div>
        </template>

        <button class="btn btn--ghost btn--xs" style="margin-top:8px;width:100%" type="button" @click="signOut">
          Cambiar cuenta
        </button>
      </div>
    </div>
  </Teleport>

  <!-- ═══════════════ BRACKET PRED MODAL ═══════════════ -->
  <Teleport to="body">
    <div v-if="bracketPredModal.open" class="pred-backdrop" @click.self="bracketPredModal.open = false" @keydown.esc="bracketPredModal.open = false" tabindex="-1">
      <div class="pred-modal bpred-card" role="dialog" aria-modal="true">

        <!-- Header -->
        <div class="pred-modal__header">
          <div class="pred-modal__teams">
            <span :class="`fi fi-${bracketPredModal.homeFl} flag-lg`"></span>
            <strong>{{ bracketPredModal.home }}</strong>
            <span class="pred-modal__vs">vs</span>
            <strong>{{ bracketPredModal.away }}</strong>
            <span :class="`fi fi-${bracketPredModal.awayFl} flag-lg`"></span>
          </div>
          <button class="pred-modal__close" type="button" @click="bracketPredModal.open = false">✕</button>
        </div>

        <p v-if="bracketPredModal.home === 'Pendiente' || bracketPredModal.away === 'Pendiente'" class="bpred-hint" style="color:var(--amber)">
          ⚠️ Los equipos se conocerán cuando avance el torneo, pero puedes guardar ya el resultado.
        </p>
        <p v-else class="bpred-hint">¿Qué resultado predices al final del tiempo reglamentario?</p>

        <!-- Score inputs -->
        <div class="pred-modal__score">
          <div class="pred-score-block">
            <span :class="`fi fi-${bracketPredModal.homeFl} flag-sm`"></span>
            <label>{{ bracketPredModal.home }}</label>
            <div class="pred-stepper">
              <button type="button" @click="bracketPredModal.homeGoals = Math.max(0, bracketPredModal.homeGoals - 1)">−</button>
              <span class="pred-stepper__val">{{ bracketPredModal.homeGoals }}</span>
              <button type="button" @click="bracketPredModal.homeGoals++">+</button>
            </div>
          </div>
          <span class="pred-dash">—</span>
          <div class="pred-score-block">
            <span :class="`fi fi-${bracketPredModal.awayFl} flag-sm`"></span>
            <label>{{ bracketPredModal.away }}</label>
            <div class="pred-stepper">
              <button type="button" @click="bracketPredModal.awayGoals = Math.max(0, bracketPredModal.awayGoals - 1)">−</button>
              <span class="pred-stepper__val">{{ bracketPredModal.awayGoals }}</span>
              <button type="button" @click="bracketPredModal.awayGoals++">+</button>
            </div>
          </div>
        </div>

        <p v-if="bracketPredModal.homeGoals === bracketPredModal.awayGoals" class="bpred-tie-warn">
          ⚠️ Empate en tiempo reglamentario — si ocurre en eliminatorias, el cuadro avanzará al equipo favorito según la predicción de fase de grupos.
        </p>

        <!-- AI badge -->
        <div v-if="bracketPredModal.aiSource" class="pred-ai-block">
          <p class="pred-ai-badge">
            <Sparkles :size="12" />
            {{ bracketPredModal.aiSource.startsWith('gpt') ? 'Predicción generada por ChatGPT' : 'Predicción aleatoria (IA no disponible)' }}
          </p>
          <p v-if="bracketPredModal.aiReasoning" class="pred-ai-reasoning">{{ bracketPredModal.aiReasoning }}</p>
        </div>

        <!-- Actions -->
        <div class="pred-modal__actions">
          <button
            class="btn btn--outline btn--sm pred-btn-ai"
            type="button"
            :disabled="bracketPredModal.aiLoading"
            @click="askAiBracketPrediction"
          >
            <Sparkles :size="13" :class="{ 'spin': bracketPredModal.aiLoading }" />
            {{ bracketPredModal.aiLoading ? 'Preguntando a la IA…' : 'Predecir con IA' }}
          </button>
          <button class="btn btn--primary" type="button" :disabled="bracketPredModal.saving" @click="saveBracketPred">
            <Check :size="14" /> {{ bracketPredModal.saving ? '…' : 'Guardar predicción' }}
          </button>
        </div>

      </div>
    </div>
  </Teleport>

</template>
