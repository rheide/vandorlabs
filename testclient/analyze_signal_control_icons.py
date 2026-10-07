#!/usr/bin/env python3
"""Verify visible padding around every supplied control's real hotbar icon."""
from pathlib import Path
import sys
import numpy as np
from PIL import Image
root=Path(sys.argv[1])
empty=np.asarray(Image.open(root/'shot_controls_hotbar_empty.png').convert('RGB'),dtype=np.int16)
filled=np.asarray(Image.open(root/'shot_controls_hotbar.png').convert('RGB'),dtype=np.int16)
assert empty.shape==filled.shape==(720,1280,3),'Unexpected hotbar viewport'
scale=3;left=(427//2-91)*scale;top=(240-19)*scale
changed=np.max(np.abs(empty-filled),axis=2)>25
for slot in range(6):
    x=left+(slot*20+3)*scale
    mask=changed[top-scale:top+17*scale,x-scale:x+17*scale]
    yy,xx=np.nonzero(mask)
    assert len(xx)>35,f'Control {slot}: invisible icon'
    # One logical pixel of padding inside the 16x16 icon area on every side.
    assert xx.min()>=2*scale and xx.max()<16*scale,f'Control {slot}: missing side padding'
    assert yy.min()>=2*scale and yy.max()<16*scale,f'Control {slot}: missing vertical padding'
    print(f'Control {slot} padded icon PASS ({xx.max()-xx.min()+1} x {yy.max()-yy.min()+1} physical pixels)')

# Every combination of the four base heights, four tilt angles and four directions.
for page in range(24):
    shot=root/f'shot_controls_mount_icons_{page}.png'
    if not shot.exists():
        assert page==0, 'Missing configured icon page'
        break
    configured=np.asarray(Image.open(shot).convert('RGB'),dtype=np.int16)
    difference=np.max(np.abs(empty-configured),axis=2)>25
    for slot in range(8):
        x=left+(slot*20+3)*scale
        yy,xx=np.nonzero(difference[top-scale:top+17*scale,x-scale:x+17*scale])
        variant=page*8+slot
        assert len(xx)>25, f'Configured control {variant}: invisible icon'
        assert xx.min()>=2*scale and xx.max()<16*scale, f'Configured control {variant}: missing side padding'
        assert yy.min()>=2*scale and yy.max()<16*scale, f'Configured control {variant}: missing vertical padding'
    print(f'Configured control icons {page*8}..{page*8+7} padded PASS')
