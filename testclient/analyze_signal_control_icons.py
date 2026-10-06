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
for slot in range(4):
    x=left+(slot*20+3)*scale
    mask=changed[top-scale:top+17*scale,x-scale:x+17*scale]
    yy,xx=np.nonzero(mask)
    assert len(xx)>35,f'Control {slot}: invisible icon'
    # One logical pixel of padding inside the 16x16 icon area on every side.
    assert xx.min()>=2*scale and xx.max()<16*scale,f'Control {slot}: missing side padding'
    assert yy.min()>=2*scale and yy.max()<16*scale,f'Control {slot}: missing vertical padding'
    print(f'Control {slot} padded icon PASS ({xx.max()-xx.min()+1} x {yy.max()-yy.min()+1} physical pixels)')
