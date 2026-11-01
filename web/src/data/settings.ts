/** The settings, kept in the browser, as the app's are on the phone. */
import { useSyncExternalStore } from 'react'

export type Theme = 'Device' | 'Light' | 'Dark'
export type Ground = 'Warm' | 'Neutral' | 'Black'
export type TextSize = 'Small' | 'Default' | 'Large' | 'Larger'
export type MemeFrequency = 'Rare' | 'Sometimes' | 'Often'

export const TEXT_SCALE: Record<TextSize, number> = { Small: 0.9, Default: 1, Large: 1.15, Larger: 1.3 }
/** About how many questions go by between memes; never exactly. */
export const MEME_EVERY: Record<MemeFrequency, number> = { Rare: 15, Sometimes: 8, Often: 4 }

export interface Settings {
  theme: Theme
  ground: Ground
  scienceColours: boolean
  textSize: TextSize
  darkPictures: boolean
  greeting: boolean
  examClock: boolean
  memes: boolean
  memeFrequency: MemeFrequency
}

const DEFAULTS: Settings = {
  theme: 'Device',
  ground: 'Warm',
  scienceColours: true,
  textSize: 'Default',
  darkPictures: false,
  greeting: true,
  examClock: true,
  memes: false,
  memeFrequency: 'Sometimes',
}

const KEY = 'paperzero.settings'

function read(): Settings {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) ?? '{}') }
  } catch {
    return DEFAULTS
  }
}

let current = read()
const listeners = new Set<() => void>()

export function getSettings(): Settings {
  return current
}

export function updateSettings(change: Partial<Settings>) {
  current = { ...current, ...change }
  try {
    localStorage.setItem(KEY, JSON.stringify(current))
  } catch {
    // Not kept (a private window): it still holds for this visit.
  }
  listeners.forEach((l) => l())
}

export function useSettings(): Settings {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l)
      return () => listeners.delete(l)
    },
    () => current,
  )
}
