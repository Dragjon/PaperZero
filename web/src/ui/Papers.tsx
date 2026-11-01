import { useEffect, useState } from 'react'
import { ArrowRight, NotebookPen, Shuffle, Timer } from 'lucide-react'
import { type PaperInfo, type Subject, SUBJECTS, isOLevel, paperTitle } from '../data/pack'
import * as pr from '../data/progress'
import { Chip, Sheet, SheetAction, SubjectTheme, TopBar, go, useDb, usePack } from './common'

type Source = 'All' | 'OLevel' | 'Schools'

/** A science's papers, in its colours (which change with it): the O-Level years first, then the prelims. */
export function Papers({ start }: { start: Subject }) {
  const pack = usePack()
  useDb()
  const [subject, setSubject] = useState(start)
  const [source, setSource] = useState<Source>('All')
  const [picked, setPicked] = useState<PaperInfo | null>(null)
  const p = pr.progress(pack)
  const subjects = SUBJECTS.filter((s) => pack.bySubject[s].length > 0)
  const papers = pack.papers
    .filter((x) => x.subject === subject && (source === 'All' || (source === 'OLevel') === isOLevel(x)))
    .sort((a, b) => Number(!isOLevel(a)) - Number(!isOLevel(b)) || (b.year ?? 0) - (a.year ?? 0) || (a.source ?? '').localeCompare(b.source ?? ''))
  const timedTitle = `${subject} timed set`

  return (
    <SubjectTheme subject={subject} fill className="screen">
      <TopBar title="Papers" />
      <div className="chips">{subjects.map((s) => <Chip key={s} label={s} on={s === subject} onClick={() => setSubject(s)} />)}</div>
      <div className="chips" style={{ paddingTop: 8 }}>
        <Chip label="All" subtle on={source === 'All'} onClick={() => setSource('All')} />
        <Chip label="O-Level" subtle on={source === 'OLevel'} onClick={() => setSource('OLevel')} />
        <Chip label="School prelims" subtle on={source === 'Schools'} onClick={() => setSource('Schools')} />
      </div>
      <div className="content pad-bottom" style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingTop: 16 }}>
        <TimedSet
          papers={pack.papers.filter((x) => x.subject === subject).length}
          unfinished={!!pr.unfinishedTimed(timedTitle)}
          onStart={async () => go(`quiz/${await pr.startTimed(pack, subject, timedTitle)}`)}
        />
        {papers.map((paper) => {
          const best = p.best.get(paper.id)
          return (
            <button key={paper.id} className="card press" onClick={() => setPicked(paper)} style={{ display: 'flex', alignItems: 'center', padding: '16px 20px', textAlign: 'left' }}>
              <span style={{ flex: 1 }}>
                <span className="title-m" style={{ display: 'block' }}>{paperTitle(paper)}</span>
                <span className="body-m muted">{best ? `${paper.count} questions · best ${best.score}/${best.total}` : `${paper.count} questions`}</span>
              </span>
              {best && <span className="title-m muted">{Math.floor((best.score * 100) / Math.max(1, best.total))}%</span>}
            </button>
          )
        })}
      </div>
      {picked && (
        <Sheet onClose={() => setPicked(null)}>
          <SubjectTheme subject={subject}>
            <div className="title-l" style={{ padding: '0 4px 12px' }}>{subject} · {paperTitle(picked)}</div>
            <SheetAction icon={<NotebookPen size={22} />} title="Practise" description="Each answer marked as you go, with the working where there is some" onClick={async () => go(`quiz/${await pr.startPaper(pack, picked.id, 'Practice')}`)} />
            <SheetAction icon={<Timer size={22} />} title="Exam" description="The whole paper in an hour, marked at the end" onClick={async () => go(`quiz/${await pr.startPaper(pack, picked.id, 'Exam')}`)} />
          </SubjectTheme>
        </Sheet>
      )}
    </SubjectTheme>
  )
}

/** A timed set to make: a fanned stack of papers, what you'll get, and a button that shuffles it. */
function TimedSet({ papers, unfinished, onStart }: { papers: number; unfinished: boolean; onStart: () => void }) {
  const [shuffling, setShuffling] = useState(false)
  useEffect(() => {
    if (!shuffling) return
    const still = matchMedia('(prefers-reduced-motion: reduce)').matches
    const t = setTimeout(onStart, still ? 0 : 700)
    return () => clearTimeout(t)
  }, [shuffling])
  const go = () => !shuffling && (unfinished ? onStart() : setShuffling(true))
  return (
    <button className="card press" onClick={go} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 16, textAlign: 'left', background: 'var(--primary-container)', color: 'var(--on-primary-container)' }}>
      <svg width="64" height="72" viewBox="0 0 64 72" style={{ flex: 'none', overflow: 'visible' }} aria-hidden>
        {[0, 1, 2, 3].map((i) => (
          <g key={i} className={shuffling ? `shuffle shuffle-${i}` : ''} style={{ transformOrigin: '32px 36px', transform: `rotate(${(i - 1.5) * 7}deg)` }}>
            <rect x="12" y="8" width="40" height="56" rx="5" fill="#000" opacity=".18" transform="translate(1.5 2.5)" />
            <rect x="12" y="8" width="40" height="56" rx="5" fill="#F7F3EE" />
            {[0, 1, 2, 3].map((k) => <line key={k} x1="18" x2={k === 3 ? 34 : 46} y1={20 + k * 8} y2={20 + k * 8} stroke="var(--primary)" strokeOpacity=".35" strokeWidth="2" strokeLinecap="round" />)}
            <circle cx="42" cy="53" r="3.6" fill="var(--primary)" />
          </g>
        ))}
      </svg>
      <span style={{ flex: 1 }}>
        <span className="title-m" style={{ display: 'block' }}>Timed set</span>
        <span className="body-m" style={{ display: 'block', opacity: 0.8 }}>
          {shuffling ? `Picking 40 questions from ${papers} papers…` : unfinished ? 'You have a set on the go: pick up where you left off' : '40 random questions from across the papers · 1 hour · marked at the end'}
        </span>
        <span className="pill small" style={{ marginTop: 10 }}>{unfinished ? <ArrowRight size={18} /> : <Shuffle size={18} />}{unfinished ? 'Continue' : 'New set'}</span>
      </span>
    </button>
  )
}
