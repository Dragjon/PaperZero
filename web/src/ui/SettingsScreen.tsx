import { useState, type ReactNode } from 'react'
import { Code, Download, ExternalLink } from 'lucide-react'
import * as db from '../data/db'
import { type Settings, updateSettings, useSettings } from '../data/settings'
import { Chip, Dialog, Switch, TopBar } from './common'

const GITHUB = 'https://github.com/Dragjon/PaperZero'

/**
 * The settings: how the site looks and reads, how practice goes, memes in the feeds, and what's
 * kept in this browser, to start again from nothing.
 */
export function SettingsScreen({ install }: { install: (() => void) | null }) {
  const s = useSettings()
  const [confirming, setConfirming] = useState<'progress' | 'saved' | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const set = (c: Partial<Settings>) => updateSettings(c)
  return (
    <div className="screen">
      <TopBar title="Settings" />
      <div className="content pad-bottom" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <Section title="Look">
          <Choice title="Theme" options={[['Device', 'Device'], ['Light', 'Light'], ['Dark', 'Dark']]} value={s.theme} onChoose={(v) => set({ theme: v })} />
          <Choice title="Background" description="Black is for the dark theme, and saves battery on OLED screens" options={[['Warm', 'Warm'], ['Neutral', 'Gray'], ['Black', 'Black']]} value={s.ground} onChoose={(v) => set({ ground: v })} />
          <Toggle title="Colours for each science" description="Biology green, Chemistry violet, Physics blue; off, the site's orange throughout" on={s.scienceColours} onSet={(v) => set({ scienceColours: v })} />
        </Section>
        <Section title="Reading">
          <Choice title="Text size" description="Questions are pictures: tap one to look closer, and pinch to zoom" options={[['Small', 'Small'], ['Default', 'Default'], ['Large', 'Large'], ['Larger', 'Larger']]} value={s.textSize} onChoose={(v) => set({ textSize: v })} />
          <Toggle title="Dark question pictures" description="In the dark theme, questions white on black instead of black on white" on={s.darkPictures} onSet={(v) => set({ darkPictures: v })} />
        </Section>
        <Section title="Practice">
          <Toggle title="Show the clock in exams" description="Off, the hour still counts down, out of sight" on={s.examClock} onSet={(v) => set({ examClock: v })} />
          <Toggle title="Olaf when the site opens" description="His moment on the way in" on={s.greeting} onSet={(v) => set({ greeting: v })} />
        </Section>
        <Section title="Memes">
          <Toggle title="Science memes in feeds" description="Now and then between the questions: from science subreddits (through meme-api.com) and Lemmy's Science Memes community. Posts marked NSFW are skipped, but they're not checked by us." on={s.memes} onSet={(v) => set({ memes: v })} />
          {s.memes && <Choice title="How often" description="Roughly; never on a beat" options={[['Rare', 'Rarely'], ['Sometimes', 'Sometimes'], ['Often', 'Often']]} value={s.memeFrequency} onChoose={(v) => set({ memeFrequency: v })} />}
        </Section>
        <Section title="Your data">
          <Action title="Reset progress" description="Every answer, sitting, mistake and minute studied. Likes and comments stay" onClick={() => setConfirming('progress')} />
          <Action title="Delete likes and comments" description="Every heart and every note you left" onClick={() => setConfirming('saved')} />
        </Section>
        <Section title="About">
          {install && <Link icon={<Download size={22} />} title="Install PaperZero" description="Add it to your home screen, to open like an app and work offline" onClick={install} />}
          <Link icon={<Code size={22} />} title="PaperZero on GitHub" description="The code, the Android app, and where to report a problem" href={GITHUB} />
        </Section>
        <div className="body-s muted" style={{ padding: '0 8px' }}>
          PaperZero for the web · open source (GPL-3.0). Everything you do stays in this browser. On iPhone, tap Share then "Add to Home Screen" to keep it like an app.
        </div>
      </div>
      {confirming && (
        <Dialog
          title={confirming === 'progress' ? 'Reset progress?' : 'Delete likes and comments?'}
          text={confirming === 'progress' ? 'Every answer, best score, mistake to fix and minute studied will be gone, for good. Likes and comments stay.' : 'Every liked question and every comment will be gone, for good.'}
          confirm={confirming === 'progress' ? 'Reset' : 'Delete'} cancel="Cancel" danger
          onCancel={() => setConfirming(null)}
          onConfirm={async () => {
            const which = confirming
            setConfirming(null)
            if (which === 'progress') await db.resetProgress()
            else await db.deleteSaved()
            setDone(which === 'progress' ? 'Progress reset. A fresh start.' : 'Likes and comments deleted.')
          }}
        />
      )}
      {done && <Dialog text={done} confirm="OK" onConfirm={() => setDone(null)} />}
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <div className="title-m" style={{ padding: '12px 8px 0' }}>{title}</div>
      <div className="card" style={{ padding: '8px 0' }}>{children}</div>
    </>
  )
}

function Label({ title, description }: { title: string; description?: string }) {
  return (
    <span style={{ flex: 1, minWidth: 0 }}>
      <span className="title-s" style={{ display: 'block' }}>{title}</span>
      {description && <span className="body-m muted">{description}</span>}
    </span>
  )
}

function Toggle({ title, description, on, onSet }: { title: string; description?: string; on: boolean; onSet: (v: boolean) => void }) {
  return (
    <button onClick={() => onSet(!on)} role="switch" aria-checked={on} style={{ display: 'flex', alignItems: 'center', gap: 16, width: '100%', padding: '12px 20px', textAlign: 'left' }}>
      <Label title={title} description={description} />
      <Switch on={on} />
    </button>
  )
}

function Choice<T extends string>({ title, description, options, value, onChoose }: { title: string; description?: string; options: [T, string][]; value: T; onChoose: (v: T) => void }) {
  return (
    <div style={{ padding: '12px 20px' }}>
      <Label title={title} description={description} />
      <div style={{ display: 'flex', gap: 8, marginTop: 10, overflowX: 'auto' }}>
        {options.map(([v, name]) => <Chip key={v} label={name} subtle on={v === value} onClick={() => onChoose(v)} />)}
      </div>
    </div>
  )
}

function Action({ title, description, onClick }: { title: string; description: string; onClick: () => void }) {
  return (
    <button onClick={onClick} style={{ display: 'block', width: '100%', padding: '12px 20px', textAlign: 'left' }}>
      <span className="title-s" style={{ display: 'block', color: 'var(--error)' }}>{title}</span>
      <span className="body-m muted">{description}</span>
    </button>
  )
}

function Link({ icon, title, description, href, onClick }: { icon: ReactNode; title: string; description: string; href?: string; onClick?: () => void }) {
  const inner = (
    <>
      <span style={{ color: 'var(--primary)', display: 'grid' }}>{icon}</span>
      <Label title={title} description={description} />
      {href && <ExternalLink size={18} color="var(--on-surface-variant)" style={{ marginLeft: 16, flex: 'none' }} />}
    </>
  )
  const style = { display: 'flex', alignItems: 'center', gap: 16, width: '100%', padding: '12px 20px', textAlign: 'left' as const, color: 'inherit', textDecoration: 'none' }
  return href ? <a href={href} target="_blank" rel="noopener" style={style}>{inner}</a> : <button onClick={onClick} style={style}>{inner}</button>
}
