// PaperZero's service worker: the site works offline once opened. The pages and code are kept on
// install; questions' pictures and the pack's lists as they're first seen (and all of them, a
// little at a time, in the background). Memes from the internet are never kept.
const VERSION = 'paperzero-1'
const SHELL = ['./', 'index.html', 'manifest.webmanifest', 'icon.svg', 'pack/index.json',
  'fonts/figtree_regular.ttf', 'fonts/figtree_medium.ttf', 'fonts/figtree_semibold.ttf']

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k)))).then(() => self.clients.claim()))
})

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url)
  if (e.request.method !== 'GET' || url.origin !== location.origin) return
  // The page and its code: the network first (so an update shows), the kept copy offline.
  // The pack: kept first (it only changes with a new build, which changes VERSION).
  const packed = url.pathname.includes('/pack/') || url.pathname.includes('/fonts/')
  e.respondWith(packed ? cacheFirst(e.request) : networkFirst(e.request))
})

async function cacheFirst(req) {
  const hit = await caches.match(req)
  if (hit) return hit
  const res = await fetch(req)
  if (res.ok) (await caches.open(VERSION)).put(req, res.clone())
  return res
}

async function networkFirst(req) {
  try {
    const res = await fetch(req)
    if (res.ok) (await caches.open(VERSION)).put(req, res.clone())
    return res
  } catch {
    return (await caches.match(req)) || (await caches.match('index.html'))
  }
}
