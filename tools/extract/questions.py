"""Finds the numbered questions in a paper's words and cuts each into the stretches of pages it
covers. The same rules as Oscanner's "Split into questions" (core/data Questions.kt), checked on
334 past papers.

    starts(pages)  -> the questions' starts, in order
    parts(pages)   -> (number, page, top, bottom) stretches, number 0 for what comes before the
                      first question found; and the shared stems: {question: [(page, top, bottom)]}

pages: a list per page of [text, left, top, right, bottom] in page fractions.
"""
import collections
import re

# ---------------------------------------------------------------- lines and numbers

numbered = re.compile(r'^((?:Q|Question)\.?\s*)?\(?(\d{1,2})([.):]?(?:\([a-z]{1,4}\))+|[a-h]\)|[.):])?$', re.I)
question_word = re.compile(r'^(Q|Question)\.?$', re.I)
invisible = re.compile('[​‌‍﻿]')
marks = re.compile(r'^[\[(]\d{1,2}[\])]$')
dots = re.compile(r'^[.…_—-]+$')
later_part = re.compile(r'^\d{1,2}[.)]?\(?[b-h]\)?')

MARGIN = 0.12      # a question's number starts within this share of the text's width from its left
COLUMN = 0.04      # ...and within this of the numbers' column (page widths)
MAX_GAP = 3        # numbers that may be missed between two questions found
GAP_COST = 0.5     # what each missed number costs the run


def clean(t):
    return invisible.sub('', t).strip()


def lines(words):
    """Words sharing a line, top to bottom, each line left to right."""
    rows = []
    for w in sorted(words, key=lambda w: (w[2] + w[4]) / 2):
        if rows:
            row = rows[-1]
            top = min(x[2] for x in row)
            bottom = max(x[4] for x in row)
            if min(bottom, w[4]) - max(top, w[2]) > 0.5 * min(bottom - top, w[4] - w[2]):
                row.append(w)
                continue
        rows.append([w])
    return [sorted(r, key=lambda w: w[1]) for r in rows]


def text(line):
    return ' '.join(clean(w[0]) for w in line).strip()


def number(line):
    """The question number a line opens with, if it does."""
    words = [clean(w[0]) for w in line]
    words = [w for w in words if w]
    if not words:
        return None
    rest = words[1:]
    m = numbered.match(words[0])
    if m:
        n = int(m.group(2))
        counting = len(rest) >= 2 and all(r == str(n + 1 + k) for k, r in enumerate(rest[:3]))
        answer_line = bool([r for r in rest if not marks.match(r)]) and all(dots.match(r) for r in rest if not marks.match(r))
        return n if 1 <= n <= 60 and not counting and not answer_line else None
    if rest and question_word.match(words[0]):
        m = numbered.match(rest[0])
        if m and not m.group(1):
            n = int(m.group(2))
            counting = len(rest) >= 3 and all(r == str(n + 1 + k) for k, r in enumerate(rest[1:4]))
            return n if 1 <= n <= 60 and not counting else None
    return None


def candidates(page, words):
    if not words:
        return []
    left = min(w[1] for w in words)
    right = max(w[3] for w in words)
    out = []
    for line in lines(words):
        if line[0][1] - left > MARGIN * (right - left):
            continue
        n = number(line)
        if n is None:
            continue
        # Left of the page's text, not of the page: a scanned book's pages sit a little left or
        # right of each other.
        out.append(dict(page=page, number=n, top=min(w[2] for w in line),
                        lh=sum(w[4] - w[2] for w in line) / len(line), left=line[0][1] - left,
                        part=bool(later_part.match(clean(line[0][0])))))
    return out


def run(c):
    """The longest run numbered going up, in reading order; a few numbers may be missed."""
    if not c:
        return []
    best = [1.0] * len(c)
    prev = [-1] * len(c)
    for i in range(len(c)):
        for j in range(i):
            gap = c[i]['number'] - c[j]['number'] - 1
            if 0 <= gap <= MAX_GAP:
                s = best[j] + 1 - GAP_COST * gap
                # Ties go to the one in the same column, then to the nearer one.
                closer = prev[i] < 0 or abs(c[j]['left'] - c[i]['left']) <= abs(c[prev[i]]['left'] - c[i]['left']) + 0.005
                if s > best[i] or (s == best[i] and closer):
                    best[i] = s
                    prev[i] = j
    end = max(range(len(c)), key=lambda i: best[i])
    out = []
    while end >= 0:
        out.append(c[end])
        end = prev[end]
    out.reverse()
    return out if len(out) >= 2 else []


def starts(pages):
    c = []
    for x in (x for i, ws in enumerate(pages) for x in candidates(i, ws)):
        # "9a)" then "9b)": one question in parts, starting at the first.
        if c and c[-1]['number'] == x['number'] and x['part']:
            continue
        c.append(x)
    first = run(c)
    if not first:
        return []
    # Question numbers stand in one column: what's found away from it is dropped.
    xs = sorted(x['left'] for x in first)
    column = xs[len(xs) // 2]
    return run([x for x in c if abs(x['left'] - column) < COLUMN])


# ---------------------------------------------------------------- cutting

PAD = 0.8          # room above and below a question, in its line heights
MIN_PART = 0.02    # thinner stretches are dropped
EDGE = 0.1         # running heads and feet are this near the top or bottom
LAST_PAGES = 3     # pages the last question may run on over
LEAD_PAGES = 4     # pages the questions before the first one found may take
HEAD_MARGIN = 0.15
HEAD_GAP = 2       # lines of instructions allowed between two headings of one run

folio = re.compile(r'^(\W*\d{1,3}\W*|page \d+( of \d+)?|.*\[?turn over\]?)$', re.I)
blank_page = re.compile(r'(blank page|intentionally (been )?left blank|this page is (intentionally )?(left )?blank)', re.I)
end_of_paper = re.compile(r'(end of (the )?(question )?paper|end of (section|test|exam))', re.I)
section = re.compile(r'^(section|part|bahagian)\s+[A-H1-9]\b', re.I)
heading = re.compile(
    r'^((section|part|bahagian)\s+[A-H1-9]\b|(text|passage|source|extract|article|poem)\s+[A-H1-9]\b(?![\s,]+[A-H0-9]\b)(?!.*/)|'
    r'read (the|this|these) (following|passages?|texts?|extracts?|articles?|poems?|sources?)\b|questions?\s+\d+\s*(-|–|to|and)\s*\d+|'
    r'soalan\s+\d+|baca\b|[一二三四五六七八九十]{1,2}[、\s]|[A-F]\s*组)', re.I)
shared_range = re.compile(r'^questions?\s+(\d+)\s*(?:-|–|to|and)\s*(\d+)', re.I)
cover = re.compile(r'(instructions|index number|register number|candidate|printed pages|answer all|'
                   r'this (paper|document|question paper) consists|姓名|班级|说明|考生|nama|arahan|for examiner)', re.I)


def furniture(pages):
    """Each page's lines, and those of them that are its number, running head or foot."""
    ls = [lines(ws) for ws in pages]

    def key(t):
        return re.sub(r'\d+', '#', t.lower()).strip()

    seen = collections.Counter()
    for page in ls:
        for k in {key(text(l)) for l in page if l[0][2] < EDGE or l[0][4] > 1 - EDGE}:
            seen[k] += 1
    out = []
    for page in ls:
        out.append([l for l in page if (min(w[2] for w in l) < EDGE or max(w[4] for w in l) > 1 - EDGE)
                    and (folio.match(text(l)) or seen[key(text(l))] >= max(2, len(pages) // 4))])
    return ls, out


def parts(pages):
    found = starts(pages)
    if not found:
        return [], {}
    ls, furn = furniture(pages)
    body = [[l for l in page if not any(l is x for x in f)] for page, f in zip(ls, furn)]

    def empty(p):
        return not body[p] or bool(blank_page.search(' '.join(text(l) for l in body[p])))

    def text_top(p):
        return min(min(w[2] for w in l) for l in body[p])

    def foot(p, below, pad):
        """Where a stretch of page p below `below` ends: the page's foot (keeping the room to
        answer in) or "End of paper" (True: nothing follows)."""
        end = [min(w[2] for w in l) for l in body[p] if min(w[2] for w in l) > below and end_of_paper.search(text(l))]
        if end:
            under = [w[4] for l in body[p] for w in l if w[4] <= min(end)]
            return max(under, default=below) + pad, True
        bottom = max(max(w[4] for w in l) for l in body[p])
        feet = [min(w[2] for w in l) for l in furn[p] if min(w[2] for w in l) > max(below, 0.5)]
        return max(bottom + pad, min(feet) - pad if feet else 1.0), False

    out = []
    first = found[0]
    if first['number'] > 1:
        p = 0
        while p < first['page'] and (empty(p) or cover.search(' '.join(text(l) for l in body[p]))):
            p += 1
        if first['page'] - p <= LEAD_PAGES:
            pad = first['lh'] * PAD
            for q in range(p, first['page'] + 1):
                if empty(q):
                    continue
                top = text_top(q) - pad
                bottom = first['top'] - pad if q == first['page'] else foot(q, -1, pad)[0]
                if bottom - top > MIN_PART:
                    out.append((0, q, max(0, top), min(1, bottom)))

    # Headings, as (page, top, where its run of headings starts (top, line height), text).
    heads = []
    for p in range(len(pages)):
        if not body[p]:
            continue
        left = min(min(w[1] for w in l) for l in body[p])
        right = max(max(w[3] for w in l) for l in body[p])
        group, since = None, 0
        for l in body[p]:
            t = text(l)
            centred = section.match(t) and len(l) <= 6
            if heading.match(t) and (centred or l[0][1] - left <= HEAD_MARGIN * (right - left)):
                if group is None or since > HEAD_GAP:
                    group = (min(w[2] for w in l), sum(w[4] - w[2] for w in l) / len(l))
                heads.append((p, min(w[2] for w in l), group, t))
                since = 0
            else:
                since += 1

    def cut(a, b):
        """Above the last run of headings between (page, top) a and b, and its text, if there is one."""
        h = [h for h in heads if (h[0], h[1]) > (a[0], a[1] + 1e-4) and (h[0], h[1]) < (b[0], b[1] - 1e-4)]
        if not h:
            return None
        p, _, (top, lh), t = h[-1]
        if (p, top) <= (a[0], a[1] + 1e-4):
            return None
        return (p, top - lh * PAD, ' '.join(x[3] for x in h if x[0] == p and x[2] == (top, lh)))

    cuts = [cut((a['page'], a['top']), (b['page'], b['top'])) for a, b in zip(found, found[1:])]
    lead_cut = cut((out[0][1], out[0][2] + 0.03), (first['page'], first['top'])) if out else None
    if lead_cut:
        out = [(0, q, t, min(b, lead_cut[1]) if q == lead_cut[0] else b) for _, q, t, b in out if (q, t) < lead_cut[:2]]
        out = [x for x in out if x[3] - x[2] > MIN_PART]
    cuts = [lead_cut] + cuts

    for i, s in enumerate(found):
        nxt = found[i + 1] if i + 1 < len(found) else None
        pad = s['lh'] * PAD
        begin = cuts[i][:2] if cuts[i] else (s['page'], s['top'] - pad)
        end = (cuts[i + 1][:2] if nxt and cuts[i + 1] else None) or ((nxt['page'], nxt['top'] - nxt['lh'] * PAD) if nxt else None)
        p = begin[0]
        while True:
            if not empty(p):
                top = begin[1] if p == begin[0] else text_top(p) - pad
                ended = False
                if end and p == end[0]:
                    bottom = end[1]
                else:
                    bottom, ended = foot(p, begin[1] + pad if p == begin[0] else -1, pad)
                if bottom - top > MIN_PART:
                    out.append((s['number'], p, max(0, top), min(1, bottom)))
                if ended:
                    break
            p += 1
            if p >= len(pages):
                break
            if end is None:
                if p > s['page'] + LAST_PAGES or empty(p) or candidates(p, pages[p]):
                    break
            elif p > end[0]:
                break

    # "Questions 27 and 28 refer to the diagram below": the stretch from that heading down to 27
    # goes with 28 too (and every question in a "to" range).
    shared = {}
    for i, s in enumerate(found):
        c = cuts[i]
        m = shared_range.match(c[2]) if c else None
        if not m:
            continue
        lo, hi = int(m.group(1)), int(m.group(2))
        stem = [(p, t, s['top'] - s['lh'] * PAD if p == s['page'] else b) for n, p, t, b in out
                if n == s['number'] and (p, t) < (s['page'], s['top'])]
        stem = [x for x in stem if x[2] - x[1] > MIN_PART]
        for n in range(lo + 1, hi + 1):
            if stem:
                shared[n] = stem
    return out, shared
