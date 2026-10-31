"""Tags each question with the topics it covers: the chapters of the BPGHS notes for its science.

    python topics.py [paper-id ...]

Each topic has a hand-made list of key terms (keyterms.py). A question's words (what words.json
has inside its stretches of page) are looked through for each topic's terms, each place in the
question counted once, by the longest term there ("light intensity" once, not again as "light").
A term only one topic lists counts 1, a term shared with others ONE_OF_MANY. A topic is taken
when it scores at least ENOUGH and clearly more than the next (MARGIN); the next is taken too when
it also scores ENOUGH and nearly as much (SECOND).

A question that isn't clear-cut (too few terms, or two topics close) is not guessed at: it's listed
in work/topics_review.json, to be read by eye, and what's decided goes in
content/topic_overrides.json (private, like the pack), {paper: {question: [topic names]}}, which
wins over the terms.

A term is looked for at the start of a word, the spaces between its words optional, since OCR of
an old scan runs words together ("Anaqueoussolutionof"); a term of SHORT letters or fewer ("iris",
"lens") only as a word on its own.

Writes work/<id>/topics.json, {question: [topic, ...]}, and work/topics.json, {subject: [topic
names]}, a topic being its place in that list.
"""
import collections
import json
import os
import re
import sys

import questions
import words
from keyterms import KEY_TERMS

WORK = words.WORK
OVERRIDES = os.path.join(os.path.dirname(__file__), '..', '..', 'content', 'topic_overrides.json')
# Each science's topics, in the order of the notes' chapters.
TOPICS = {s: list(KEY_TERMS[s]) for s in KEY_TERMS}
ENOUGH = 2.0        # what a topic must score to be a question's
MARGIN = 1.5        # ...and by how much it must beat the next, to be sure
SECOND = 0.75       # the next is a second topic when it scores ENOUGH and this much of the first
ONE_OF_MANY = 0.5   # what a term several topics list counts for
SHORT = 4


def letters(text):
    return re.sub(r'[^a-z]', '', text.lower())


def terms(subject):
    """Each topic's terms, as patterns, with what each counts for."""
    lists = [[letters(t) for t in KEY_TERMS[subject][name]] for name in TOPICS[subject]]
    seen = collections.Counter(t for ts in lists for t in set(ts))
    return [{t: (pattern(t), 1.0 if seen[t] == 1 else ONE_OF_MANY) for t in ts if len(t) >= 2} for ts in lists]


def pattern(term):
    """[term] at the start of a word, spaces between its letters optional (so "active transport" and
    OCR's "activetransport" both match, but "ratio" doesn't inside "concentration"); a short one
    as a whole word."""
    body = ' ?'.join(term)
    return re.compile(r'(?<![a-z])' + body + (r'(?![a-z])' if len(term) <= SHORT else ''))


def score(spaced, vocab):
    """A topic's score in a question ([spaced], its words, lower case, single spaces), and the terms
    found: each place counted once, by the longest term there ("light intensity" once, not again
    as "light")."""
    total, found = 0.0, []
    for t, (rx, w) in sorted(vocab.items(), key=lambda kv: -len(kv[0])):
        if rx.search(spaced):
            total += w
            found.append(t)
            spaced = rx.sub('#', spaced)
    return total, found


def tag(text, vocab):
    """The question's topics, best first; whether that's clear-cut; and each topic's score and terms."""
    spaced = ' ' + re.sub(r'[^a-z]+', ' ', text.lower()) + ' '
    scored = [score(spaced, v) for v in vocab]
    order = sorted(range(len(scored)), key=lambda i: -scored[i][0])
    best, nxt = order[0], order[1]
    s1, s2 = scored[best][0], scored[nxt][0]
    if s1 < ENOUGH:
        return [], False, scored
    tags = [best] + ([nxt] if s2 >= ENOUGH and s2 >= SECOND * s1 else [])
    sure = s1 - s2 >= MARGIN or len(tags) == 2
    return tags, sure, scored


def question_texts(pages, pid=None):
    """Each question's words, stem and all, from where crop.py cuts it."""
    found, shared = questions.parts(pages, questions.manual(pid) if pid else None)
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


def main():
    only = set(sys.argv[1:])
    vocab = {s: terms(s) for s in TOPICS}
    overrides = json.load(open(OVERRIDES, encoding='utf-8')) if os.path.exists(OVERRIDES) else {}
    json.dump(TOPICS, open(os.path.join(WORK, 'topics.json'), 'w'), indent=1)
    review, counts = [], collections.Counter()
    for p in json.load(open(os.path.join(WORK, 'manifest.json'), encoding='utf-8')):
        pid = words.paper_id(p)
        path = os.path.join(WORK, pid, 'words.json')
        if (only and pid not in only) or not os.path.exists(path):
            continue
        names = TOPICS[p['subject']]
        mine = overrides.get(pid, {})
        out = {}
        for n, text in sorted(question_texts(json.load(open(path, encoding='utf-8')), pid).items()):
            if str(n) in mine:
                out[str(n)] = [names.index(t) for t in mine[str(n)]]
                counts['by eye'] += 1
                continue
            tags, sure, scored = tag(text, vocab[p['subject']])
            out[str(n)] = tags
            if sure:
                counts['sure'] += 1
            else:
                counts['to check'] += 1
                top = sorted(range(len(scored)), key=lambda i: -scored[i][0])[:3]
                review.append({'paper': pid, 'q': n, 'guess': [names[i] for i in tags],
                               'scores': [[names[i], round(scored[i][0], 1), scored[i][1][:6]] for i in top if scored[i][0] > 0]})
        json.dump(out, open(os.path.join(WORK, pid, 'topics.json'), 'w'))
    json.dump(review, open(os.path.join(WORK, 'topics_review.json'), 'w'), indent=1)
    print(dict(counts))


if __name__ == '__main__':
    main()
