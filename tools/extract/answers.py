"""Finds each paper's answers (A to D for questions 1 to 40) in its key's words.

    python answers.py BACKUP_DIR [paper-id ...]

Reads work/manifest.json, the key's words (work/keys/<key>/words.json) or, for a key printed in
the paper, the paper's own last pages; writes work/<id>/answers.json as {"1": "D", ...} and prints
what's missing. Answers read by eye (keys OCR can't be trusted with) are in
content/manual_keys.json, private like the rest of the pack, and win over what's found.
"""
import json
import os
import re
import sys

import fitz

import keywords
import questions
import words

WORK = words.WORK
# "1", "Q1", "MCQ1", "1.", and Section A's "A1".
NUMBER = re.compile(r'^(?:MCQ|Q(?:n|uestion)?\.?|A)?\s*(\d{1,2})[.):]?$', re.I)
# Capitals only: a lower-case "a" is a part of a Paper 2 question ("1 a Velocity ...").
LETTER = re.compile(r'^\(?([A-D](?:\s*[/,&]\s*[A-D])*)\)?[.,;:]?$')


def tokens(page):
    nums, lets = [], []
    for w in page:
        t = questions.clean(w[0])
        m = NUMBER.match(t)
        if m and 1 <= int(m.group(1)) <= 40:
            nums.append((int(m.group(1)), w))
            continue
        m = LETTER.match(t)
        if m:
            lets.append((''.join(sorted(set(re.sub(r'[^A-D]', '', m.group(1).upper())))), w))
    return nums, lets


def pair(nums, lets, below):
    """Each number's letter: the nearest one under it (below=True) or beside it on its right."""
    found = {}
    for n, w in nums:
        cx, cy, h = (w[1] + w[3]) / 2, (w[2] + w[4]) / 2, max(1e-3, w[4] - w[2])
        best = None
        for letter, v in lets:
            lx, ly = (v[1] + v[3]) / 2, (v[2] + v[4]) / 2
            if below:
                dx, dy = abs(lx - cx), ly - cy
                if dy <= h * 0.4 or dy > h * 4 or dx > h * 1.5:
                    continue
                d = dy + dx * 2
            else:
                dx, dy = lx - cx, abs(ly - cy)
                if dx <= 0 or dx > h * 12 or dy > h * 0.6:
                    continue
                d = dx + dy * 4
            if best is None or d < best[0]:
                best = (d, letter)
        if best and n not in found:
            found[n] = best[1]
    return found


def kind(word):
    t = questions.clean(word[0])
    m = NUMBER.match(t)
    if m and 1 <= int(m.group(1)) <= 40:
        return 'n', int(m.group(1))
    m = LETTER.match(t)
    if m:
        # "A/D": either is right.
        return 'l', ''.join(sorted(set(re.sub(r'[^A-D]', '', m.group(1).upper()))))
    return 'x', t


def rows(page):
    """Words in rows by their middles: a key's cells can be drawn tall enough that a row of
    numbers and the row of letters under it overlap."""
    out = []
    for w in sorted(page, key=lambda w: (w[2] + w[4]) / 2):
        cy, h = (w[2] + w[4]) / 2, w[4] - w[2]
        if out and abs(cy - out[-1][0]) < 0.3 * min(h, out[-1][1]):
            out[-1][2].append(w)
        else:
            out.append([cy, h, [w]])
    return [sorted(r[2], key=lambda w: w[1]) for r in out]


def by_order(page, lines=None):
    """Answers read in order: a row of numbers over a row of as many letters, or lines going
    number, letter, number, letter ("1 D 11 C 21 A"). [lines], a PDF's own lines of text, are
    read instead of rows found by where words sit, when there are some."""
    out = {}
    if lines is not None:
        ls = [[kind([t]) for t in line.split()] for line in lines]
    else:
        ls = [[kind(w) for w in l] for l in rows(page)]
    for i, line in enumerate(ls):
        nums = [v for k, v in line if k == 'n']
        if len(nums) >= 3 and len(nums) == len(line) and all(b == a + 1 for a, b in zip(nums, nums[1:])):
            for nxt in ls[i + 1:i + 3]:
                lets = [v for k, v in nxt if k == 'l']
                if len(lets) == len(nums) and len(lets) >= len(nxt) - 1:
                    for n, letter in zip(nums, lets):
                        out.setdefault(n, letter)
                    break
            continue
        # "1 D 11 C 21 A": a line of a table, nearly all numbers and letters, two pairs or more.
        # (Not "1 A pancreas ...", a Paper 2 answer.)
        pairs = [(v1, v2) for (k1, v1), (k2, v2) in zip(line, line[1:]) if k1 == 'n' and k2 == 'l']
        if len(pairs) >= 2 and sum(k != 'x' for k, _ in line) >= len(line) - 1:
            for n, letter in pairs:
                out.setdefault(n, letter)
    return out


def by_stream(lines):
    """Answers in a PDF's text read as one stream of words, lines and all: a run of numbers and
    then a run of as many letters (a table laid out a cell a line), paired by place, so a misprint
    in a number doesn't matter; or a number on its own followed by a letter on its own."""
    out = {}
    toks = [kind([t]) for line in lines for t in line.split()]
    i = 0
    while i < len(toks):
        j = i
        while j < len(toks) and toks[j][0] == 'n':
            j += 1
        nums = [v for _, v in toks[i:j]]
        # A row's label may sit between the numbers and their letters ("Qn 1 2 3 ... Ans C D D").
        k = j
        while k < len(toks) and k - j < 2 and toks[k][0] == 'x' and len(nums) >= 3:
            k += 1
        j = k
        while k < len(toks) and toks[k][0] == 'l':
            k += 1
        lets = [v for _, v in toks[j:k]]
        # "Paper 1 1 2 3 ... 10 D B C ...": numbers before the table's own run are dropped.
        if len(lets) >= 3 and len(nums) > len(lets):
            nums = nums[-len(lets):]
        # "... 39 40 C D A: 10 B: 11": letters after the table's own run (a tally) are dropped.
        if len(nums) >= 3 and len(lets) > len(nums):
            lets = lets[:len(nums)]
        if len(nums) >= 3 and len(lets) == len(nums):
            first = nums[0]
            # Mostly counting up by one (a misprint breaks the count twice): a misprinted number
            # is taken for its place.
            if nums[-1] - nums[0] == len(nums) - 1 and sum(b == a + 1 for a, b in zip(nums, nums[1:])) >= len(nums) - 3:
                for place, letter in enumerate(lets):
                    out.setdefault(first + place, letter)
            i = k
            continue
        if nums and 1 <= len(lets) < 3:
            # "Paper 1 30 B Mitosis is ...", "11 C A limiting factor is ...": the first letter
            # is the number's just before it.
            out.setdefault(nums[-1], lets[0])
        i = max(k, i + 1)
    return out


def from_table(pages, text=None):
    """Answers in a table, by order (in the PDF's own lines of [text], when it has some), and
    whether they were placed by where letters sit instead: a guess, only made for a key that's a
    scan, and to be checked by eye."""
    # A PDF with text is read in its text's order only: rows found by where words sit run a
    # table's rows together. A scan has only those.
    # Page by page: Paper 1's key comes before Paper 2's scheme, so the first reading of a
    # question counts.
    out, conflicts = {}, {}
    for i, page in enumerate(pages):
        lines = text[i] if text and i < len(text) and text[i] else None
        for r in ([by_stream(lines), by_order(page, lines)] if lines else [by_order(page)]):
            for n, letter in r.items():
                if n in out and out[n] != letter:
                    conflicts.setdefault(n, {out[n]}).add(letter)
                out.setdefault(n, letter)
    # Two tables that disagree: the key isn't understood; look at it.
    from_table.conflicts = {n: sorted(v) for n, v in conflicts.items()}
    if text and any(text):
        return out, False
    guessed = False
    for page in pages:
        nums, lets = tokens(page)
        if len(nums) < 5 or len(lets) < 5:
            continue
        a, b = pair(nums, lets, True), pair(nums, lets, False)
        for n, letter in (a if len(a) >= len(b) else b).items():
            if n not in out:
                out[n] = letter
                guessed = True
    return out, guessed


# A key's heading, a line of its own (or starting one): not "answers" in the instructions.
# The yearly series' books of worked answers (a "Thought process" for each).
WORKED = re.compile(r'(copy of \d{4} o-level chemistry ans|o-level physics ans)', re.I)
KEY_HEADING = re.compile(r'^(answer key|answers|marking scheme|answer scheme|mark scheme|suggested answers?)\b', re.I)
BOX_STEP = 0.004   # how much further left than the box's text a worked answer's letter starts
HEAD = re.compile(r'^(\d{1,2})\.$')
# A worked answer's letter, alone or run into the explanation's first word ("BMr of N2",
# "D1: Metals ..."); not a page reference like "A51".
CHOSEN = re.compile(r'^([A-D])(?:$|(?=[A-Z][a-z])|(?=[A-Z]\b)|(?=\d:))')
# "PAPER 1" starts the multiple choice; "PAPER 2" ends it.
PAPER = re.compile(r'^PAPER\s*([12])$', re.I)


def columns(page):
    """A worked-answers page's two columns, each its lines top to bottom, left column first."""
    left = [w for w in page if (w[1] + w[3]) / 2 < 0.5]
    right = [w for w in page if (w[1] + w[3]) / 2 >= 0.5]
    return [questions.lines(c) for c in (left, right) if c]


def from_worked(pages, paper=1):
    """Answers in a book of worked answers (O-Level yearly series): each question "N." with a
    "Thought process" box, then the chosen letter starting the explanation. Only the first run of
    1, 2, 3... counts: Paper 2's answers follow. A "PAPER 1" heading starts the count afresh (a
    book may open on the end of the year before's Paper 2), and "PAPER 2" ends it."""
    out, expect, done = {}, 1, False
    pending, box = None, []
    for page in pages:
        for col in columns(page):
            for line in col:
                first = questions.clean(line[0][0])
                paper = PAPER.match(questions.text(line))
                if paper and paper.group(1) == '1' and not done:
                    out, expect, pending, box = {}, 1, None, []
                    continue
                if paper and paper.group(1) == '2' and out:
                    done = True
                    pending = None
                m = HEAD.match(first)
                # The next number, or one a little further on when OCR missed a heading or two.
                if m and expect <= int(m.group(1)) <= expect + 3 and not done:
                    pending, box = int(m.group(1)), []
                    expect = pending + 1
                    continue
                if not pending:
                    continue
                # The letter starts its line a little left of the "Thought process" box's text
                # (which may begin "A ball falls ..." too).
                if CHOSEN.match(first) and (not box or line[0][1] < min(box) - BOX_STEP):
                    out.setdefault(pending, CHOSEN.match(first).group(1))
                    pending = None
                else:
                    box.append(line[0][1])
                if expect > 40:
                    done = True
    return out


# O-Level Biology's key: a grid a year, read by grid_key.py.
GRID = re.compile(r'Compiled Paper 1 Answers', re.I)
# The older yearly series' answers, several years in one book: "OCTOBER/NOVEMBER 2010", then
# "1. D" with its explanation for each question. OCR gives "1.D" or "1." and "D".
BOOK = re.compile(r'Answer Key Paper 1 (\d{4})-(\d{4})', re.I)
ENTRY = re.compile(r'^(\d{1,2})\s?[.,]\s?([A-D])$')
ENTRY_NUMBER = re.compile(r'^(\d{1,2})[.,]$')
SINGLE = re.compile(r'^[A-D]$')


def entries(page):
    """A page's "N. L"s, each with the word it starts at."""
    out = []
    for w in page:
        t = w[0].strip()
        m = ENTRY.match(t)
        if m:
            out.append((int(m.group(1)), m.group(2), w))
            continue
        m = ENTRY_NUMBER.match(t)
        if m:
            h, cy = w[4] - w[2], (w[2] + w[4]) / 2
            after = [v for v in page if SINGLE.match(v[0].strip()) and abs((v[2] + v[4]) / 2 - cy) < h * 0.5 and 0 <= v[1] - w[3] < h * 2.5]
            if after:
                out.append((int(m.group(1)), min(after, key=lambda v: v[1])[0].strip(), w))
    return out


def by_column(found):
    """[found] in reading order: columns (entries starting at about the same left) left to right,
    each top to bottom. A page may be a spread of two, four columns in all."""
    column, c, last = {}, -1, None
    for x in sorted({e[2][1] for e in found}):
        if last is None or x - last > 0.08:
            c += 1
        column[x] = c
        last = x
    return sorted(found, key=lambda e: (column[e[2][1]], e[2][2]))


def counting_up(run):
    """The longest part of [run] whose numbers count up: what's left is a misread number, or a
    Paper 2 part ("2. A") read as an answer."""
    best = []   # best[i]: the longest such part ending at run[i]
    for i, (n, _) in enumerate(run):
        before = [best[j] for j in range(i) if run[j][0] < n]
        best.append(max(before, key=len, default=[]) + [run[i]])
    return max(best, key=len, default=[])


def from_book(pages, years):
    """{year: {question: letter}} from a book of [years]' answers, in order: the entries in reading
    order, a year ending where the count, into the thirties, goes back to the start (Paper 2's
    numbers are mostly "1. (a)", which don't count). None when that doesn't give a run a year."""
    runs = [[]]
    for page in pages:
        for n, letter, _ in by_column(entries(page)):
            if runs[-1] and runs[-1][-1][0] >= 30 and n < runs[-1][-1][0] - 5:
                runs.append([])
            runs[-1].append((n, letter))
    if len(runs) != len(years):
        return None
    return {year: dict(counting_up(run)) for year, run in zip(years, runs)}


def readings(key):
    """A key's readings: the first, and the larger second one farm.py --sharp makes, if there is one."""
    folder = os.path.join(keywords.KEYS, keywords.key_id(key))
    return [json.load(open(os.path.join(folder, f), encoding='utf-8')) for f in ('words.json', 'words300.json')
            if os.path.exists(os.path.join(folder, f))]


def merged(found):
    """Readings of one paper's answers made one: what any reading found; where two disagree, nothing
    (and the question reported)."""
    out, disagree = {}, {}
    for f in found:
        for n, letter in f.items():
            if n in out and out[n] != letter:
                disagree.setdefault(n, {out[n]}).add(letter)
            out.setdefault(n, letter)
    for n in disagree:
        del out[n]
    return out, {n: sorted(v) for n, v in disagree.items()}


def o_level(p, manifest):
    """An O-Level paper's answers, from each book that has them, each reading of each; and where
    they disagree. Chemistry's worked answers from 2016 overlap the older series' last book."""
    found = []
    books = [q['key'] for q in manifest if q['subject'] == p['subject'] and q['source'] == 'O-Level' and q['key'] and BOOK.search(q['key'])]
    for key in sorted(set(books + [p['key']])):
        m = BOOK.search(key)
        if m and not int(m.group(1)) <= p['year'] <= int(m.group(2)):
            continue
        for pages in readings(key):
            if m:
                years = from_book(pages, list(range(int(m.group(1)), int(m.group(2)) + 1)))
                if years is None:
                    print('    %s: years not told apart' % keywords.key_id(key))
                    continue
                found.append(years[p['year']])
            elif WORKED.search(key):
                found.append(from_worked(pages))
    return merged(found)


def main():
    manual = {}
    # Part of the private content pack, like the answers it holds.
    path = os.path.join(os.path.dirname(__file__), '..', '..', 'content', 'manual_keys.json')
    if os.path.exists(path):
        manual = json.load(open(path, encoding='utf-8'))
    root = sys.argv[1].replace(chr(92), '/').rstrip('/') + '/Science/'
    only = set(sys.argv[2:])
    manifest = json.load(open(os.path.join(WORK, 'manifest.json'), encoding='utf-8'))
    for p in manifest:
        pid = words.paper_id(p)
        if only and pid not in only:
            continue
        text = None
        if p['key_in_paper']:
            pages = json.load(open(os.path.join(WORK, pid, 'words.json'), encoding='utf-8'))
            text = [page.get_text().splitlines() for page in fitz.open(root + p['qp'], filetype='pdf')]
            # From the key's own heading on: the question pages before it have letters and
            # numbers side by side too.
            first = next((i for i in range(len(text) - 1, -1, -1) if any(KEY_HEADING.match(line.strip()) for line in text[i])), len(text) - 6)
            pages, text = pages[first:], text[first:]
        elif p['key'] and (WORKED.search(p['key']) or BOOK.search(p['key']) or GRID.search(p['key'])):
            if GRID.search(p['key']):
                grid = os.path.join(keywords.KEYS, keywords.key_id(p['key']), 'grid.json')
                found = {int(k): v for k, v in json.load(open(grid)).get(str(p['year']), {}).items()} if os.path.exists(grid) else {}
                disagree = {}
            else:
                found, disagree = o_level(p, manifest)
            found.update({int(k): v for k, v in manual.get(pid, {}).items()})
            json.dump({str(k): v for k, v in sorted(found.items())}, open(os.path.join(WORK, pid, 'answers.json'), 'w'))
            missing = sorted(set(range(1, 41)) - set(found))
            disagree = {n: v for n, v in disagree.items() if str(n) not in manual.get(pid, {})}
            print('%-28s %2d answers%s%s' % (pid, len(found), ('  missing %s' % missing) if missing else '',
                                             ('  DISAGREE: %s' % ', '.join('%d %s' % (n, '/'.join(v)) for n, v in sorted(disagree.items()))) if disagree else ''), flush=True)
            continue
            found = from_worked(json.load(open(kp, encoding='utf-8')))
            from_table.conflicts = {}
            guessed = False
            found.update({int(k): v for k, v in manual.get(pid, {}).items()})
            json.dump({str(k): v for k, v in sorted(found.items())}, open(os.path.join(WORK, pid, 'answers.json'), 'w'))
            missing = sorted(set(range(1, 41)) - set(found))
            print('%-28s %2d answers (worked)%s' % (pid, len(found), ('  missing %s' % missing[:12]) if missing else ''), flush=True)
            continue
        elif p['key']:
            kp = os.path.join(keywords.KEYS, keywords.key_id(p['key']), 'words.json')
            if not os.path.exists(kp):
                print('%-28s key not read yet' % pid)
                continue
            pages = json.load(open(kp, encoding='utf-8'))
            text = [page.get_text().splitlines() for page in fitz.open(root + p['key'], filetype='pdf')]
        else:
            pages = []
        found, guessed = from_table(pages, text)
        found.update({int(k): v for k, v in manual.get(pid, {}).items()})
        json.dump({str(k): v for k, v in sorted(found.items())}, open(os.path.join(WORK, pid, 'answers.json'), 'w'))
        missing = sorted(set(range(1, 41)) - set(found))
        conflicts = from_table.conflicts
        if pid in manual:
            guessed = False
            conflicts = {}
        print('%-28s %2d answers%s%s' % (pid, len(found), ('  missing %s' % missing[:12]) if missing else '',
                                         '  PLACED BY POSITION: check by eye' if guessed else ''), flush=True)
        if conflicts:
            print('    DISAGREE: %s' % ', '.join('%d %s' % (n, '/'.join(v)) for n, v in sorted(conflicts.items())))


if __name__ == '__main__':
    main()
