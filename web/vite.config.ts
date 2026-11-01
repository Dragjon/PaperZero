import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'
import path from 'node:path'

// The questions are the private content pack (../content/pack, kept out of git, like the app's):
// served as /pack in development and copied into the build, never into the repository.
const PACK = path.resolve(__dirname, '../content/pack')

function pack(): Plugin {
  return {
    name: 'paperzero-pack',
    configureServer(server) {
      server.middlewares.use('/pack', (req, res, next) => {
        const file = path.join(PACK, decodeURIComponent((req.url ?? '').split('?')[0]))
        if (!file.startsWith(PACK) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) return next()
        res.setHeader('Content-Type', file.endsWith('.json') ? 'application/json' : 'image/webp')
        fs.createReadStream(file).pipe(res)
      })
    },
    closeBundle() {
      if (!fs.existsSync(PACK)) {
        this.warn('No content/pack: the site is built with no papers.')
        return
      }
      fs.cpSync(PACK, path.resolve(__dirname, 'dist/pack'), { recursive: true })
    },
  }
}

export default defineConfig({
  plugins: [react(), pack()],
  base: './',
  build: { target: 'es2020', chunkSizeWarningLimit: 800 },
})
