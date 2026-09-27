#!/usr/bin/env python3
"""Compare occupied and empty hotbar slots; icons must stay inside their frames."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image
root=Path(sys.argv[1])
empty=np.asarray(Image.open(root/'shot_connected_seat_hotbar_empty.png').convert('RGB'),dtype=np.int16)
filled=np.asarray(Image.open(root/'shot_connected_seat_hotbar.png').convert('RGB'),dtype=np.int16)
assert empty.shape==filled.shape==(720,1280,3), 'Unexpected hotbar viewport'
# The test client uses auto GUI scale 3 at 1280x720 (427x240 logical pixels).
scale=3
left=(427//2-91)*scale
top=(240-22)*scale
changed=np.max(np.abs(empty-filled),axis=2)>25
for slot in range(8):
    x=left+(slot*20+3)*scale
    y=(240-19)*scale
    # Include the surrounding frame in the search so oversize pixels cannot hide.
    mask=changed[max(0,top-24):720,x-2*scale:x+18*scale]
    yy,xx=np.nonzero(mask)
    assert len(xx)>40,f'Slot {slot}: invisible item icon'
    xx=xx+x-2*scale;yy=yy+max(0,top-24)
    assert xx.min()>=x-scale and xx.max()<x+17*scale, f'Slot {slot}: icon crosses side frame'
    assert yy.min()>=y-scale and yy.max()<y+17*scale, f'Slot {slot}: icon crosses top/bottom frame ({yy.min()}..{yy.max()})'
print('PASS: low/default/high Luxury and Military Seat icons and both gear icons fit their hotbar frames')
