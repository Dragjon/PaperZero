import { useEffect, useMemo, useRef, useState } from 'react'
import { Check, Heart, Lightbulb, Lock, MessageCircle, Send, Share2, Smile, Trash2, X } from 'lucide-react'
import * as db from '../data/db'
import { fetchMemes, type Meme } from '../data/memes'
import { type Question, type Subject, paperTitle, subjectOf } from '../data/pack'
import * as pr from '../data/progress'
import { MEME_EVERY, useSettings } from '../data/settings'
import { QImage, SUBJECT_ICON, Sheet, Splash, SubjectTheme, back, go, useDb, usePack } from './common'

export type FeedSource = { kind: 'subject'; subject: Subject; topic?: number } | { kind: 'saved'; title: string; keys: string[]; start: number }

type Page = { kind: 'ask'; index: number; q: Question } | { kind: 'meme'; meme: Meme; n: number }

/** Left this long without a swipe or an answer, a feed stops counting the time. */
const IDLE_MS = 120_000
const MEME_AHEAD = 15
const MEME_RETRY_MS = 10_000
const MEME_RETRY_MAX_MS = 60_000

/**
 * Questions as a feed, one to a screen, swiped up for the next, on black. A subject's feed never
 * ends: once every question has gone by they come round again, ones not yet tried first.
 */
export function Feed({ source }: { source: FeedSource }) {
  const pack = usePack()
  const state = useDb()
  const settings = useSettings()
  const title = source.kind === 'subject'
    ? (source.topic != null ? pack.topics[source.subject]?.[source.topic] : undefined) ?? source.subject
    : source.title
  const [feed, setFeed] = useState<Question[]>([])
  const [chosen, setChosen] = useState<Map<number, string>>(new Map())
  const [session, setSession] = useState(0)
  const [page, setPage] = useState(0)
  const [zoom, setZoom] = useState<string | null>(null)
  const [commenting, setCommenting] = useState<Question | null>(null)
  const touched = useRef(Date.now())
  const scroller = useRef<HTMLDivElement>(null)

  // Memes between the questions, when turned on: about one in MEME_EVERY, never on a beat.
  const [memes, setMemes] = useState<Meme[]>([])
  const [slots, setSlots] = useState<number[]>([])
  const [shown, setShown] = useState<Map<number, number>>(new Map())
  const [misses, setMisses] = useState(0)
  const fetching = useRef(false)

  useEffect(() => {
    pr.startScroll(title).then(setSession)
    if (source.kind === 'subject') setFeed(pr.feed(pack, source.subject, pr.progress(pack).tried, source.topic))
    else {
      setFeed(source.keys.map((k) => pack.questions.get(k)).filter((q): q is Question => !!q))
      requestAnimationFrame(() => scroller.current?.scrollTo({ top: source.start * innerHeight }))
    }
  }, [])

  const pages: Page[] = useMemo(() => {
    const out: Page[] = []
    feed.forEach((q, i) => {
      out.push({ kind: 'ask', index: i, q })
      const k = settings.memes ? shown.get(i) : undefined
      if (k != null && memes[k]) out.push({ kind: 'meme', meme: memes[k], n: k })
    })
    return out
  }, [feed, shown, memes, settings.memes])

  const here = (() => {
    const p = pages[page]
    if (!p) return 0
    if (p.kind === 'ask') return p.index
    for (const [at, k] of shown) if (k === p.n) return at
    return 0
  })()

  // Near the end of a subject's feed, its questions come round again.
  useEffect(() => {
    if (source.kind === 'subject' && feed.length && page > pages.length - 4) setFeed((f) => [...f, ...pr.feed(pack, source.subject, new Set(), source.topic)])
  }, [page, pages.length])

  // Meme slots, a gap of about MEME_EVERY questions each.
  useEffect(() => {
    if (!settings.memes || !feed.length) return
    const every = MEME_EVERY[settings.memeFrequency]
    const gap = () => Math.max(2, Math.floor((every + 1) / 2) + Math.floor(Math.random() * (every + 1)))
    setSlots((s) => {
      const out = [...s]
      let next = out.length ? out[out.length - 1] + gap() : gap()
      while (next < feed.length) { out.push(next); next += gap() }
      return out
    })
  }, [feed.length, settings.memes, settings.memeFrequency])

  // Each meme, as it comes, to the next free slot ahead; fetched only for the slots a little ahead.
  useEffect(() => {
    if (!settings.memes) return
    let free = shown.size
    const next = new Map(shown)
    for (const at of slots) if (at > here && !next.has(at) && free < memes.length) next.set(at, free++)
    if (next.size !== shown.size) setShown(next)
    const wanted = next.size + slots.filter((at) => at > here && at <= here + MEME_AHEAD && !next.has(at)).length + 1
    if (memes.length >= wanted || fetching.current) return
    fetching.current = true
    const wait = misses ? Math.min(MEME_RETRY_MS * 2 ** Math.min(misses - 1, 2), MEME_RETRY_MAX_MS) : 0
    const t = setTimeout(async () => {
      const got = await fetchMemes(source.kind === 'subject' ? source.subject : undefined, new Set(memes.map((m) => m.link)))
      fetching.current = false
      if (got.length) { setMisses(0); setMemes((m) => [...m, ...got]) } else setMisses((m) => m + 1)
    }, wait)
    return () => { clearTimeout(t); fetching.current = false }
  }, [slots, memes.length, misses, here, settings.memes])

  // Time studied, a second at a time, to the science of the question on screen, while in use.
  useEffect(() => {
    const t = setInterval(() => {
      if (document.hidden || Date.now() - touched.current > IDLE_MS) return
      const p = pages[page]
      const s = p?.kind === 'ask' ? subjectOf(pack, p.q.key) : undefined
      if (s) db.addStudy(s, 1000)
    }, 1000)
    return () => clearInterval(t)
  }, [pages, page])

  const onScroll = () => {
    const el = scroller.current!
    touched.current = Date.now()
    const p = Math.round(el.scrollTop / el.clientHeight)
    if (p !== page) setPage(p)
  }

  const tried = chosen.size
  const right = [...chosen.entries()].filter(([i, l]) => feed[i]?.answer === l).length
  const liked = new Set(state.likes.map((l) => l.questionKey))

  return (
    <div className="feed">
      <div className="feed-scroller" ref={scroller} onScroll={onScroll}>
        {pages.length === 0 && <div className="feed-page" style={{ alignItems: 'center' }}><div className="spinner" /></div>}
        {pages.map((p, i) => (
          <div className="feed-page" key={p.kind === 'ask' ? `q${p.index}` : `m${p.n}`}>
            {Math.abs(i - page) > 2 ? null : p.kind === 'ask' ? (
              <SubjectTheme subject={source.kind === 'saved' ? subjectOf(pack, p.q.key) : null} className="feed-inner">
                <QuestionPage
                  q={p.q}
                  mine={chosen.get(p.index)}
                  liked={liked.has(p.q.key)}
                  comments={state.comments.filter((c) => c.questionKey === p.q.key)}
                  onZoom={setZoom}
                  onLike={(v) => db.setLike(p.q.key, v)}
                  onComments={() => setCommenting(p.q)}
                  onTopic={(t) => { const s = subjectOf(pack, p.q.key); if (s) go(`feed/${s}?topic=${t}`) }}
                  onChoose={(l) => {
                    touched.current = Date.now()
                    setChosen((m) => new Map(m).set(p.index, l))
                    pr.answer(session, p.q, l)
                  }}
                  onUndo={() => {
                    setChosen((m) => { const n = new Map(m); n.delete(p.index); return n })
                    pr.undo(session, p.q)
                  }}
                />
              </SubjectTheme>
            ) : <MemePage meme={p.meme} />}
          </div>
        ))}
      </div>
      <div className="feed-top">
        <button className="icon-btn" onClick={back} aria-label="Close"><X size={24} /></button>
        <div className="title-l" style={{ flex: 1, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{title}</div>
        {tried > 0 && <div className="title-m ink-soft" style={{ paddingRight: 12 }}>{right}/{tried} right</div>}
      </div>
      {settings.memes && misses >= 2 && (
        <div className="glass label-m" style={{ position: 'absolute', top: 'calc(max(env(safe-area-inset-top), 4px) + 60px)', left: '50%', transform: 'translateX(-50%)', padding: '8px 14px', borderRadius: 999, zIndex: 3, whiteSpace: 'nowrap' }}>
          Memes can't load right now: no internet, or blocked here
        </div>
      )}
      {commenting && <Comments q={commenting} onClose={() => setCommenting(null)} />}
      {zoom && <Zoom src={zoom} onClose={() => setZoom(null)} />}
    </div>
  )
}

function QuestionPage({ q, mine, liked, comments, onZoom, onLike, onComments, onTopic, onChoose, onUndo }: {
  q: Question; mine?: string; liked: boolean; comments: db.Comment[]
  onZoom: (src: string) => void; onLike: (v: boolean) => void; onComments: () => void; onTopic: (t: number) => void
  onChoose: (l: string) => void; onUndo: () => void
}) {
  const pack = usePack()
  const paper = pack.paper(q.paperId)!
  const Icon = SUBJECT_ICON[paper.subject]
  const [hearts, setHearts] = useState(0)
  const lastTap = useRef(0)
  const tapTimer = useRef<number>()
  const [splashed, setSplashed] = useState<string | null>(null)
  const topics = q.topics.map((t) => [pack.topics[paper.subject]?.[t], t] as const).filter(([n]) => n)

  const onPicture = () => {
    const now = Date.now()
    if (now - lastTap.current < 300) {
      clearTimeout(tapTimer.current)
      lastTap.current = 0
      if (!liked) onLike(true)
      setHearts((h) => h + 1)
    } else {
      lastTap.current = now
      tapTimer.current = window.setTimeout(() => onZoom(q.image), 300)
    }
  }

  const share = async () => {
    const text = `Can you get this one? ${paperTitle(paper)}, Question ${q.number}. From PaperZero.`
    try {
      const blob = await (await fetch(q.image)).blob()
      const file = new File([blob], 'question.webp', { type: blob.type || 'image/webp' })
      if (navigator.canShare?.({ files: [file] })) return await navigator.share({ files: [file], text })
      if (navigator.share) return await navigator.share({ text, url: location.href.split('#')[0] })
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = `${paper.id}-q${q.number}.webp`
      a.click()
    } catch {
      // Sharing put away: nothing to do.
    }
  }

  return (
    <>
      <div className="feed-picture" onClick={onPicture}>
        <QImage src={q.image} />
        {hearts > 0 && <Heart key={hearts} className="heart-pop" size={120} fill="var(--primary)" color="var(--primary)" />}
      </div>
      <div style={{ display: 'flex', alignItems: 'flex-end', padding: '8px 8px 0 16px', gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ width: 36, height: 36, borderRadius: 18, display: 'grid', placeItems: 'center', background: 'var(--primary-container)', color: 'var(--on-primary-container)', flex: 'none' }}><Icon size={20} /></span>
            <span>
              <span className="title-s" style={{ display: 'block' }}>{paperTitle(paper)}</span>
              <span className="body-s ink-soft">{paper.subject} · Question {q.number}</span>
            </span>
          </div>
          {topics.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {topics.map(([name, t]) => <button key={t} className="tag glass" onClick={() => onTopic(t)}># {name}</button>)}
            </div>
          )}
          {mine && (
            <div className="body-m" style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
              {mine === q.answer ? <Check size={18} color="var(--success)" /> : <X size={18} color="var(--error)" />}
              {mine === q.answer ? 'Right' : `Not quite: it's ${q.answer}`}
              <button className="label-l ink-soft" style={{ padding: '4px 8px', borderRadius: 999 }} onClick={onUndo}>Misclicked? Undo</button>
            </div>
          )}
          {comments.length > 0 && <button className="body-m ink-soft" style={{ textAlign: 'left' }} onClick={onComments}>{comments[comments.length - 1].text}</button>}
        </div>
        <div className="rail">
          <button onClick={() => onLike(!liked)} aria-label={liked ? 'Unlike' : 'Like'}>
            <Heart size={30} fill={liked ? 'var(--primary)' : 'none'} color={liked ? 'var(--primary)' : '#fff'} />Like
          </button>
          <button onClick={onComments}><MessageCircle size={30} fill="#fff" />{comments.length ? comments.length : 'Comment'}</button>
          {mine && q.explain && <button onClick={() => onZoom(q.explain!)}><Lightbulb size={30} />Working</button>}
          <button onClick={share}><Share2 size={30} />Share</button>
        </div>
      </div>
      <div className="letters">
        {['A', 'B', 'C', 'D'].map((l) => {
          const [bg, ink] = mine == null ? ['rgba(255,255,255,.14)', '#fff']
            : l === q.answer ? ['var(--success)', '#000']
            : l === mine ? ['var(--error)', 'var(--on-error)']
            : ['rgba(255,255,255,.14)', 'rgba(255,255,255,.3)']
          return (
            <button key={l} className="letter" style={{ background: bg, color: ink }} onClick={() => {
              if (mine != null) return
              onChoose(l)
              if (l === q.answer) setSplashed(l)
            }}>
              {l}
              {splashed === l && mine === l && <Splash key={String(mine)} />}
            </button>
          )
        })}
      </div>
    </>
  )
}

function MemePage({ meme }: { meme: Meme }) {
  return (
    <div className="feed-inner">
      <div className="feed-picture">
        <img src={meme.src} alt={meme.title} referrerPolicy="no-referrer" style={{ width: '100%', height: '100%', objectFit: 'contain' }} />
      </div>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, padding: '8px 8px 24px 16px' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ width: 36, height: 36, borderRadius: 18, display: 'grid', placeItems: 'center', background: 'var(--tertiary-container)', color: 'var(--on-tertiary-container)' }}><Smile size={20} /></span>
            <span>
              <span className="title-s" style={{ display: 'block' }}>{meme.source}</span>
              <span className="body-s ink-soft">Meme · a break from the questions</span>
            </span>
          </div>
          {meme.title && <div className="body-m" style={{ marginTop: 6 }}>{meme.title}</div>}
        </div>
        <div className="rail">
          <button onClick={() => navigator.share ? navigator.share({ text: meme.title, url: meme.link }).catch(() => {}) : window.open(meme.link, '_blank', 'noopener')}><Share2 size={30} />Share</button>
        </div>
      </div>
    </div>
  )
}

/** A question's comments, newest at the foot, and a field to add one. They're for you alone. */
export function Comments({ q, onClose }: { q: Question; onClose: () => void }) {
  const state = useDb()
  const [draft, setDraft] = useState('')
  const list = state.comments.filter((c) => c.questionKey === q.key).sort((a, b) => a.at - b.at)
  const send = () => {
    if (!draft.trim()) return
    db.addComment(q.key, draft.trim())
    setDraft('')
  }
  return (
    <div style={{ color: 'var(--on-surface)' }}>
      <Sheet onClose={onClose}>
        <div className="title-l" style={{ padding: '0 4px' }}>Comments</div>
        <div className="body-s muted" style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 4px 12px' }}>
          <Lock size={14} />Comments are private: only you can see them, and they stay in this browser.
        </div>
        {list.length === 0 && <div className="body-m muted" style={{ padding: '0 4px 12px' }}>No comments yet. Leave yourself a note: a hint, a mistake you made, a formula.</div>}
        {list.map((c) => (
          <div key={c.id} style={{ display: 'flex', alignItems: 'flex-start', padding: '4px 0' }}>
            <div style={{ flex: 1, padding: '0 4px' }}>
              <div className="body-l">{c.text}</div>
              <div className="body-s muted">{new Date(c.at).toLocaleString()}</div>
            </div>
            <button className="icon-btn" onClick={() => db.deleteComment(c.id)} aria-label="Delete comment"><Trash2 size={20} color="var(--on-surface-variant)" /></button>
          </div>
        ))}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, paddingTop: 8 }}>
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && send()}
            placeholder="Add a comment…"
            style={{ flex: 1, padding: '14px 16px', borderRadius: 24, border: 0, background: 'var(--surface-high)', outline: 'none' }}
          />
          <button className="icon-btn" onClick={send} disabled={!draft.trim()} aria-label="Send"><Send size={22} color={draft.trim() ? 'var(--primary)' : 'var(--on-surface-variant)'} /></button>
        </div>
      </Sheet>
    </div>
  )
}

/** A picture full screen, to pinch closer. */
export function Zoom({ src, onClose }: { src: string; onClose: () => void }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: '#fff', zIndex: 70, overflow: 'auto', touchAction: 'pinch-zoom pan-x pan-y' }} className="q-img" onClick={onClose}>
      <img src={src} alt="" style={{ width: '100%', minHeight: '100%', objectFit: 'contain' }} />
      <button className="icon-btn" style={{ position: 'fixed', top: 'max(env(safe-area-inset-top), 8px)', right: 8, color: '#000' }} aria-label="Close" onClick={onClose}><X size={24} /></button>
    </div>
  )
}
