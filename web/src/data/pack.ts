/** The content pack the site reads (built by tools/extract, the same as the app's). */

export type Subject = 'Biology' | 'Chemistry' | 'Physics'
export const SUBJECTS: Subject[] = ['Biology', 'Chemistry', 'Physics']

export interface PaperInfo {
  id: string
  subject: Subject
  source: string | null
  year: number | null
  count: number
}

export interface Question {
  /** paper id / number: the same key the app uses. */
  key: string
  paperId: string
  number: number
  answer: string
  /** The picture's path, from the site's root. */
  image: string
  explain?: string
  /** Places in the subject's topic list. */
  topics: number[]
}

export interface Pack {
  papers: PaperInfo[]
  topics: Record<Subject, string[]>
  questions: Map<string, Question>
  bySubject: Record<Subject, Question[]>
  paper(id: string): PaperInfo | undefined
  ofPaper(id: string): Question[]
}

const BASE = 'pack/'

/** The whole pack: every paper's questions are small JSON, so they're all read at the start. */
export async function loadPack(): Promise<Pack> {
  const index = await (await fetch(BASE + 'index.json')).json()
  const papers: PaperInfo[] = index.papers
  const questions = new Map<string, Question>()
  const byPaper = new Map<string, Question[]>()
  const loaded = await Promise.all(
    papers.map(async (p) => {
      const json = await (await fetch(`${BASE}${p.id}/paper.json`)).json()
      return { p, qs: json.questions as { n: number; answer: string; image: string; explain?: string; topics?: number[] }[] }
    }),
  )
  for (const { p, qs } of loaded) {
    const list = qs.map((q) => ({
      key: `${p.id}/${q.n}`,
      paperId: p.id,
      number: q.n,
      answer: q.answer,
      image: `${BASE}${p.id}/${q.image}`,
      explain: q.explain ? `${BASE}${p.id}/${q.explain}` : undefined,
      topics: q.topics ?? [],
    }))
    list.forEach((q) => questions.set(q.key, q))
    byPaper.set(p.id, list)
  }
  const byId = new Map(papers.map((p) => [p.id, p]))
  const bySubject = Object.fromEntries(
    SUBJECTS.map((s) => [s, papers.filter((p) => p.subject === s).flatMap((p) => byPaper.get(p.id) ?? [])]),
  ) as Record<Subject, Question[]>
  return {
    papers,
    topics: index.topics ?? { Biology: [], Chemistry: [], Physics: [] },
    questions,
    bySubject,
    paper: (id) => byId.get(id),
    ofPaper: (id) => byPaper.get(id) ?? [],
  }
}

/** "O-Level 2023", "BPGHS 2019". */
export function paperTitle(p: PaperInfo): string {
  return [p.source, p.year].filter((x) => x != null).join(' ')
}

export function isOLevel(p: PaperInfo): boolean {
  return (p.source ?? '').toLowerCase().includes('o-level')
}

export function subjectOf(pack: Pack, key: string): Subject | undefined {
  return pack.paper(key.slice(0, key.lastIndexOf('/')))?.subject
}
