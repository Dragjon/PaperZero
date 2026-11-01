/**
 * Science memes from the internet, only when they're turned on in the settings, from two open
 * places that need no key: science subreddits through meme-api.com, and Lemmy's Science Memes
 * community (thousands of posts) read through lemmy.world. The same as the app's.
 */
import type { Subject } from './pack'

export interface Meme {
  title: string
  /** The picture, loaded and ready to show. */
  src: string
  source: string
  link: string
}

interface Post {
  title: string
  url: string
  source: string
  link: string
  /** The same picture kept on lemmy.world, for when its own server doesn't answer. */
  copy?: string
}

const REDDIT = 'https://meme-api.com/gimme'
const LEMMY = 'https://lemmy.world/api/v3/post/list'
const LEMMY_COMMUNITY = 'science_memes@mander.xyz'
const LEMMY_HOST = 'lemmy.world'
const LEMMY_PAGES = 90
const AT_ONCE = 4
const TIMEOUT_MS = 6000
const PICTURE = /\.(jpe?g|png|webp)$/i

const general = ['sciencememes', 'ScienceHumor', 'nerdymemes']
function subreddits(subject?: Subject): string[] {
  const own: Record<Subject, string[]> = {
    Biology: ['biologymemes', 'MicrobiologyMemes', 'AnatomyMemes', 'Biochemistrymemes', 'BioMemes'],
    Chemistry: ['chemistrymemes', 'Biochemistrymemes'],
    Physics: ['physicsmemes', 'astronomymemes', 'spacememes', 'physicsjokes', 'engineeringmemes'],
  }
  return [...(subject ? own[subject] : ['geologymemes', 'MicrobiologyMemes', 'astronomymemes']), ...general]
}

const pool = new Map<string, Post[]>()
const used = new Map<string, Set<string>>()
/** Servers that didn't answer: their pictures are taken from a copy instead. */
const dead = new Set<string>()

function host(url?: string): string | undefined {
  try {
    return url ? new URL(url).host : undefined
  } catch {
    return undefined
  }
}

function sources(p: Post): string[] {
  const list = [p.copy && host(p.copy) === LEMMY_HOST ? p.copy : undefined, p.url, p.copy].filter((x): x is string => !!x)
  return [...new Set(list)].filter((u) => !dead.has(host(u) ?? ''))
}

async function json(url: string): Promise<any> {
  const c = new AbortController()
  const t = setTimeout(() => c.abort(), TIMEOUT_MS)
  try {
    const r = await fetch(url, { signal: c.signal })
    if (!r.ok) throw new Error(`HTTP ${r.status}`)
    return await r.json()
  } finally {
    clearTimeout(t)
  }
}

/** A picture loaded by the browser, or null if it won't load in time. */
function load(url: string): Promise<boolean> {
  return new Promise((resolve) => {
    const img = new Image()
    const t = setTimeout(() => { img.src = ''; resolve(false) }, TIMEOUT_MS)
    img.onload = () => { clearTimeout(t); resolve(img.naturalWidth > 0) }
    img.onerror = () => { clearTimeout(t); resolve(false) }
    img.referrerPolicy = 'no-referrer'
    img.src = url
  })
}

async function picture(p: Post): Promise<string | null> {
  for (const url of sources(p)) {
    if (await load(url)) return url
    const h = host(url)
    if (h && h !== LEMMY_HOST && url !== p.copy && p.copy) dead.add(h)
  }
  return null
}

function reddit(j: any): Post[] {
  return (j?.memes ?? [])
    .filter((m: any) => m.nsfw === false && m.spoiler === false && PICTURE.test(String(m.url).split('?')[0]))
    .map((m: any) => ({ title: m.title, url: m.url, source: `r/${m.subreddit}`, link: m.postLink }))
}

function lemmy(j: any): Post[] {
  return (j?.posts ?? [])
    .filter((v: any) => v.post && v.post.nsfw === false && v.community?.nsfw === false && PICTURE.test(String(v.post.url ?? '').split('?')[0]))
    .map((v: any) => ({
      title: v.post.name,
      url: v.post.url,
      source: 'Lemmy · Science Memes',
      link: v.post.ap_id,
      copy: v.post.thumbnail_url && v.post.thumbnail_url !== v.post.url ? v.post.thumbnail_url : undefined,
    }))
}

/** Posts from one source not yet drawn on, a subreddit or a page of Lemmy's, about half and half. */
async function gather(subject?: Subject): Promise<Post[] | null> {
  const k = subject ?? ''
  if (!used.has(k)) used.set(k, new Set())
  const drawn = used.get(k)!
  const subs = subreddits(subject).filter((s) => !drawn.has(`r/${s}`))
  const pages = Array.from({ length: LEMMY_PAGES }, (_, i) => `lemmy/${i + 1}`).filter((p) => !drawn.has(p))
  const page = pages[Math.floor(Math.random() * pages.length)]
  const source = page && (!subs.length || Math.random() < 0.5) ? page : subs.length ? `r/${subs[Math.floor(Math.random() * subs.length)]}` : undefined
  if (!source) {
    drawn.clear()
    return []
  }
  drawn.add(source)
  try {
    if (source.startsWith('r/')) return reddit(await json(`${REDDIT}/${source.slice(2)}/50`))
    const q = new URLSearchParams({ community_name: LEMMY_COMMUNITY, sort: 'TopAll', limit: '50', page: source.slice(6) })
    return lemmy(await json(`${LEMMY}?${q}`))
  } catch {
    return null
  }
}

/** A few of [subject]'s memes, none in [seen]; empty when no source answers (offline, say). */
export async function fetchMemes(subject: Subject | undefined, seen: Set<string>): Promise<Meme[]> {
  const k = subject ?? ''
  if (!pool.has(k)) pool.set(k, [])
  const queue = pool.get(k)!
  const usable = (p: Post) => !seen.has(p.link) && sources(p).length > 0
  for (let tries = 0; queue.filter(usable).length < AT_ONCE && tries < 4; tries++) {
    const more = await gather(subject)
    if (!more) continue
    for (const p of more.sort(() => Math.random() - 0.5)) if (usable(p) && !queue.some((x) => x.link === p.link)) queue.push(p)
  }
  const posts: Post[] = []
  while (posts.length < AT_ONCE && queue.length) {
    const p = queue.shift()!
    if (usable(p)) posts.push(p)
  }
  const got = await Promise.all(posts.map(async (p) => {
    const src = await picture(p)
    return src ? { title: p.title, src, source: p.source, link: p.link } : null
  }))
  return got.filter((m): m is Meme => m != null)
}
