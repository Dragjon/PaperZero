/**
 * What's been done, kept in the browser (IndexedDB), as the app keeps it on the phone: sittings,
 * answers, likes, comments and time studied. Held in memory too, so screens read it at once; each
 * change is written through and the screens told.
 */

export type Mode = 'Practice' | 'Exam'

export interface Session {
  id: number
  mode: Mode
  paperId: string | null
  title: string
  /** Its questions' keys, in order; empty for a feed, whose questions are picked as it goes. */
  keys: string[]
  startedAt: number
  finishedAt: number | null
  spentMs: number
  score: number
  total: number
}

export interface Answer {
  id: number
  sessionId: number
  questionKey: string
  chosen: string
  correct: boolean
  at: number
}

export interface Like {
  questionKey: string
  at: number
}

export interface Comment {
  id: number
  questionKey: string
  text: string
  at: number
}

/** Time spent on a science on a day ("2026-11-01"). */
export interface Study {
  day: string
  subject: string
  ms: number
}

interface State {
  sessions: Session[]
  answers: Answer[]
  likes: Like[]
  comments: Comment[]
  study: Study[]
}

const NAME = 'paperzero'
const STORES = ['sessions', 'answers', 'likes', 'comments', 'study'] as const
type StoreName = (typeof STORES)[number]

let db: IDBDatabase | null = null
let state: State = { sessions: [], answers: [], likes: [], comments: [], study: [] }
let version = 0
const listeners = new Set<() => void>()

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const r = indexedDB.open(NAME, 1)
    r.onupgradeneeded = () => {
      const d = r.result
      d.createObjectStore('sessions', { keyPath: 'id', autoIncrement: true })
      d.createObjectStore('answers', { keyPath: 'id', autoIncrement: true })
      d.createObjectStore('likes', { keyPath: 'questionKey' })
      d.createObjectStore('comments', { keyPath: 'id', autoIncrement: true })
      d.createObjectStore('study', { keyPath: ['day', 'subject'] })
    }
    r.onsuccess = () => resolve(r.result)
    r.onerror = () => reject(r.error)
  })
}

function all<T>(store: StoreName): Promise<T[]> {
  return new Promise((resolve, reject) => {
    const r = db!.transaction(store).objectStore(store).getAll()
    r.onsuccess = () => resolve(r.result as T[])
    r.onerror = () => reject(r.error)
  })
}

function write(store: StoreName, f: (s: IDBObjectStore) => IDBRequest): Promise<IDBValidKey | undefined> {
  return new Promise((resolve, reject) => {
    if (!db) return resolve(undefined)
    const t = db.transaction(store, 'readwrite')
    const r = f(t.objectStore(store))
    t.oncomplete = () => resolve(r.result as IDBValidKey)
    t.onerror = () => reject(t.error)
  })
}

function changed() {
  version++
  listeners.forEach((l) => l())
}

/** Reads what's kept. If the browser won't keep anything (a private window), it's kept for this visit only. */
export async function loadDb(): Promise<void> {
  try {
    db = await open()
    const [sessions, answers, likes, comments, study] = await Promise.all([
      all<Session>('sessions'),
      all<Answer>('answers'),
      all<Like>('likes'),
      all<Comment>('comments'),
      all<Study>('study'),
    ])
    answers.sort((a, b) => a.at - b.at)
    state = { sessions, answers, likes, comments, study }
  } catch {
    db = null
  }
  changed()
}

export function getState(): State {
  return state
}

export function subscribe(l: () => void): () => void {
  listeners.add(l)
  return () => listeners.delete(l)
}

export function getVersion(): number {
  return version
}

let nextLocalId = -1

export async function insertSession(s: Omit<Session, 'id'>): Promise<number> {
  const id = ((await write('sessions', (o) => o.add(s))) as number | undefined) ?? nextLocalId--
  state = { ...state, sessions: [...state.sessions, { ...s, id }] }
  changed()
  return id
}

export async function updateSession(id: number, change: Partial<Session>) {
  const s = state.sessions.find((x) => x.id === id)
  if (!s) return
  const next = { ...s, ...change }
  state = { ...state, sessions: state.sessions.map((x) => (x.id === id ? next : x)) }
  changed()
  await write('sessions', (o) => o.put(next))
}

export async function deleteSession(id: number) {
  state = { ...state, sessions: state.sessions.filter((x) => x.id !== id), answers: state.answers.filter((a) => a.sessionId !== id) }
  changed()
  await write('sessions', (o) => o.delete(id))
  for (const a of (await all<Answer>('answers').catch(() => [])).filter((a) => a.sessionId === id)) await write('answers', (o) => o.delete(a.id))
}

export async function insertAnswer(a: Omit<Answer, 'id'>): Promise<void> {
  const id = ((await write('answers', (o) => o.add(a))) as number | undefined) ?? nextLocalId--
  state = { ...state, answers: [...state.answers, { ...a, id }] }
  changed()
}

export async function deleteAnswer(id: number) {
  state = { ...state, answers: state.answers.filter((a) => a.id !== id) }
  changed()
  await write('answers', (o) => o.delete(id))
}

export async function setLike(key: string, liked: boolean) {
  if (liked) {
    const like = { questionKey: key, at: Date.now() }
    state = { ...state, likes: [...state.likes.filter((l) => l.questionKey !== key), like] }
    changed()
    await write('likes', (o) => o.put(like))
  } else {
    state = { ...state, likes: state.likes.filter((l) => l.questionKey !== key) }
    changed()
    await write('likes', (o) => o.delete(key))
  }
}

export async function addComment(key: string, text: string) {
  const c = { questionKey: key, text, at: Date.now() }
  const id = ((await write('comments', (o) => o.add(c))) as number | undefined) ?? nextLocalId--
  state = { ...state, comments: [...state.comments, { ...c, id }] }
  changed()
}

export async function deleteComment(id: number) {
  state = { ...state, comments: state.comments.filter((c) => c.id !== id) }
  changed()
  await write('comments', (o) => o.delete(id))
}

/** Counts [ms] more on [subject] today. Not announced each second: stats read it when they're opened. */
export async function addStudy(subject: string, ms: number) {
  const day = today()
  const old = state.study.find((s) => s.day === day && s.subject === subject)
  const next = { day, subject, ms: (old?.ms ?? 0) + ms }
  state = { ...state, study: [...state.study.filter((s) => s !== old), next] }
  await write('study', (o) => o.put(next))
}

async function clear(store: StoreName) {
  await write(store, (o) => o.clear())
}

/** Every answer, sitting and second studied gone; likes and comments stay. */
export async function resetProgress() {
  state = { ...state, sessions: [], answers: [], study: [] }
  changed()
  await Promise.all([clear('sessions'), clear('answers'), clear('study')])
}

export async function deleteSaved() {
  state = { ...state, likes: [], comments: [] }
  changed()
  await Promise.all([clear('likes'), clear('comments')])
}

/** Today in the browser's time zone, "2026-11-01". */
export function today(at: number = Date.now()): string {
  const d = new Date(at)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
