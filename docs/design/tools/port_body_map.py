"""Ports the approved body-map artwork into Kotlin (#311).

Usage (from the repo root):
  python docs/design/tools/port_body_map.py docs/design/reference/Anatomy.dc.html app/src/main/java/com/example/gymtrack/core/ui/components/BodyMapArt.kt
"""
import re, json, sys
src, out = sys.argv[1], sys.argv[2]
s = open(src, encoding='utf-8').read()

def compact(d):
    d = re.sub(r'(\d)\.0(?=\D|$)', r'\1', d)
    d = re.sub(r'\s+', ' ', d).strip()
    return d

def section(start, end):
    a = s.index(start); b = s.index(end, a) if end else len(s)
    return s[a:b]

front = section('<g display="{{frontDisp}}">', '<g display="{{backDisp}}">')
back = section('<g display="{{backDisp}}">', '<sc-if') if '<sc-if' in s[s.index('{{backDisp}}'):] else section('<g display="{{backDisp}}">', '</svg>')

def parse(seg):
    res = {'sil': [], 'npUnder': [], 'npOver': [], 'regions': [], 'detail': [], 'outMain': [], 'outGhost': []}
    det = re.search(r'<g fill="none" stroke="\{\{detail\}\}"[^>]*>(.*?)</g>', seg, re.S)
    res['detail'] = [compact(d) for d in re.findall(r' d="([^"]*)"', det.group(1))]
    out = re.search(r'<g display="\{\{outDisp\}\}"[^>]*>\s*<g stroke-width="1.15"[^>]*>(.*?)</g>\s*<g transform="translate\(0.5 0.45\)"[^>]*>(.*?)</g>', seg, re.S)
    res['outMain'] = [compact(d) for d in re.findall(r' d="([^"]*)"', out.group(1))]
    res['outGhost'] = [compact(d) for d in re.findall(r' d="([^"]*)"', out.group(2))]
    body = seg[:det.start()]
    seen_region = False
    for m in re.finditer(r'<path d="([^"]*)" fill="\{\{([^}]*)\}\}"', body):
        d, fill = compact(m.group(1)), m.group(2)
        if fill == 'sil': res['sil'].append(d)
        elif fill == 'np': res['npOver' if seen_region else 'npUnder'].append(d)
        elif fill.startswith('f.'):
            seen_region = True; res['regions'].append((fill[2:], d))
        else: raise SystemExit('unknown fill ' + fill)
    return res

F, B = parse(front), parse(back)
L = json.loads(re.search(r'const AN_L = (\{.*?\});\n', s, re.S).group(1))

ids = json.loads(re.search(r'const AN_IDS = (\[.*?\]);', s).group(1))
enum = {}
for i in ids:
    enum[i] = re.sub(r'([A-Z])', r'_\1', i).upper()

def kstr(d): return '"' + d + '"'
def klist(xs, ind='            '):
    return 'listOf(\n' + ''.join(f'{ind}{kstr(x)},\n' for x in xs) + ind[:-4] + ')'

def view(name, v, loops):
    regions = ''.join(f'            BodyMapArtRegion(Muscle.{enum[k]}, {kstr(d)}),\n' for k, d in v['regions'])
    lp = ''.join(f'            Muscle.{enum[k]} to {kstr(compact(d))},\n' for k, d in loops.items())
    return f'''    val {name} = BodyMapArtView(
        silhouette = {klist(v['sil'])},
        partsUnder = {klist(v['npUnder'])},
        regions = listOf(
{regions}        ),
        partsOver = {klist(v['npOver'])},
        details = {klist(v['detail'])},
        pencilOutline = {klist(v['outMain'])},
        pencilGhost = {klist(v['outGhost'])},
        pencilLoops = mapOf(
{lp}        ),
    )
'''

kt = f'''package com.example.gymtrack.core.ui.components

import com.example.gymtrack.core.muscle.Muscle

// GENERATED from docs/design/reference/Anatomy.dc.html (the approved artwork, #311) by
// docs/design/tools/port_body_map.py; do not hand-edit shapes here, fix the artwork and re-port. Coordinates are the
// artwork's 820 × 460 viewBox: the front figure sits in x 0..400, the back figure in x 420..820.
// Path data uses only absolute M, L, C and Z.

/** One fillable piece of a region, in artwork draw order (later pieces paint on top). */
internal class BodyMapArtRegion(val muscle: Muscle, val d: String)

/** All layers of one figure, bottom to top, as SVG path data. */
internal class BodyMapArtView(
    /** Body silhouette (torso, arms, legs). */
    val silhouette: List<String>,
    /** Non-muscle parts under the muscles (head, hands, knees, feet). */
    val partsUnder: List<String>,
    val regions: List<BodyMapArtRegion>,
    /** Non-muscle parts drawn over the muscles (neck, kneecaps). */
    val partsOver: List<String>,
    /** Thin anatomical detail lines (clavicles, navel, spine). */
    val details: List<String>,
    /** Notebook only: the graphite contour pass. */
    val pencilOutline: List<String>,
    /** Notebook only: the lighter re-trace pass, drawn offset by (0.5, 0.45). */
    val pencilGhost: List<String>,
    /** Notebook only: hand-drawn highlight loops around each region ("next muscle" guidance). */
    val pencilLoops: Map<Muscle, String>,
)

internal object BodyMapArt {{
    const val VIEW_WIDTH = 820f
    const val VIEW_HEIGHT = 460f
    const val BACK_OFFSET_X = 420f

{view('front', F, L['front'])}
{view('back', B, L['back'])}}}
'''
open(out, 'w', encoding='utf-8', newline='\n').write(kt)
for n, v in (('front', F), ('back', B)):
    print(n, {k: len(x) for k, x in v.items()}, sorted(set(k for k, _ in v['regions'])))
