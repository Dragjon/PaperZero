import { RotateCcw } from 'lucide-react'
import { SUBJECTS, paperTitle, subjectOf } from '../data/pack'
import * as pr from '../data/progress'
import { Chip, SheetAction, SUBJECT_ICON, SubjectTheme, TopBar, go, useDb, usePack } from './common'
import { Tile } from './Home'

/** What's been got wrong and not yet put right, by subject, to practise again; and each one, to remove a misclick. */
export function Mistakes() {
  const pack = usePack()
  useDb()
  const mistakes = pr.progress(pack).mistakes
  return (
    <div className="screen">
      <TopBar title="Mistakes" />
      <div className="content" style={{ paddingBottom: 140 }}>
        <div className="body-l muted" style={{ padding: '0 4px 16px' }}>
          {mistakes.length ? 'A question you get wrong comes back here until you get it right twice running.' : 'No mistakes to fix. Wrong answers come here to practise again.'}
        </div>
        {SUBJECTS.map((s) => {
          const keys = mistakes.filter((k) => subjectOf(pack, k) === s)
          if (!keys.length) return null
          const Icon = SUBJECT_ICON[s]
          return (
            <SubjectTheme key={s} subject={s}>
              <SheetAction icon={<Icon size={22} />} title={s} description={`${keys.length} mistake${keys.length === 1 ? '' : 's'} to fix`}
                onClick={async () => go(`quiz/${await pr.startMistakes(keys, `Mistakes · ${s}`)}`)} />
            </SubjectTheme>
          )
        })}
        {mistakes.length > 0 && (
          <>
            <div className="title-m" style={{ padding: '20px 4px 0' }}>Each mistake</div>
            <div className="body-m muted" style={{ padding: '0 4px 8px' }}>Tapped the wrong letter by accident? Remove it, and it won't count in your stats.</div>
            {mistakes.map((k) => {
              const q = pack.questions.get(k)
              if (!q) return null
              return (
                <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '4px 0' }}>
                  <div style={{ width: 56, height: 56, borderRadius: 12, overflow: 'hidden', flex: 'none' }}><Tile src={q.image} /></div>
                  <div className="title-s" style={{ flex: 1 }}>{paperTitle(pack.paper(q.paperId)!)} · Q{q.number}</div>
                  <button className="text-btn" style={{ color: 'var(--on-surface-variant)' }} onClick={() => pr.forgetMistake(k)}>Remove</button>
                </div>
              )
            })}
          </>
        )}
      </div>
      {mistakes.length > 0 && (
        <div className="dock-wrap">
          <div className="dock" style={{ justifyContent: 'center' }}>
            <button className="pill press" onClick={async () => go(`quiz/${await pr.startMistakes(mistakes, 'Mistakes')}`)}><RotateCcw size={20} />Practise mistakes</button>
          </div>
        </div>
      )}
    </div>
  )
}

/** The liked and commented questions' keys, newest first. */
export function useSavedKeys(tab: 'liked' | 'commented'): string[] {
  const state = useDb()
  const pack = usePack()
  if (tab === 'liked') return [...state.likes].sort((a, b) => b.at - a.at).map((l) => l.questionKey).filter((k) => pack.questions.has(k))
  const at = new Map<string, number>()
  state.comments.forEach((c) => at.set(c.questionKey, Math.max(at.get(c.questionKey) ?? 0, c.at)))
  return [...at.entries()].sort((a, b) => b[1] - a[1]).map(([k]) => k).filter((k) => pack.questions.has(k))
}

/** What's been liked or commented on, as a grid, to open as a feed. */
export function Saved({ tab }: { tab: 'liked' | 'commented' }) {
  const pack = usePack()
  const keys = useSavedKeys(tab)
  return (
    <div className="screen">
      <TopBar title="Saved" />
      <div className="chips">
        <Chip label="Liked" on={tab === 'liked'} onClick={() => go('saved/liked', true)} />
        <Chip label="Commented" on={tab === 'commented'} onClick={() => go('saved/commented', true)} />
      </div>
      <div className="content pad-bottom" style={{ paddingTop: 16 }}>
        {keys.length === 0 ? (
          <div className="body-l muted" style={{ textAlign: 'center', paddingTop: 48 }}>
            {tab === 'liked' ? 'Questions you like show up here. Double-tap one in your feed, or tap its heart.' : 'Questions you comment on show up here, with your notes.'}
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 2 }}>
            {keys.map((k, i) => <Tile key={k} src={pack.questions.get(k)!.image} onClick={() => go(`savedfeed/${tab}/${i}`)} />)}
          </div>
        )}
      </div>
    </div>
  )
}

/** Time studied, streaks, how you do by science and by topic. */
export function StatsScreen() {
  const pack = usePack()
  useDb()
  const s = pr.stats(pack)
  const maxDay = Math.max(1, ...s.days.map((d) => d.ms))
  const enough = s.topics.filter((t) => t.tally.answered >= pr.ENOUGH)
  const weakest = [...enough].sort((a, b) => (pr.percent(a.tally) ?? 0) - (pr.percent(b.tally) ?? 0)).slice(0, 3)
  const strongest = [...enough].sort((a, b) => (pr.percent(b.tally) ?? 0) - (pr.percent(a.tally) ?? 0)).slice(0, 3)
  const weekday = (day: string) => new Date(day + 'T12:00').toLocaleDateString(undefined, { weekday: 'narrow' })
  const topicRow = (t: pr.TopicStats, showSubject: boolean) => (
    <SubjectTheme key={`${t.subject}${t.topic}`} subject={t.subject}>
      <button className="card press" onClick={() => go(`feed/${t.subject}?topic=${t.topic}`)} style={{ width: '100%', padding: '14px 18px', textAlign: 'left', marginBottom: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <span style={{ flex: 1 }}>
            <span className="title-s" style={{ display: 'block', color: t.tally.answered ? undefined : 'var(--on-surface-variant)' }}>{t.name}</span>
            <span className="body-s muted">{showSubject ? `${t.subject} · ` : ''}{t.tally.answered ? `${t.tally.answered} answers · ${t.tried} of ${t.questions} questions tried` : `Not tried yet · ${t.questions} questions`}</span>
          </span>
          <span className="title-m" style={{ color: 'var(--primary)' }}>{pr.percent(t.tally) != null ? `${pr.percent(t.tally)}%` : ''}</span>
        </div>
        {t.tally.answered > 0 && <div className="bar" style={{ marginTop: 10 }}><i style={{ width: `${pr.percent(t.tally)}%`, background: 'var(--primary)' }} /></div>}
      </button>
    </SubjectTheme>
  )
  return (
    <div className="screen">
      <TopBar title="Stats" />
      <div className="content pad-bottom">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
          <Big value={pr.duration(s.studiedMs)} label="studied in all" />
          <Big value={String(s.streak)} label={`day streak${s.longestStreak > s.streak ? ` · best ${s.longestStreak}` : ''}`} />
          <Big value={pr.percent(s.tally) != null ? `${pr.percent(s.tally)}%` : '–'} label={`right · ${s.tally.answered} answers`} />
        </div>
        {s.fixed > 0 && <div className="body-m muted" style={{ padding: '12px 4px 0' }}>{s.fixed} mistake{s.fixed === 1 ? '' : 's'} put right: wrong once, since right twice running.</div>}
        <div className="card" style={{ padding: 20, marginTop: 12 }}>
          <div className="title-m">This week</div>
          <div className="body-m muted">{pr.duration(s.days.reduce((a, d) => a + d.ms, 0))}{pr.percent(s.week) != null ? ` · ${pr.percent(s.week)}% right` : ''}</div>
          <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: 120, marginTop: 16 }}>
            {s.days.map((d, i) => (
              <div key={d.day} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, height: '100%', justifyContent: 'flex-end' }}>
                <div style={{ width: '100%', maxWidth: 28, height: `${Math.max(4, (d.ms / maxDay) * 90)}px`, borderRadius: 8, background: i === 6 ? 'var(--primary)' : 'var(--surface-highest)' }} />
                <span className="body-s muted">{weekday(d.day)}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="title-m" style={{ padding: '20px 4px 8px' }}>By science</div>
        {s.subjects.map((ss) => (
          <SubjectTheme key={ss.subject} subject={ss.subject}>
            <div className="card" style={{ padding: 20, marginBottom: 8 }}>
              <div style={{ display: 'flex', alignItems: 'center' }}>
                <span style={{ flex: 1 }}>
                  <span className="title-m" style={{ display: 'block' }}>{ss.subject}</span>
                  <span className="body-m muted">{pr.duration(ss.studiedMs)} · {ss.tally.answered} answers</span>
                </span>
                <span className="headline" style={{ color: 'var(--primary)' }}>{pr.percent(ss.tally) != null ? `${pr.percent(ss.tally)}%` : '–'}</span>
              </div>
              <div className="bar" style={{ marginTop: 14 }}><i style={{ width: `${(ss.tried * 100) / Math.max(1, ss.questions)}%`, background: 'var(--primary)' }} /></div>
              <div className="body-s muted" style={{ marginTop: 6 }}>{ss.tried} of {ss.questions} questions tried</div>
            </div>
          </SubjectTheme>
        ))}
        {weakest.length > 0 && (
          <>
            <div className="title-m" style={{ padding: '20px 4px 0' }}>Needs work</div>
            <div className="body-s muted" style={{ padding: '0 4px 8px' }}>Your least right topics, with 5 answers or more. Tap one to practise it.</div>
            {weakest.map((t) => topicRow(t, true))}
            <div className="title-m" style={{ padding: '20px 4px 8px' }}>Going well</div>
            {strongest.map((t) => topicRow(t, true))}
          </>
        )}
        <div className="title-m" style={{ padding: '20px 4px 0' }}>By topic</div>
        <div className="body-s muted" style={{ padding: '0 4px 8px' }}>Topics follow the BPGHS notes' chapters. Tap one for a feed of only its questions.</div>
        {s.topics.map((t) => topicRow(t, false))}
      </div>
    </div>
  )
}

function Big({ value, label }: { value: string; label: string }) {
  return (
    <div className="card" style={{ padding: 16 }}>
      <div className="headline" style={{ whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{value}</div>
      <div className="body-s muted" style={{ minHeight: 32 }}>{label}</div>
    </div>
  )
}
