# PaperZero

Paper 1 (multiple choice) questions from O-Level and school prelims, in Biology, Chemistry and
Physics, to practise again and again. A sibling of [Oscanner](https://github.com/Dragjon/Oscanner),
with the same philosophy: fully offline, no account, no ads, no tracking, no network permission.

- **Doomscroll**: a science's questions as an endless feed of posts. Swipe past, or tap A to D to
  try one and see at once if you're right.
- **Like and comment**: heart a question to find it again; leave yourself comments on it (a hint, a
  mistake you made, a formula). Comments are private: they stay on your phone and no one else sees
  them. Home shows what you've liked and commented on.
- **Topics**: each question is tagged with the chapters it covers (the BPGHS notes' chapters); tap a
  tag for a feed of only that topic.
- **Papers**: each O-Level year and school prelim, done as **Practise** (each answer marked at once,
  with the working where there is some) or **Exam** (the whole paper in an hour, marked at the end).
- **Mistakes**: a question you get wrong comes back until you get it right twice running.
- **Stats**: time studied (this week, day by day), your streak, how often you're right, and that by
  science and by topic, weakest topics first.

Each science has its own colours: Biology green, Chemistry violet, Physics blue. Olaf, in chalk,
studies at his desk.

## The questions

The questions themselves are not in this repository: the papers belong to SEAB/Cambridge, the
schools and their publishers. They're a private content pack (`content/pack`, git-ignored) that
`tools/extract` builds from PDFs, and release builds bundle. A build without it has no papers.

`tools/extract`, in order:

| Step | What it does |
|---|---|
| `inventory.py BACKUP` | finds every Paper 1 and its answer key → `work/manifest.json` |
| `words.py BACKUP` | renders pages and reads their words (the PDF's text, or OCR) |
| `margins.py BACKUP` | adds question numbers OCR missed or ran into a word |
| `crop.py` | cuts each question out (the same rules as Oscanner's Split into questions) |
| `keywords.py BACKUP` | renders and reads every answer key |
| `grid_key.py BACKUP` | reads O-Level Biology's key, a grid of answers a year |
| `answers.py BACKUP` | finds the answers, checking a second, sharper reading of each O-Level key against the first; `content/manual_keys.json` (private) has ones read by eye |
| `review_keys.py BACKUP --brief` | checks each answer found is in its key as number then letter |
| `topics.py BACKUP` | tags each question with its topics, by the words it shares with each chapter's notes |
| `unwrite.py HAND_ONNX --score` | finds a student's writing on scanned questions, to look over by eye; `content/review.json` (private) lists the ones left out |
| `pack.py` | writes the pack: an index, each paper's answers and topics, gray WebP pictures |

`farm.py` runs the slow steps (OCR) on a machine with many cores, each process on two threads;
`--upright` turns and splits papers scanned two pages to a sheet.

## License

GPL-3.0, as Oscanner. The questions in release builds are not covered by it: they remain their
owners'.
