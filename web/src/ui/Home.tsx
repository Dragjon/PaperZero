import { useRef, useState } from 'react'
import { Bookmark, ChevronRight, RotateCcw, Settings as Gear, Sparkles, Hand } from 'lucide-react'
import { type Subject, SUBJECTS, paperTitle } from '../data/pack'
import { duration, percent, progress, stats } from '../data/progress'
import { BubbleRing, BubbleSpiral } from './bubbles'
import { ChalkOlaf, ZeroLogo } from './olaf'
import { QImage, Sheet, SheetAction, SUBJECT_ICON, SubjectTheme, go, olafQuote, useDb, usePack } from './common'

/** Home: each science's feed as a story to tap into, what you've saved, and the papers by science. */
export function Home({ dark }: { dark: boolean }) {
  const pack = usePack()
  const state = useDb()
  const p = progress(pack)
  const s = stats(pack)
  const [choosing, setChoosing] = useState(false)
  const [entering, setEntering] = useState<{ subject: Subject; x: number; y: number; color: string } | null>(null)
  const subjects = SUBJECTS.filter((x) => pack.bySubject[x].length > 0)
  const liked = [...state.likes].sort((a, b) => b.at - a.at).map((l) => l.questionKey).filter((k) => pack.questions.has(k))
  const commentedAt = new Map<string, number>()
  state.comments.forEach((c) => commentedAt.set(c.questionKey, Math.max(commentedAt.get(c.questionKey) ?? 0, c.at)))
  const commented = [...commentedAt.entries()].sort((a, b) => b[1] - a[1]).map(([k]) => k).filter((k) => pack.questions.has(k))
  const toFix = p.mistakes.length

  return (
    <div className="screen">
      <div className={`content pad-top ${entering ? 'tipping' : ''}`} style={{ paddingBottom: 140 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 4px 0' }}>
          <ZeroLogo size={48} />
          <div className="display" style={{ flex: 1 }}>PaperZero</div>
          <button className="icon-btn" onClick={() => go('settings')} aria-label="Settings"><Gear size={24} /></button>
        </div>
        <div className="body-l muted" style={{ padding: '4px 4px 0' }}>Paper 1 questions from O-Level and school prelims, to do again and again</div>
        <ChalkOlaf dark={dark} style={{ width: '100%', height: 170, marginTop: 8 }} />
        <div className="body-l muted" style={{ textAlign: 'center', fontStyle: 'italic', padding: '6px 24px 0' }}>“{olafQuote()}”</div>
        <div className="label-l" style={{ textAlign: 'center', opacity: 0.7, color: 'var(--on-surface-variant)' }}>— Olaf</div>

        {subjects.length === 0 ? (
          <div className="body-l muted" style={{ textAlign: 'center', paddingTop: 48 }}>This build has no papers in it.</div>
        ) : (
          <>
            <div style={{ display: 'flex', gap: 16, padding: '20px 0 8px', overflowX: 'auto' }}>
              {subjects.map((sub) => (
                <SubjectTheme key={sub} subject={sub}>
                  <Story subject={sub} onTap={(x, y, color) => !entering && setEntering({ subject: sub, x, y, color })} />
                </SubjectTheme>
              ))}
            </div>

            <button className="card press" onClick={() => go('stats')} style={{ width: '100%', display: 'flex', alignItems: 'center', gap: 16, padding: '16px 20px', textAlign: 'left', marginTop: 8 }}>
              <Sparkles size={24} color="var(--primary)" />
              <span style={{ flex: 1 }}>
                <span className="title-m" style={{ display: 'block' }}>Stats</span>
                <span className="body-m muted">
                  {s.tally.answered === 0 && s.studiedMs < 60_000
                    ? 'Time studied, streaks, and how you do by topic, once you start.'
                    : [s.streak > 0 ? `${s.streak} day streak` : null, `${duration(s.days.reduce((a, d) => a + d.ms, 0))} this week`, percent(s.tally) != null ? `${percent(s.tally)}% right` : null].filter(Boolean).join(' · ')}
                </span>
              </span>
              <ChevronRight size={20} />
            </button>

            <Head title="Liked" onAll={liked.length ? () => go('saved/liked') : undefined} />
            {liked.length === 0 ? <Hint text="Questions you like show up here. Double-tap one in your feed, or tap its heart." /> : (
              <div style={{ display: 'flex', gap: 2 }}>
                {liked.slice(0, 3).map((k, i) => <Tile key={k} src={pack.questions.get(k)!.image} onClick={() => go(`savedfeed/liked/${i}`)} />)}
                {Array.from({ length: 3 - Math.min(3, liked.length) }, (_, i) => <div key={i} style={{ flex: 1 }} />)}
              </div>
            )}
            <Head title="Commented" onAll={commented.length ? () => go('saved/commented') : undefined} />
            {commented.length === 0 ? <Hint text="Questions you comment on show up here, with your notes." /> : commented.slice(0, 3).map((k, i) => {
              const q = pack.questions.get(k)!
              const last = state.comments.filter((c) => c.questionKey === k).sort((a, b) => b.at - a.at)[0]
              return (
                <button key={k} onClick={() => go(`savedfeed/commented/${i}`)} style={{ display: 'flex', alignItems: 'center', gap: 12, width: '100%', textAlign: 'left', padding: '4px 0' }}>
                  <div style={{ width: 56, height: 56, borderRadius: 12, overflow: 'hidden', flex: 'none' }}><Tile src={q.image} /></div>
                  <span style={{ minWidth: 0 }}>
                    <span className="title-s" style={{ display: 'block' }}>{paperTitle(pack.paper(q.paperId)!)} · Q{q.number}</span>
                    <span className="body-m muted" style={{ display: 'block', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{last?.text}</span>
                  </span>
                </button>
              )
            })}

            <Head title="Papers" />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {subjects.map((sub) => {
                const sp = p.subjects[sub]
                const Icon = SUBJECT_ICON[sub]
                return (
                  <SubjectTheme key={sub} subject={sub}>
                    <button className="card press" onClick={() => go(`papers/${sub}`)} style={{ width: '100%', padding: 20, textAlign: 'left' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
                        <span style={{ width: 48, height: 48, borderRadius: 24, display: 'grid', placeItems: 'center', background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}><Icon size={24} /></span>
                        <span>
                          <span className="title-l" style={{ display: 'block' }}>{sub}</span>
                          <span className="body-m muted">{sp.tried === 0 ? `${sp.total} questions · not started` : `${sp.total} questions · ${sp.tried} tried · ${Math.floor((sp.right * 100) / sp.tried)}% right`}</span>
                        </span>
                      </div>
                      {sp.tried > 0 && (
                        <div className="bar" style={{ marginTop: 16 }}>
                          <i style={{ width: `${(sp.tried * 100) / sp.total}%`, background: 'var(--outline)' }} />
                          <i style={{ width: `${(sp.right * 100) / sp.total}%`, background: 'var(--success)' }} />
                        </div>
                      )}
                    </button>
                  </SubjectTheme>
                )
              })}
            </div>
            {toFix > 0 && (
              <div style={{ marginTop: 12 }}>
                <SheetAction icon={<RotateCcw size={22} />} title={`${toFix} mistake${toFix === 1 ? '' : 's'} to fix`} description="A question you get wrong comes back here until you get it right twice running." onClick={() => go('mistakes')} />
              </div>
            )}
          </>
        )}
      </div>

      <div className="dock-fade" />
      <div className="dock-wrap">
        <div className="dock">
          <button className="dock-action" onClick={() => go('saved/liked')}><Bookmark size={22} />Saved</button>
          <button className="pill press" onClick={() => setChoosing(true)}><Hand size={20} />Doomscroll</button>
          <button className="dock-action" onClick={() => go('mistakes')}><RotateCcw size={22} />Mistakes</button>
        </div>
      </div>

      {choosing && (
        <Sheet onClose={() => setChoosing(false)}>
          <div className="title-l" style={{ padding: '0 4px' }}>Doomscroll</div>
          <div className="body-m muted" style={{ padding: '4px 4px 16px' }}>An endless feed of a subject's questions. Like them, comment on them, or tap a letter to try one.</div>
          {subjects.map((sub) => {
            const Icon = SUBJECT_ICON[sub]
            return (
              <SubjectTheme key={sub} subject={sub}>
                <SheetAction icon={<Icon size={22} />} title={sub} onClick={() => { setChoosing(false); go(`feed/${sub}`) }} />
              </SubjectTheme>
            )
          })}
        </Sheet>
      )}
      {entering && <BubbleSpiral from={entering} color={entering.color} seed={SUBJECTS.indexOf(entering.subject)} onDone={() => go(`feed/${entering.subject}`)} />}
    </div>
  )
}

function Story({ subject, onTap }: { subject: Subject; onTap: (x: number, y: number, color: string) => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const Icon = SUBJECT_ICON[subject]
  return (
    <button
      style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}
      onClick={() => {
        const r = ref.current!.getBoundingClientRect()
        onTap(r.left + r.width / 2, r.top + r.height / 2, getComputedStyle(ref.current!).getPropertyValue('--primary').trim() || '#ff6a3d')
      }}
    >
      <div ref={ref} style={{ width: 92, height: 92, position: 'relative', display: 'grid', placeItems: 'center' }}>
        <BubbleRing size={92} radius={41} biggest={6} seed={SUBJECTS.indexOf(subject)} />
        <span style={{ width: 66, height: 66, borderRadius: '50%', display: 'grid', placeItems: 'center', background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}><Icon size={30} /></span>
      </div>
      <span className="label-l">{subject}</span>
    </button>
  )
}

function Head({ title, onAll }: { title: string; onAll?: () => void }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', padding: '20px 4px 8px' }}>
      <div className="title-m" style={{ flex: 1 }}>{title}</div>
      {onAll && <button className="text-btn" onClick={onAll}>See all</button>}
    </div>
  )
}

function Hint({ text }: { text: string }) {
  return <div className="body-m muted" style={{ padding: '0 4px' }}>{text}</div>
}

/** A question's top, square, as a grid's tile. */
export function Tile({ src, onClick }: { src: string; onClick?: () => void }) {
  return (
    <button onClick={onClick} style={{ flex: 1, aspectRatio: '1', overflow: 'hidden', background: '#fff', display: 'block', width: '100%' }}>
      <QImage src={src} style={{ width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top left', padding: 4 }} />
    </button>
  )
}
