"""Tags each question with the topics it covers: the chapters of the BPGHS notes for its science.

    python topics.py BACKUP_DIR [paper-id ...]

Each chapter's notes give it a vocabulary: the words and pairs of words its notes use much more
than the other chapters' (tf-idf). A question's words (what words.json has inside its stretches of
page) are looked through for each chapter's vocabulary, and the best chapter is its topic, with the
next one too when it scores nearly as well. Looked for in the question's letters run together,
since OCR of an old scan runs words together ("Anaqueoussolutionof").

Writes work/<id>/topics.json, {question: [topic, ...]}, and work/topics.json, {subject: [topic
names]}, a topic being its place in that list.
"""
import collections
import glob
import json
import math
import os
import re
import sys

import fitz

import questions
import words

WORK = words.WORK
NOTES = {
    'Biology': 'Biology/BPGHS Complete Notes',
    'Chemistry': 'Chemistry/BPGHS Complete Notes',
    'Physics': 'Physics/BPGHS + Notes (also for practical)/BPGHS Complete Notes',
}
# Each science's topics in the notes' order, and the file names that hold each (case ignored).
TOPICS = {
    'Biology': [
        ('Cell structure and organisation', r'Chap 1 '), ('Movement of substances', r'Chap 2 '),
        ('Biological molecules', r'Chap 3 '), ('Enzymes', r'Chap 4 '), ('Nutrition in humans', r'Chap 5 '),
        ('Transport in humans', r'Chap 6 '), ('Respiration in humans', r'Chap 7 '), ('Excretion in humans', r'Chap 8 '),
        ('Homeostasis and hormones', r'Chap 9 '), ('Nervous system and the eye', r'Chap 10 '),
        ('Infectious diseases', r'Chap 11 '), ('Nutrition and transport in plants', r'Chap 12 '),
        ('Organisms and their environment', r'Chap 13 '), ('Molecular genetics', r'Chap 14 '),
        ('Modes of reproduction', r'Chap 15 '), ('Reproduction in plants', r'Chap 16 '),
        ('Reproduction in humans', r'Chap 17 '), ('Inheritance', r'Chap 18 '),
    ],
    'Chemistry': [
        ('Experimental chemistry', r'Chapter 1：'), ('Kinetic particle theory', r'Chapter 2：'),
        ('Atomic structure', r'Chapter 3：'), ('Chemical bonding and structure', r'Chapter 4 & 5：'),
        ('Formulae and equations', r'Chapter 6：'), ('Mole concept and stoichiometry', r'Chapter 7：'),
        ('Acids and bases', r'Chapter 8：'), ('Salts', r'Chapter 9：'), ('Ammonia', r'Chapter 10：'),
        ('Qualitative analysis', r'Chapter 11：'), ('Redox', r'Chapter 12：'), ('Electrochemistry', r'Chapter 13：'),
        ('The Periodic Table', r'Chapter 14：'), ('Reactivity series', r'Chapter 15：'),
        ('Chemical energetics', r'Chapter 16：'), ('Rate of reactions', r'Chapter 17：'),
        ('Fuels and crude oil', r'Chapter 18：'), ('Hydrocarbons', r'Chapter 19：'),
        ('Alcohols, acids and esters', r'Chapter 20：'), ('Polymers', r'Chapter 21：'),
        ('Air quality', r'Chapter 22：'),
    ],
    'Physics': [
        ('Measurements', r'Chapter 1 '), ('Kinematics', r'Chapter 2 '), ('Dynamics', r'Chapter 3&4 '),
        ('Turning effect of forces', r'Chapter 5 '), ('Pressure', r'Chapter 6 '), ('Energy', r'Chapter 7 '),
        ('Kinetic particle model', r'Chapter 8'), ('Thermal processes', r'Chapter 9'),
        ('Thermal properties of matter', r'Chapter 10'), ('Waves and sound', r'Chapter 11&12'),
        ('Electromagnetic spectrum', r'Chapter 13'), ('Light', r'Chapter 14'), ('Static electricity', r'Chapter 15'),
        ('Current electricity', r'Chapter 16 '), ('D.C. circuits', r'Chapter 17 '),
        ('Practical electricity', r'Chapter 18 '), ('Magnetism', r'Chapter 19 '), ('Electromagnetism', r'Chapter 20 '),
        ('Electromagnetic induction', r'Chapter 21 '), ('Radioactivity', r'Chapter 22 '),
    ],
}
# Physics' Energy notes are a scan without text: its vocabulary by hand.
BY_HAND = {
    ('Physics', 'Energy'): 'kinetic energy potential energy gravitational potential energy work done power efficiency '
                           'useful energy output input joule watt conservation of energy energy transfer energy store '
                           'chemical potential energy elastic potential energy dissipated wasted renewable '
                           'non-renewable fossil fuel hydroelectric solar wind tidal geothermal nuclear power output power input '
                           'rate of work done rate of energy transfer power supplied average power motor lifts '
                           'raised height gain in potential energy loss of kinetic energy',
}
TERMS = 600         # each topic's vocabulary, its words most its own
MIN_LETTERS = 5     # shorter words turn up inside others too often to count
SECOND = 0.8       # a second topic when it scores this much of the first's
STOP = set('which about these their there where would could should other given shown diagram table graph '
           'answer correct statement statements following student students below above using value '
           'chapter notes teacher teachers page figure question questions example examples explain state '
           'describe include includes because therefore however called number numbers'.split())


def letters(text):
    return re.sub(r'[^a-z]', '', text.lower())


def notes_text(root, subject, pattern):
    """A chapter's notes, as words: the students' notes where there are teachers' too (the same)."""
    files = [f for f in glob.glob(os.path.join(root, NOTES[subject], '**', '*'), recursive=True)
             if os.path.isfile(f) and pattern.lower() in f.replace('\\', '/').lower()]
    students = [f for f in files if 'teacher' not in os.path.basename(f).lower()]
    out = []
    for f in students or files:
        try:
            out += [page.get_text() for page in fitz.open(f, filetype='pdf')]
        except Exception:
            pass
    return ' '.join(out)


def tokens(text):
    ws = [w for w in re.findall(r'[a-z]+', text.lower())]
    singles = [w for w in ws if len(w) >= MIN_LETTERS and w not in STOP]
    pairs = [a + b for a, b in zip(ws, ws[1:]) if len(a) >= 3 and len(b) >= 3 and a not in STOP and b not in STOP]
    return singles + pairs


def vocabularies(root, subject):
    """Each topic's terms and their weights."""
    counts = []
    for name, pattern in TOPICS[subject]:
        text = BY_HAND.get((subject, name)) or notes_text(root, subject, pattern)
        c = collections.Counter(tokens(text))
        if (subject, name) in BY_HAND:
            c = collections.Counter({t: 20 for t in c})
        counts.append(c)
    df = collections.Counter(t for c in counts for t in c)
    n = len(counts)
    out = []
    for c in counts:
        total = sum(c.values()) or 1
        weights = {t: (k / total) ** 0.5 * math.log(n / df[t]) for t, k in c.items() if df[t] < n and k >= 2}
        best = sorted(weights.items(), key=lambda kv: -kv[1])[:TERMS]
        top = best[0][1] if best else 1
        out.append({t: w / top for t, w in best})
    return out


def question_texts(pages):
    """Each question's words, stem and all, from where crop.py cuts it."""
    found, shared = questions.parts(pages)
    out = collections.defaultdict(list)

    def inside(p, top, bottom):
        return [w[0] for w in pages[p] if top <= (w[2] + w[4]) / 2 <= bottom]
    for n, p, top, bottom in found:
        if n > 0:
            out[n] += inside(p, top, bottom)
    for n, stretches in shared.items():
        for p, top, bottom in stretches:
            out[n] += inside(p, top, bottom)
    return {n: ' '.join(ws) for n, ws in out.items()}


def tag(text, vocab):
    """The question's topics, best first, and every topic's score."""
    run = letters(text)
    scores = [sum(w * min(2, run.count(t)) for t, w in v.items()) for v in vocab]
    best = max(range(len(scores)), key=lambda i: scores[i])
    if scores[best] <= 0:
        return [], scores
    second = sorted(range(len(scores)), key=lambda i: -scores[i])[1]
    return [best] + ([second] if scores[second] >= SECOND * scores[best] else []), scores


def main():
    root = sys.argv[1].replace(chr(92), '/').rstrip('/') + '/Science/'
    only = set(sys.argv[2:])
    vocab = {s: vocabularies(root, s) for s in TOPICS}
    json.dump({s: [name for name, _ in TOPICS[s]] for s in TOPICS}, open(os.path.join(WORK, 'topics.json'), 'w'), indent=1)
    for p in json.load(open(os.path.join(WORK, 'manifest.json'), encoding='utf-8')):
        pid = words.paper_id(p)
        path = os.path.join(WORK, pid, 'words.json')
        if (only and pid not in only) or not os.path.exists(path):
            continue
        texts = question_texts(json.load(open(path, encoding='utf-8')))
        out = {str(n): tag(t, vocab[p['subject']])[0] for n, t in sorted(texts.items())}
        json.dump(out, open(os.path.join(WORK, pid, 'topics.json'), 'w'))
        untagged = sum(not v for v in out.values())
        print('%-28s %2d questions%s' % (pid, len(out), '  %d without a topic' % untagged if untagged else ''), flush=True)


if __name__ == '__main__':
    main()
