/**
 * A story's ring of bubbles, turning slowly; and the way into its feed: the bubbles burst out in a
 * spiral while a dark hole opens over the screen, the feed's black.
 */
import { useEffect, useMemo, useRef } from 'react'

interface Bubble { angle: number; reach: number; size: number; drift: number }

function bubbles(count: number, seed: number): Bubble[] {
  let s = seed * 7919 + 17
  const r = () => ((s = (s * 9301 + 49297) % 233280) / 233280)
  return Array.from({ length: count }, (_, i) => ({
    angle: ((i + r() * 0.6) / count) * Math.PI * 2,
    reach: 0.92 + r() * 0.16,
    size: 0.35 + r() * 0.65,
    drift: 0.8 + r() * 0.4,
  }))
}

/** Nine bubbles of different sizes in a ring of [radius], turning together, slowly. */
export function BubbleRing({ size, radius, biggest, seed }: { size: number; radius: number; biggest: number; seed: number }) {
  const ring = useMemo(() => bubbles(9, seed), [seed])
  return (
    <svg width={size} height={size} viewBox={`${-size / 2} ${-size / 2} ${size} ${size}`} style={{ position: 'absolute', inset: 0 }} aria-hidden>
      <g className="ring-turn">
        {ring.map((b, i) => {
          const out = radius * (b.reach + 0.04 * Math.sin(b.angle * 3))
          return <circle key={i} cx={Math.cos(b.angle) * out} cy={Math.sin(b.angle) * out} r={biggest * b.size} fill="var(--primary)" />
        })}
      </g>
    </svg>
  )
}

/** The burst into a feed, from [from] (a point on screen), in [color]; [onDone] once the screen is covered. */
export function BubbleSpiral({ from, color, seed, onDone }: { from: { x: number; y: number }; color: string; seed: number; onDone: () => void }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const swarm = useMemo(() => bubbles(46, seed), [seed])
  useEffect(() => {
    const still = matchMedia('(prefers-reduced-motion: reduce)').matches
    const c = canvas.current!
    const dpr = devicePixelRatio || 1
    c.width = innerWidth * dpr
    c.height = innerHeight * dpr
    const g = c.getContext('2d')!
    g.scale(dpr, dpr)
    const far = Math.max(
      Math.hypot(from.x, from.y), Math.hypot(innerWidth - from.x, from.y),
      Math.hypot(from.x, innerHeight - from.y), Math.hypot(innerWidth - from.x, innerHeight - from.y),
    )
    const start = performance.now()
    const MS = still ? 0 : 280
    let raf = 0
    const frame = (now: number) => {
      const t = MS ? Math.min(1, (now - start) / MS) : 1
      const p = 1 - (1 - t) * (1 - t)
      g.clearRect(0, 0, innerWidth, innerHeight)
      g.fillStyle = '#000'
      g.beginPath()
      g.arc(from.x, from.y, far * 1.05 * p * p, 0, Math.PI * 2)
      g.fill()
      for (const b of swarm) {
        const a = b.angle + p * 2.4 * b.drift
        const dist = 38 * b.reach + far * 1.1 * p * b.drift * 0.8
        g.globalAlpha = Math.max(0, 1 - p * 0.6)
        g.fillStyle = color
        g.beginPath()
        g.arc(from.x + Math.cos(a) * dist, from.y + Math.sin(a) * dist, 6 * b.size * (1 + p * 5), 0, Math.PI * 2)
        g.fill()
        g.globalAlpha = 1
      }
      if (t < 1) raf = requestAnimationFrame(frame)
      else onDone()
    }
    raf = requestAnimationFrame(frame)
    return () => cancelAnimationFrame(raf)
  }, [])
  return <canvas ref={canvas} style={{ position: 'fixed', inset: 0, width: '100vw', height: '100vh', zIndex: 40, pointerEvents: 'none' }} />
}
