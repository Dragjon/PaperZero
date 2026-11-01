/**
 * Olaf in chalk, seen from the side, studying at a desk: the app's drawing, stroke for stroke, in
 * SVG. Each line is chalk: a broken main pass and two fainter, slightly offset ones beside it.
 */
import { useMemo } from 'react'

type Ink = 'chalk' | 'carrot' | 'twig'
interface Stroke { d: string; width: number; ink: Ink; seed: number }
type P = [number, number]

/** A small seeded random, so a drawing's wobble is the same each time. */
function random(seed: number) {
  let s = seed * 9301 + 49297
  return () => {
    s = (s * 9301 + 49297) % 233280
    return s / 233280
  }
}

/** Catmull–Rom through [points], as cubic Béziers: a curve with no corners. */
function smooth(points: P[], closed: boolean): string {
  const n = points.length
  const at = (i: number) => (closed ? points[(i + n) % n] : points[Math.max(0, Math.min(n - 1, i))])
  let d = `M${points[0][0]},${points[0][1]}`
  const segments = closed ? n : n - 1
  for (let i = 0; i < segments; i++) {
    const [p0, p1, p2, p3] = [at(i - 1), at(i), at(i + 1), at(i + 2)]
    d += ` C${p1[0] + (p2[0] - p0[0]) / 6},${p1[1] + (p2[1] - p0[1]) / 6} ${p2[0] - (p3[0] - p1[0]) / 6},${p2[1] - (p3[1] - p1[1]) / 6} ${p2[0]},${p2[1]}`
  }
  return closed ? d + ' Z' : d
}

/** A closed, slightly lumpy loop round a centre: drawn by hand, not with a compass. */
function loop(cx: number, cy: number, rx: number, ry: number, seed: number, start = 0, sweep = Math.PI * 2): string {
  const r = random(seed)
  const closed = sweep >= Math.PI * 2
  const pts: P[] = []
  for (let i = 0; i <= 12; i++) {
    const a = start + (sweep * i) / 12
    const w = 1 + (r() - 0.5) * 0.06
    pts.push([cx + rx * w * Math.cos(a), cy + ry * w * Math.sin(a)])
  }
  return smooth(closed ? pts.slice(0, -1) : pts, closed)
}

const curve = (...xy: number[]): string => smooth(Array.from({ length: xy.length / 2 }, (_, i) => [xy[i * 2], xy[i * 2 + 1]] as P), false)

function strokes(): Stroke[] {
  const s: Stroke[] = []
  let seed = 1
  const add = (d: string, width: number, ink: Ink = 'chalk') => s.push({ d, width, ink, seed: seed++ })
  // The stool.
  add(curve(46, 158, 76, 156, 106, 158), 3)
  add(curve(54, 159, 52, 176, 49, 192), 2.6)
  add(curve(98, 159, 100, 176, 103, 192), 2.6)
  add(curve(52, 178, 76, 177, 101, 178), 2)
  // Body, sat on the stool, and head leaning in towards the book.
  add(loop(76, 124, 35, 33, 11), 3.2)
  add(loop(102, 68, 27, 26, 12), 3.2)
  // Twig hair.
  add(curve(95, 43, 90, 33, 83, 24), 2.6, 'twig')
  add(curve(103, 42, 103, 31, 106, 20), 2.6, 'twig')
  add(curve(111, 44, 117, 35, 124, 29), 2.6, 'twig')
  add(curve(119, 35, 125, 36, 129, 33), 2, 'twig')
  // One eye, looking down at the page, and a brow raised in thought.
  add(loop(116, 65, 3, 3.6, 21), 3.4)
  add(curve(109, 55, 115, 52, 122, 55), 2)
  // Carrot nose, pointing down at the book.
  add(curve(125, 71, 133, 77, 140, 84), 3, 'carrot')
  add(curve(123, 79, 131, 82, 140, 84), 3, 'carrot')
  add(curve(130, 75, 128, 80), 1.6, 'carrot')
  add(curve(135, 78, 134, 82), 1.6, 'carrot')
  // A small, concentrating smile.
  add(loop(114, 82, 8, 5, 31, Math.PI * 0.2, Math.PI * 0.45), 2.2)
  // Buttons.
  add(loop(106, 108, 2.4, 2.4, 41), 3)
  add(loop(109, 124, 2.4, 2.4, 42), 3)
  add(loop(107, 140, 2.4, 2.4, 43), 3)
  // Arm reaching to the table; twig fingers hold a pencil to the page.
  add(curve(96, 104, 122, 98, 147, 99), 2.6, 'twig')
  add(curve(140, 98, 145, 93, 149, 91), 2, 'twig')
  add(curve(141, 100, 146, 103, 150, 104), 2, 'twig')
  add(curve(148, 90, 158, 108), 3.4)
  add(curve(158, 108, 160, 112), 1.8)
  // The table.
  add(curve(128, 115, 230, 115), 3)
  add(curve(128, 115, 128, 120), 2.2)
  add(curve(128, 120, 230, 120), 2.2)
  add(curve(230, 115, 230, 120), 2.2)
  add(curve(136, 120, 135, 158, 136, 192), 2.6)
  add(curve(222, 120, 223, 158, 222, 192), 2.6)
  // The open book, and some writing.
  add(curve(144, 114, 206, 114), 2)
  add(curve(146, 113, 158, 106, 175, 110), 2.4)
  add(curve(175, 110, 190, 105, 204, 113), 2.4)
  add(curve(175, 110, 175, 114), 1.8)
  add(curve(180, 109, 188, 107, 196, 109), 1.4)
  add(curve(182, 112, 190, 110, 199, 112), 1.4)
  add(curve(162, 108, 168, 108, 172, 110), 1.4)
  // A pile of books at the back of the table, each spine with a label.
  for (const [x0, x1, y0, y1] of [[208, 229, 114, 106], [210, 227, 106, 98], [207, 228, 98, 91]]) {
    add(curve(x0, y0, x1, y0), 2.2)
    add(curve(x0, y1, x1, y1), 2.2)
    add(curve(x0, y0, x0, y1), 2.2)
    add(curve(x1, y0, x1, y1), 2.2)
    add(curve(x0 + 4, (y0 + y1) / 2, x0 + 10, (y0 + y1) / 2), 1.4)
  }
  // Snowflakes.
  ;[[30, 40], [160, 30], [200, 60], [22, 118]].forEach(([cx, cy], i) => {
    const r = i % 2 === 0 ? 5 : 4
    for (let k = 0; k < 3; k++) {
      const a = (Math.PI / 3) * k + 0.3 * i
      add(curve(cx - r * Math.cos(a), cy - r * Math.sin(a), cx + r * Math.cos(a), cy + r * Math.sin(a)), 1.4)
    }
  })
  // The floor.
  add(curve(26, 193, 90, 190, 150, 193, 210, 190, 236, 193), 2.2)
  return s
}

const COLOURS: Record<'dark' | 'light', Record<Ink, string>> = {
  dark: { chalk: '#EDE6DC', carrot: '#E59A73', twig: '#B89478' },
  light: { chalk: '#5E544C', carrot: '#D9774E', twig: '#8A6448' },
}

export function ChalkOlaf({ dark, style }: { dark: boolean; style?: React.CSSProperties }) {
  const all = useMemo(strokes, [])
  const c = COLOURS[dark ? 'dark' : 'light']
  const passes: [number, number, number, number][] = [[0, 0, 1, 0.8], [0.7, -0.5, 0.55, 0.35], [-0.6, 0.6, 0.4, 0.3]]
  return (
    <svg viewBox="0 0 240 200" style={style} aria-hidden>
      {passes.map(([dx, dy, w, a], i) => (
        <g key={i} transform={`translate(${dx} ${dy})`} opacity={a} fill="none" strokeLinecap="round" strokeLinejoin="round">
          {all.map((s, k) => (
            <path key={k} d={s.d} stroke={c[s.ink]} strokeWidth={s.width * w} strokeDasharray={`${7 + i * 2} ${1.6 + i * 0.6}`} strokeDashoffset={s.seed * 3 + i * 5} />
          ))}
        </g>
      ))}
    </svg>
  )
}

/** The site's icon: the zero, in snow, with Olaf's twigs, coal eyes, and his carrot for its slash. */
export function ZeroLogo({ size = 48 }: { size?: number }) {
  return <img src="icon.svg" width={size} height={size} alt="" style={{ borderRadius: '50%', flex: 'none' }} />
}
