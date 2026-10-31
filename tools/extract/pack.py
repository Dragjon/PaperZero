"""Builds the private content pack the app reads, from the cut questions and found answers.

    python pack.py [OUT_DIR]   (default: ../../content/pack)

OUT_DIR/index.json lists the papers; OUT_DIR/<id>/paper.json has each question's answer and
image (and explanation image, when there's one); the images are gray WebP. A question goes in
only with both its picture and its answer; a paper only with most of its questions.
"""
import json
import os
import sys

import cv2

import words

WORK = words.WORK
WIDTH = 1100        # widest a question's picture is kept (pixels): plenty for a phone, zoomed in
QUALITY = 80
MIN_QUESTIONS = 30  # a paper with fewer good questions than this is left out, to look at again


def webp(src, dst):
    img = cv2.imread(src, cv2.IMREAD_GRAYSCALE)
    if img.shape[1] > WIDTH:
        img = cv2.resize(img, (WIDTH, int(img.shape[0] * WIDTH / img.shape[1])), interpolation=cv2.INTER_AREA)
    cv2.imwrite(dst, img, [cv2.IMWRITE_WEBP_QUALITY, QUALITY])


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..', 'content', 'pack')
    os.makedirs(out, exist_ok=True)
    index = []
    for p in json.load(open(os.path.join(WORK, 'manifest.json'), encoding='utf-8')):
        pid = words.paper_id(p)
        d = os.path.join(WORK, pid)
        qdir = os.path.join(d, 'q')
        apath = os.path.join(d, 'answers.json')
        if not (os.path.isdir(qdir) and os.path.exists(apath)):
            continue
        answers = json.load(open(apath))
        qs = []
        os.makedirs(os.path.join(out, pid), exist_ok=True)
        for n in range(1, 61):
            src = os.path.join(qdir, '%02d.png' % n)
            if not os.path.exists(src) or str(n) not in answers:
                continue
            webp(src, os.path.join(out, pid, '%02d.webp' % n))
            q = {'n': n, 'answer': answers[str(n)], 'image': '%02d.webp' % n}
            ex = os.path.join(d, 'explain', '%02d.png' % n)
            if os.path.exists(ex):
                webp(ex, os.path.join(out, pid, 'e%02d.webp' % n))
                q['explain'] = 'e%02d.webp' % n
            qs.append(q)
        if len(qs) < MIN_QUESTIONS:
            print('%-28s left out: %d questions' % (pid, len(qs)))
            continue
        json.dump({'id': pid, 'questions': qs}, open(os.path.join(out, pid, 'paper.json'), 'w'), indent=0)
        index.append({'id': pid, 'subject': p['subject'], 'source': p['source'], 'year': p['year'], 'count': len(qs)})
        print('%-28s %d questions' % (pid, len(qs)))
    json.dump({'version': 1, 'papers': index}, open(os.path.join(out, 'index.json'), 'w'), indent=1)
    print(len(index), 'papers,', sum(p['count'] for p in index), 'questions')


if __name__ == '__main__':
    main()
