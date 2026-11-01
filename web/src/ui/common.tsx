import { createContext, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from 'react'
import { ArrowLeft, FlaskConical, Leaf, Zap } from 'lucide-react'
import * as db from '../data/db'
import type { Pack, Subject } from '../data/pack'
import { useSettings } from '../data/settings'

export const PackContext = createContext<Pack | null>(null)
export const usePack = () => useContext(PackContext)!

/** Re-renders when what's kept changes; returns its state. */
export function useDb() {
  useSyncExternalStore(db.subscribe, db.getVersion)
  return db.getState()
}

// ---------------------------------------------------------------- navigation

/** Where we are: the part after # ("feed/Biology?topic=3"). */
export function useRoute(): string {
  const [route, setRoute] = useState(() => location.hash.replace(/^#\/?/, ''))
  useEffect(() => {
    const on = () => setRoute(location.hash.replace(/^#\/?/, ''))
    addEventListener('hashchange', on)
    return () => removeEventListener('hashchange', on)
  }, [])
  return route
}

export function go(route: string, replace = false) {
  const url = '#/' + route
  if (replace) location.replace(url)
  else location.hash = url
}

export function back() {
  if (history.length > 1) history.back()
  else go('', true)
}

// ---------------------------------------------------------------- pieces

export const SUBJECT_ICON: Record<Subject, typeof Leaf> = { Biology: Leaf, Chemistry: FlaskConical, Physics: Zap }

/** [children] in [subject]'s colours (unless they're turned off); [fill]: a whole screen on its tinted ground. */
export function SubjectTheme({ subject, fill, children, className = '' }: { subject?: Subject | null; fill?: boolean; children: ReactNode; className?: string }) {
  const settings = useSettings()
  if (!subject || !settings.scienceColours) return <div className={className}>{children}</div>
  return <div className={`s-${subject} ${fill ? 's-fill' : ''} ${className}`}>{children}</div>
}

export function TopBar({ title, subtitle, end, onBack = back }: { title: string; subtitle?: string; end?: ReactNode; onBack?: () => void }) {
  return (
    <div className="topbar">
      <button className="icon-btn" onClick={onBack} aria-label="Back"><ArrowLeft size={24} /></button>
      <div style={{ flex: 1, minWidth: 0 }}>
        <h1 className="title-l">{title}</h1>
        {subtitle && <div className="body-m muted" style={{ marginLeft: 4 }}>{subtitle}</div>}
      </div>
      {end}
    </div>
  )
}

export function Chip({ label, on, subtle, onClick }: { label: string; on: boolean; subtle?: boolean; onClick: () => void }) {
  return <button className={`chip label-l ${on ? 'on' : ''} ${subtle ? 'subtle' : ''}`} onClick={onClick}>{label}</button>
}

export function Sheet({ onClose, children }: { onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const k = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    addEventListener('keydown', k)
    return () => removeEventListener('keydown', k)
  }, [onClose])
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="grab" />
        {children}
      </div>
    </div>
  )
}

export function SheetAction({ icon, title, description, onClick }: { icon: ReactNode; title: string; description?: string; onClick: () => void }) {
  return (
    <button className="sheet-action press" onClick={onClick}>
      <span className="ic">{icon}</span>
      <span>
        <span className="title-m" style={{ display: 'block' }}>{title}</span>
        {description && <span className="body-m muted">{description}</span>}
      </span>
    </button>
  )
}

export function Dialog({ title, text, confirm, cancel, danger, onConfirm, onCancel }: {
  title?: string; text: string; confirm: string; cancel?: string; danger?: boolean; onConfirm: () => void; onCancel?: () => void
}) {
  return (
    <div className="dialog-backdrop" onClick={onCancel ?? onConfirm}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        {title && <div className="headline" style={{ marginBottom: 12 }}>{title}</div>}
        <div className="body-m muted">{text}</div>
        <div className="actions">
          {cancel && <button className="text-btn" style={{ color: 'var(--on-surface-variant)' }} onClick={onCancel}>{cancel}</button>}
          <button className="text-btn" style={danger ? { color: 'var(--error)' } : undefined} onClick={onConfirm}>{confirm}</button>
        </div>
      </div>
    </div>
  )
}

export function Switch({ on }: { on: boolean }) {
  return <span className={`switch ${on ? 'on' : ''}`} aria-hidden />
}

/** A question's picture, on white as printed (or inverted, with dark pictures on). */
export function QImage({ src, style, className = '' }: { src: string; style?: React.CSSProperties; className?: string }) {
  return <img className={`q-img ${className}`} src={src} alt="" loading="lazy" decoding="async" style={{ background: '#fff', ...style }} />
}

/** A splash round a button the moment it turns out right: a ring and droplets, fading as they go. */
export function Splash() {
  return (
    <span className="splash">
      <span className="ring" />
      {Array.from({ length: 16 }, (_, i) => {
        const a = (i / 16) * Math.PI * 2 + (i % 2 ? 0.22 : 0)
        const far = i % 2 ? 46 : 64
        const r = i % 2 ? 6 : 9
        return <span key={i} className="drop" style={{ width: r, height: r, ['--dx' as string]: `${Math.cos(a) * far}px`, ['--dy' as string]: `${Math.sin(a) * far * 0.75}px` }} />
      })}
    </span>
  )
}

export function Spinner() {
  return <div style={{ flex: 1, display: 'grid', placeItems: 'center', minHeight: 200 }}><div className="spinner" /></div>
}

export const OLAF_QUOTES = [
  'Some people are worth melting for.',
  "Hi, I'm Olaf, and I like warm hugs!",
  "Love is putting someone else's needs before yours.",
  'Watch out for my butt!',
  "Oh look, I've been impaled.",
]

const quote = OLAF_QUOTES[Math.floor(Math.random() * OLAF_QUOTES.length)]
/** One of his lines, a new one each time the site is opened. */
export const olafQuote = () => quote
