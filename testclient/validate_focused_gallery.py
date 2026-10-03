#!/usr/bin/env python3
"""Validate fresh captures and the runtime contracts relevant to a focused run."""
import sys
from pathlib import Path
from PIL import Image
from export_gallery import SHOTS

source, prefix, target = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
selected = [name for name in SHOTS if name.startswith(prefix)]
assert selected, 'No mapped screenshots match ' + prefix
log = (source / 'client.log').read_text()

def require(marker):
    assert marker in log, 'Missing runtime result: ' + marker

for name in selected:
    image = source / ('shot_' + name + '.png')
    assert image.exists(), 'Missing focused screenshot: ' + str(image)
    with Image.open(image) as png:
        assert png.size == (1280, 720), 'Unexpected screenshot dimensions: ' + name
        png.verify()
    require('wrote shot_' + name + '.png')
    if name.startswith('gallery_trapdoor_followup_'):
        require('trapdoor-material-runtime PASS ' + name[len('gallery_trapdoor_followup_'):])
    if name.startswith('gallery_trapdoor_patch_'):
        require('diagonal-partial-patch-runtime PASS client ' + name[len('gallery_trapdoor_'):])
require('custom-materials-runtime PASS')
require('imported-materials-runtime PASS')
if target == 'trapdoors':
    for marker in ['sliding-next-mount-overlap-runtime PASS client closed',
                   'sliding-next-mount-overlap-runtime PASS client open',
                   'vanilla-trapdoor-alignment-runtime PASS client',
                   'opposing-next-block-runtime PASS client',
                   'next-block-open-placement-runtime PASS',
                   'trapdoor-movement-hinge-gui PASS',
                   'offset-trapdoor-hitbox-runtime PASS',
                   'trapdoor-side-placement-runtime PASS',
                   'trapdoor-offset-copy-neighbor-runtime PASS',
                   'trapdoor-group-offset-guard PASS',
                   'diagonal-trapdoor-hotbar-runtime PASS',
                   'diagonal-sliding-style-gui PASS into wall',
                   'diagonal-sliding-style-gui PASS over wall']:
        require(marker)
    for family in ('block', 'slab', 'door', 'trapdoor'):
        require('custom-picker-reopen-runtime PASS ' + family)
print('PASS: focused live checks and', len(selected), 'fresh captures for', prefix)
print('Full-gallery pixel analyzers were not run for this focused scope.')
