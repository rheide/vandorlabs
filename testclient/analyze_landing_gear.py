#!/usr/bin/env python3
"""Check that retraction returns all three wheel assemblies to the same rendered pose."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image

root=Path(sys.argv[1])
box=(350,70,850,270)
def pixels(name):
    return np.asarray(Image.open(root/f'shot_gallery_v12_{name}.png').convert('RGB').crop(box),dtype=np.int16)
closed=pixels('gear')
returned=pixels('gear_retracted')
changed=np.max(np.abs(closed-returned),axis=2)>3
assert np.count_nonzero(changed)==0, 'Gear did not return to the retracted pose'
# A nonempty comparison prevents a pair of missing fixtures from passing.
extended=pixels('gear_extended')
assert np.count_nonzero(np.max(np.abs(closed-extended),axis=2)>3)>300, 'Gear extension is not visible'
log=(root/'client.log').read_text()
assert 'seat-gear-gui PASS' in log, 'Seat/gear configuration packets did not pass'
print('PASS: all three gear sizes return to the same pixels after retraction; extension and configuration dialogs verified')
