import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, Check, Flag, LayoutGrid, X } from 'lucide-react'
import * as db from '../data/db'
import { type Question, subjectOf } from '../data/pack'
import * as pr from '../data/progress'
import { useSettings } from '../data/settings'
import { Dialog, QImage, Sheet, Splash, SubjectTheme, back, go, useDb, usePack } from './common'
import { Zoom } from './Feed'

/** A whole paper (or a timed set) is given this long in Exam mode, as in the real one. */
const EXAM_MS = 60 * 60 * 1000

/**
 * One question at a time, A to D under it. In Practice each answer is marked at once; in Exam the
 * paper is marked at the end, against the clock. [review] shows a finished sitting.
 */
export function Quiz({ id, review }: { id: number; review?: boolean }) {
  const pack = usePack()
  const state = useDb()
  const settings = useSettings()
  const s = state.sessions.find((x) => x.id === id)
  const questions = (s?.keys ?? []).map((k) => pack.questions.get(k)).filter((q): q is Question => !!q)
  const given = pr.answersOf(id)
  const [index, setIndex] = useState(() => (review ? 0 : Math.max(0, questions.findIndex((q) => !given.has(q.key)))))
  const [spent, setSpent] = useState(s?.spentMs ?? 0)
  const [grid, setGrid] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [finishing, setFinishing] = useState(false)
  const [zoom, setZoom] = useState<string | null>(null)
  const [splashed, setSplashed] = useState<string | null>(null)
  const done = useRef(false)

  const exam = s?.mode === 'Exam' && !review
  const q = questions[index]

  const finish = async () => {
    if (done.current) return
    done.current = true
    await pr.finish(id)
    go(`result/${id}`, true)
  }

  // The time spent answering, kept as it goes: time away from the page doesn't count.
  useEffect(() => {
    if (review || !s) return
    const t = setInterval(() => {
      if (document.hidden) return
      setSpent((v) => {
        const next = v + 1000
        db.updateSession(id, { spentMs: next })
        const sub = q && subjectOf(pack, q.key)
        if (sub) db.addStudy(sub, 1000)
        if (exam && next >= EXAM_MS) finish()
        return next
      })
    }, 1000)
    return () => clearInterval(t)
  }, [q?.key, review, exam])

  if (!s || !q) return <div className="screen" style={{ display: 'grid', placeItems: 'center' }}><div className="spinner" /></div>

  const mine = given.get(q.key)?.chosen
  const marked = review || (!exam && mine != null)
  const last = index === questions.length - 1
  const unanswered = questions.filter((x) => !given.has(x.key)).length
  const tryLeave = () => (review || given.size === 0 ? back() : setLeaving(true))
  const subjects = [...new Set(questions.map((x) => subjectOf(pack, x.key)))]
  const left = Math.max(0, EXAM_MS - spent) / 1000

  return (
    <SubjectTheme subject={subjects.length === 1 ? subjects[0] : null} fill className="screen">
      <div className="topbar">
        <button className="icon-btn" onClick={tryLeave} aria-label="Close"><X size={24} /></button>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div className="title-m">Question {index + 1} of {questions.length}</div>
          <div className="body-s muted" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.title}</div>
        </div>
        {exam && settings.examClock && (
          <div className="title-m" style={{ padding: '0 8px', color: left < 300 ? 'var(--error)' : 'var(--on-surface-variant)' }}>
            {Math.floor(left / 60)}:{String(Math.floor(left % 60)).padStart(2, '0')}
          </div>
        )}
        <button className="icon-btn" onClick={() => setGrid(true)} aria-label="All questions"><LayoutGrid size={22} /></button>
      </div>

      <div className="content" style={{ flex: 1, paddingBottom: 16 }}>
        <button style={{ display: 'block', width: '100%' }} onClick={() => setZoom(q.image)}>
          <QImage src={q.image} style={{ width: '100%', borderRadius: 24, padding: 8 }} />
        </button>
        {marked && (
          <div style={{ marginTop: 16 }}>
            <div className="title-m" style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', paddingLeft: 4 }}>
              {mine === q.answer ? <Check color="var(--success)" /> : <X color="var(--error)" />}
              {mine == null ? `Not answered · Not quite: it's ${q.answer}` : mine === q.answer ? 'Right' : `Not quite: it's ${q.answer}`}
              {!review && mine != null && (
                <button className="text-btn" style={{ color: 'var(--on-surface-variant)' }} onClick={() => pr.undo(id, q)}>Misclicked? Undo</button>
              )}
            </div>
            {q.explain && (
              <>
                <div className="title-s muted" style={{ padding: '12px 4px 6px' }}>How it's worked out</div>
                <button style={{ display: 'block', width: '100%' }} onClick={() => setZoom(q.explain!)}><QImage src={q.explain} style={{ width: '100%', borderRadius: 24, padding: 8 }} /></button>
              </>
            )}
          </div>
        )}
      </div>

      <div style={{ background: 'var(--surface)', padding: '12px 16px calc(env(safe-area-inset-bottom) + 12px)', position: 'sticky', bottom: 0 }}>
        <div style={{ display: 'flex', gap: 10, maxWidth: 520, margin: '0 auto' }}>
          {['A', 'B', 'C', 'D'].map((l) => {
            const [bg, ink] = marked
              ? l === q.answer ? ['var(--success)', 'var(--surface)'] : l === mine ? ['var(--error)', 'var(--on-error)'] : ['var(--surface-high)', 'color-mix(in srgb, var(--on-surface-variant) 50%, transparent)']
              : l === mine ? ['var(--primary)', 'var(--on-primary)'] : ['var(--surface-high)', 'var(--on-surface)']
            return (
              <button key={l} className="press" style={{ flex: 1, height: 64, borderRadius: 24, background: bg, color: ink, fontSize: 24, fontWeight: 600, position: 'relative' }}
                onClick={() => {
                  if (review || (!exam && mine != null)) return
                  pr.answer(id, q, l)
                  if (!exam && l === q.answer) setSplashed(q.key + l)
                }}>
                {l}
                {splashed === q.key + l && mine === l && <Splash />}
              </button>
            )
          })}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', marginTop: 12, maxWidth: 720, marginInline: 'auto' }}>
          <button className="text-btn" disabled={index === 0} style={{ display: 'flex', alignItems: 'center', gap: 6, opacity: index === 0 ? 0.4 : 1 }} onClick={() => setIndex(index - 1)}>
            <ArrowLeft size={18} />Back
          </button>
          <div style={{ flex: 1 }} />
          {review && last ? <button className="pill press" onClick={back}><Check size={20} />Done</button>
            : !review && last && (exam || mine != null) ? <button className="pill press" onClick={() => (unanswered > 0 ? setFinishing(true) : finish())}><Flag size={20} />Finish</button>
            : <button className="pill press" onClick={() => !last && setIndex(index + 1)}><ArrowRight size={20} />Next</button>}
        </div>
      </div>

      {grid && (
        <Sheet onClose={() => setGrid(false)}>
          <div className="title-l" style={{ padding: '0 4px 12px' }}>All questions</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {questions.map((x, i) => {
              const m = given.get(x.key)?.chosen
              const [bg, ink] = m == null ? ['var(--surface-high)', 'var(--on-surface-variant)']
                : !(review || !exam) ? ['var(--primary-container)', 'var(--on-primary-container)']
                : m === x.answer ? ['var(--success)', 'var(--surface)'] : ['var(--error)', 'var(--on-error)']
              return (
                <button key={x.key} className="label-l" onClick={() => { setIndex(i); setGrid(false) }}
                  style={{ width: 48, height: 48, borderRadius: 24, background: bg, color: ink, outline: i === index ? '2px solid var(--on-surface)' : 'none' }}>{i + 1}</button>
              )
            })}
          </div>
        </Sheet>
      )}
      {finishing && (
        <Dialog title="Finish now?" text={`${unanswered} question${unanswered === 1 ? ' has' : 's have'} no answer yet. ${unanswered === 1 ? "It'll" : "They'll"} count as wrong.`}
          confirm="Finish" cancel="Keep going" onConfirm={() => { setFinishing(false); finish() }} onCancel={() => setFinishing(false)} />
      )}
      {leaving && (
        <Dialog title="Leave this paper?" text="Your answers are kept: open the paper again to carry on." confirm="Leave" cancel="Keep going"
          onConfirm={() => { setLeaving(false); back() }} onCancel={() => setLeaving(false)} />
      )}
      {zoom && <Zoom src={zoom} onClose={() => setZoom(null)} />}
    </SubjectTheme>
  )
}

/** How a sitting went: the score, the time, and what next. */
export function Result({ id }: { id: number }) {
  const pack = usePack()
  const state = useDb()
  const s = state.sessions.find((x) => x.id === id)
  if (!s) return null
  const given = pr.answersOf(id)
  const wrong = s.keys.filter((k) => !given.get(k)?.correct)
  const subjects = [...new Set(s.keys.map((k) => subjectOf(pack, k)))]
  return (
    <SubjectTheme subject={subjects.length === 1 ? subjects[0] : null} fill className="screen">
      <div className="content pad-top pad-bottom" style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div className="title-m muted" style={{ marginTop: 32 }}>{s.title}</div>
        <div style={{ fontSize: 72, fontWeight: 600, lineHeight: 1.1, marginTop: 24, color: 'var(--primary)' }}>{s.score}</div>
        <div className="title-l muted">out of {s.total}</div>
        <div className="body-l muted" style={{ marginTop: 8 }}>{Math.floor((s.score * 100) / Math.max(1, s.total))}% · {pr.duration(s.spentMs)}</div>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, width: '100%', maxWidth: 420 }}>
          <button className="pill press" onClick={() => go(`review/${id}`)}>Review</button>
          {wrong.length > 0 && <button className="card press title-m" style={{ padding: 18 }} onClick={async () => go(`quiz/${await pr.startMistakes(wrong, s.title)}`, true)}>Practise the wrong ones</button>}
          <button className="text-btn" onClick={() => go('', true)}>Done</button>
        </div>
      </div>
    </SubjectTheme>
  )
}
