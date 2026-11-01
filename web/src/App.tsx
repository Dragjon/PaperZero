import { useEffect, useState } from 'react'
import { loadDb } from './data/db'
import { type Pack, type Subject, SUBJECTS, loadPack } from './data/pack'
import { TEXT_SCALE, useSettings } from './data/settings'
import { PackContext, Spinner, olafQuote, useRoute } from './ui/common'
import { Feed } from './ui/Feed'
import { Home } from './ui/Home'
import { Mistakes, Saved, StatsScreen, useSavedKeys } from './ui/Lists'
import { ChalkOlaf } from './ui/olaf'
import { Papers } from './ui/Papers'
import { Quiz, Result } from './ui/Quiz'
import { SettingsScreen } from './ui/SettingsScreen'

/** How long Olaf greets on the way in. */
const STARTUP_MS = 1600

/** The browser's "install" offer, when it makes one (Chrome, Edge, Android). */
type InstallPrompt = Event & { prompt: () => Promise<void> }

export function App() {
  const settings = useSettings()
  const [pack, setPack] = useState<Pack | null>(null)
  const [failed, setFailed] = useState(false)
  const [greeting, setGreeting] = useState(settings.greeting)
  const [installer, setInstaller] = useState<InstallPrompt | null>(null)
  const systemDark = useSystemDark()
  const dark = settings.theme === 'Dark' || (settings.theme === 'Device' && systemDark)

  useEffect(() => {
    Promise.all([loadPack(), loadDb()]).then(([p]) => setPack(p)).catch(() => setFailed(true))
    const t = setTimeout(() => setGreeting(false), STARTUP_MS)
    const offer = (e: Event) => { e.preventDefault(); setInstaller(e as InstallPrompt) }
    addEventListener('beforeinstallprompt', offer)
    return () => { clearTimeout(t); removeEventListener('beforeinstallprompt', offer) }
  }, [])

  // The theme on the page itself, so everything (and the browser's own bars) follows it.
  useEffect(() => {
    const root = document.documentElement
    root.dataset.theme = dark ? 'dark' : 'light'
    root.dataset.ground = settings.ground
    root.dataset.darkPictures = String(settings.darkPictures)
    root.style.setProperty('--text-scale', String(TEXT_SCALE[settings.textSize]))
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', getComputedStyle(root).getPropertyValue('--background').trim())
  }, [dark, settings.ground, settings.darkPictures, settings.textSize])

  if (greeting || (!pack && !failed)) {
    return (
      <div className="screen" style={{ alignItems: 'center', justifyContent: 'center', gap: 16 }}>
        <ChalkOlaf dark={dark} style={{ width: 240, height: 200 }} />
        {greeting && <div className="body-l muted" style={{ fontStyle: 'italic', textAlign: 'center', padding: '0 32px' }}>“{olafQuote()}”<br /><span className="label-l">— Olaf</span></div>}
        {!greeting && <Spinner />}
      </div>
    )
  }
  if (!pack) return <div className="screen body-l" style={{ display: 'grid', placeItems: 'center', padding: 32, textAlign: 'center' }}>Couldn't load the questions. Check your connection and reload.</div>

  return (
    <PackContext.Provider value={pack}>
      <Router dark={dark} install={installer ? () => installer.prompt().then(() => setInstaller(null)) : null} />
    </PackContext.Provider>
  )
}

function Router({ dark, install }: { dark: boolean; install: (() => void) | null }) {
  const route = useRoute()
  const [path, query] = route.split('?')
  const parts = path.split('/').filter(Boolean)
  const params = new URLSearchParams(query ?? '')
  const subject = (s?: string): Subject => (SUBJECTS.includes(s as Subject) ? (s as Subject) : 'Biology')
  useEffect(() => { window.scrollTo(0, 0) }, [path])

  switch (parts[0]) {
    case 'feed': {
      const topic = params.get('topic')
      return <Feed key={route} source={{ kind: 'subject', subject: subject(parts[1]), topic: topic != null ? Number(topic) : undefined }} />
    }
    case 'savedfeed':
      return <SavedFeed key={route} tab={parts[1] === 'commented' ? 'commented' : 'liked'} start={Number(parts[2] ?? 0)} />
    case 'papers':
      return <Papers key={parts[1]} start={subject(parts[1])} />
    case 'quiz':
      return <Quiz key={route} id={Number(parts[1])} />
    case 'review':
      return <Quiz key={route} id={Number(parts[1])} review />
    case 'result':
      return <Result id={Number(parts[1])} />
    case 'mistakes':
      return <Mistakes />
    case 'saved':
      return <Saved tab={parts[1] === 'commented' ? 'commented' : 'liked'} />
    case 'stats':
      return <StatsScreen />
    case 'settings':
      return <SettingsScreen install={install} />
    default:
      return <Home dark={dark} />
  }
}

/** The saved list as it was on opening, so unliking one doesn't move the feed. */
function SavedFeed({ tab, start }: { tab: 'liked' | 'commented'; start: number }) {
  const keys = useSavedKeys(tab)
  const [frozen] = useState(keys)
  return <Feed source={{ kind: 'saved', title: tab === 'liked' ? 'Liked' : 'Commented', keys: frozen, start }} />
}

function useSystemDark(): boolean {
  const [dark, setDark] = useState(() => matchMedia('(prefers-color-scheme: dark)').matches)
  useEffect(() => {
    const m = matchMedia('(prefers-color-scheme: dark)')
    const on = () => setDark(m.matches)
    m.addEventListener('change', on)
    return () => m.removeEventListener('change', on)
  }, [])
  return dark
}
