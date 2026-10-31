# PaperZero

Paper 1 (multiple choice) questions from O-Level and school prelims, to practise again and again.
A sibling of [Oscanner](https://github.com/Dragjon/Oscanner), with the same philosophy: fully
offline, no account, no ads, no tracking, no network permission.

- **Papers**: each O-Level year and school prelim, done as **Practise** (each answer marked at once,
  with the working where there is some) or **Exam** (the whole paper in an hour, marked at the end).
- **Doomscroll**: a subject's questions one a screen, without end. Swipe past, or tap a letter.
- **Mistakes**: a question you get wrong comes back until you get it right twice running.

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
| `answers.py BACKUP` | finds the answers; `content/manual_keys.json` (private) has ones read by eye |
| `review_keys.py BACKUP --brief` | checks each answer found is in its key as number then letter |
| `pack.py` | writes the pack: an index, each paper's answers, gray WebP pictures |
