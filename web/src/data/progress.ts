/** What's been answered and what to do next: the app's Progresses and Stats, for the browser. */
import * as db from './db'
import { type Pack, type Question, type Subject, SUBJECTS, paperTitle, subjectOf } from './pack'

export const MISTAKES_AT_ONCE = 20
/** Right this many times running after a mistake, a question is fixed. */
export const FIXED_AFTER = 2
/** A timed set is as long as a Paper 1. */
export const TIMED_QUESTIONS = 40

export interface SubjectProgress {
  subject: Subject
  total: number
  tried: number
  right: number
  toFix: number
}

export interface Best {
  score: number
  total: number
}

export interface Progress {
  subjects: Record<Subject, SubjectProgress>
  best: Map<string, Best>
  /** Questions to practise again, newest mistake first. */
  mistakes: string[]
  tried: Set<string>
}

/** Questions still to fix, newest mistake first: answered wrong, and not right FIXED_AFTER times running since. */
export function mistakes(answers: db.Answer[]): string[] {
  const streak = new Map<string, number>()
  const wrongAt = new Map<string, number>()
  for (const a of answers) {
    if (a.correct) {
      if (wrongAt.has(a.questionKey)) streak.set(a.questionKey, (streak.get(a.questionKey) ?? 0) + 1)
    } else {
      wrongAt.set(a.questionKey, a.at)
      streak.set(a.questionKey, 0)
    }
  }
  return [...wrongAt.entries()]
    .filter(([k]) => (streak.get(k) ?? 0) < FIXED_AFTER)
    .sort((a, b) => b[1] - a[1])
    .map(([k]) => k)
}

export function progress(pack: Pack): Progress {
  const { answers, sessions } = db.getState()
  const mis = mistakes(answers)
  const last = new Map<string, boolean>()
  answers.forEach((a) => last.set(a.questionKey, a.correct))
  const subjects = {} as Record<Subject, SubjectProgress>
  for (const s of SUBJECTS) {
    const ids = new Set(pack.papers.filter((p) => p.subject === s).map((p) => p.id))
    const inS = (k: string) => ids.has(k.slice(0, k.lastIndexOf('/')))
    const mine = [...last.entries()].filter(([k]) => inS(k))
    subjects[s] = {
      subject: s,
      total: pack.bySubject[s].length,
      tried: mine.length,
      right: mine.filter(([, v]) => v).length,
      toFix: mis.filter(inS).length,
    }
  }
  const best = new Map<string, Best>()
  for (const s of sessions) {
    if (s.paperId == null || s.finishedAt == null || s.total === 0) continue
    const b = best.get(s.paperId)
    if (!b || s.score > b.score) best.set(s.paperId, { score: s.score, total: s.total })
  }
  return { subjects, best, mistakes: mis, tried: new Set(last.keys()) }
}

function shuffled<T>(xs: T[]): T[] {
  const a = [...xs]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/** [subject]'s questions (on [topic], if one) in a fresh order: ones not yet tried first. */
export function feed(pack: Pack, subject: Subject, tried: Set<string>, topic?: number): Question[] {
  const all = pack.bySubject[subject].filter((q) => topic == null || q.topics.includes(topic))
  return [...shuffled(all.filter((q) => !tried.has(q.key))), ...shuffled(all.filter((q) => tried.has(q.key)))]
}

/** A whole paper, in [mode]; an unfinished sitting of it in that mode is carried on instead. */
export async function startPaper(pack: Pack, paperId: string, mode: db.Mode): Promise<number> {
  const open = db.getState().sessions.filter((s) => s.paperId === paperId && s.finishedAt == null).sort((a, b) => b.startedAt - a.startedAt)[0]
  if (open && open.mode === mode) return open.id
  const p = pack.paper(paperId)!
  return db.insertSession({
    mode, paperId, title: paperTitle(p), keys: pack.ofPaper(paperId).map((q) => q.key),
    startedAt: Date.now(), finishedAt: null, spentMs: 0, score: 0, total: 0,
  })
}

export function startScroll(title: string): Promise<number> {
  return db.insertSession({ mode: 'Practice', paperId: null, title, keys: [], startedAt: Date.now(), finishedAt: null, spentMs: 0, score: 0, total: 0 })
}

export function unfinishedTimed(title: string): db.Session | undefined {
  return db.getState().sessions
    .filter((s) => s.paperId == null && s.mode === 'Exam' && s.title === title && s.finishedAt == null)
    .sort((a, b) => b.startedAt - a.startedAt)[0]
}

/** A timed set: TIMED_QUESTIONS of [subject]'s questions from across its papers, in Exam mode. */
export async function startTimed(pack: Pack, subject: Subject, title: string): Promise<number> {
  const open = unfinishedTimed(title)
  if (open) return open.id
  const keys = shuffled(pack.bySubject[subject]).slice(0, TIMED_QUESTIONS).map((q) => q.key)
  return db.insertSession({ mode: 'Exam', paperId: null, title, keys, startedAt: Date.now(), finishedAt: null, spentMs: 0, score: 0, total: 0 })
}

export function startMistakes(keys: string[], title: string): Promise<number> {
  return db.insertSession({
    mode: 'Practice', paperId: null, title, keys: keys.slice(0, MISTAKES_AT_ONCE),
    startedAt: Date.now(), finishedAt: null, spentMs: 0, score: 0, total: 0,
  })
}

/** Keeps [chosen] as the answer to [q] in [sessionId]; true if it's right. */
export async function answer(sessionId: number, q: Question, chosen: string): Promise<boolean> {
  const right = chosen === q.answer
  await db.insertAnswer({ sessionId, questionKey: q.key, chosen, correct: right, at: Date.now() })
  return right
}

/** Takes back the answer just given to [q] in [sessionId] (a slip of the finger): as if never given. */
export async function undo(sessionId: number, q: Question) {
  const last = db.getState().answers.filter((a) => a.sessionId === sessionId && a.questionKey === q.key).sort((a, b) => b.at - a.at)[0]
  if (last) await db.deleteAnswer(last.id)
}

/** Forgets the last wrong answer to [key]: a misclick found later. */
export async function forgetMistake(key: string) {
  const last = db.getState().answers.filter((a) => a.questionKey === key && !a.correct).sort((a, b) => b.at - a.at)[0]
  if (last) await db.deleteAnswer(last.id)
}

/** The latest answer in [sessionId] to each of its questions. */
export function answersOf(sessionId: number): Map<string, db.Answer> {
  const m = new Map<string, db.Answer>()
  db.getState().answers.filter((a) => a.sessionId === sessionId).forEach((a) => m.set(a.questionKey, a))
  return m
}

/** Marks the sitting: right answers out of its questions. */
export async function finish(sessionId: number): Promise<Best> {
  const s = db.getState().sessions.find((x) => x.id === sessionId)
  if (!s) return { score: 0, total: 0 }
  const given = answersOf(sessionId)
  const score = s.keys.filter((k) => given.get(k)?.correct).length
  await db.updateSession(sessionId, { finishedAt: Date.now(), score, total: s.keys.length })
  return { score, total: s.keys.length }
}

// ---------------------------------------------------------------- stats

export interface Tally {
  answered: number
  right: number
}

export const percent = (t: Tally) => (t.answered === 0 ? null : Math.floor((t.right * 100) / t.answered))

export interface TopicStats {
  subject: Subject
  topic: number
  name: string
  tally: Tally
  tried: number
  questions: number
}

export interface SubjectStats {
  subject: Subject
  tally: Tally
  studiedMs: number
  tried: number
  questions: number
}

export interface Stats {
  studiedMs: number
  /** The last seven days, oldest first, with time spent. */
  days: { day: string; ms: number }[]
  tally: Tally
  week: Tally
  streak: number
  longestStreak: number
  fixed: number
  subjects: SubjectStats[]
  topics: TopicStats[]
}

/** A topic needs this many answers before it's called strong or weak. */
export const ENOUGH = 5

function dayBefore(day: string, n = 1): string {
  const [y, m, d] = day.split('-').map(Number)
  return db.today(new Date(y, m - 1, d - n, 12).getTime())
}

export function stats(pack: Pack): Stats {
  const { answers, study } = db.getState()
  const today = db.today()
  const weekStart = dayBefore(today, 6)
  const bySubject = new Map<Subject, Tally>()
  const byTopic = new Map<string, Tally>()
  const triedTopic = new Map<string, Set<string>>()
  const tally: Tally = { answered: 0, right: 0 }
  const week: Tally = { answered: 0, right: 0 }
  const active = new Set<string>()
  const add = (t: Tally, ok: boolean) => { t.answered++; if (ok) t.right++ }
  for (const a of answers) {
    const s = subjectOf(pack, a.questionKey)
    if (!s) continue
    add(tally, a.correct)
    if (!bySubject.has(s)) bySubject.set(s, { answered: 0, right: 0 })
    add(bySubject.get(s)!, a.correct)
    const day = db.today(a.at)
    active.add(day)
    if (day >= weekStart) add(week, a.correct)
    for (const t of pack.questions.get(a.questionKey)?.topics ?? []) {
      const k = `${s}/${t}`
      if (!byTopic.has(k)) byTopic.set(k, { answered: 0, right: 0 })
      add(byTopic.get(k)!, a.correct)
      if (!triedTopic.has(k)) triedTopic.set(k, new Set())
      triedTopic.get(k)!.add(a.questionKey)
    }
  }
  const spent = new Map<string, number>()
  const spentBySubject = new Map<string, number>()
  for (const d of study) {
    spent.set(d.day, (spent.get(d.day) ?? 0) + d.ms)
    spentBySubject.set(d.subject, (spentBySubject.get(d.subject) ?? 0) + d.ms)
    if (d.ms >= 60_000) active.add(d.day)
  }
  // Streaks: days running with something done, up to today (or yesterday, if nothing yet today).
  let streak = 0
  let day = active.has(today) ? today : dayBefore(today)
  while (active.has(day)) { streak++; day = dayBefore(day) }
  let longest = 0
  let run = 0
  let last: string | null = null
  for (const d of [...active].sort()) {
    run = last != null && last === dayBefore(d) ? run + 1 : 1
    longest = Math.max(longest, run)
    last = d
  }
  const tried = new Set(answers.map((a) => a.questionKey))
  const subjects = SUBJECTS.filter((s) => pack.bySubject[s].length > 0).map((s) => ({
    subject: s,
    tally: bySubject.get(s) ?? { answered: 0, right: 0 },
    studiedMs: spentBySubject.get(s) ?? 0,
    tried: pack.bySubject[s].filter((q) => tried.has(q.key)).length,
    questions: pack.bySubject[s].length,
  }))
  const topics = subjects.flatMap((ss) => {
    const names = pack.topics[ss.subject] ?? []
    const counts = names.map(() => 0)
    pack.bySubject[ss.subject].forEach((q) => q.topics.forEach((t) => { if (t < counts.length) counts[t]++ }))
    return names.map((name, i) => ({
      subject: ss.subject, topic: i, name,
      tally: byTopic.get(`${ss.subject}/${i}`) ?? { answered: 0, right: 0 },
      tried: triedTopic.get(`${ss.subject}/${i}`)?.size ?? 0,
      questions: counts[i],
    }))
  })
  const wrong = new Set(answers.filter((a) => !a.correct).map((a) => a.questionKey))
  mistakes(answers).forEach((k) => wrong.delete(k))
  return {
    studiedMs: [...spent.values()].reduce((a, b) => a + b, 0),
    days: [6, 5, 4, 3, 2, 1, 0].map((n) => { const d = dayBefore(today, n); return { day: d, ms: spent.get(d) ?? 0 } }),
    tally, week, streak, longestStreak: longest, fixed: wrong.size, subjects, topics,
  }
}

/** "1 h 05 min", "12 min", "45 s". */
export function duration(ms: number): string {
  const s = Math.floor(ms / 1000)
  if (s >= 3600) return `${Math.floor(s / 3600)} h ${String(Math.floor(s / 60) % 60).padStart(2, '0')} min`
  if (s >= 60) return `${Math.floor(s / 60)} min`
  return `${s} s`
}
