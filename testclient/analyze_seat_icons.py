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
for slot in range(9):
    x=left+(slot*20+3)*scale
    y=(240-19)*scale
    # Include the surrounding frame in the search so oversize pixels cannot hide.
    mask=changed[max(0,top-24):720,x-2*scale:x+18*scale]
    # The selected ninth slot also equips the item. Ignore disconnected hand
    # geometry above/beside the hotbar; retain every component touching the
    # slot interior, including any icon pixels extending outside its frame.
    selected=np.zeros_like(mask)
    unseen=mask.copy()
    for row,col in zip(*np.nonzero(mask)):
        if not unseen[row,col]: continue
        pending=[(row,col)];unseen[row,col]=False;component=[];touches=False
        while pending:
            r,c=pending.pop();component.append((r,c))
            px=c+x-2*scale;py=r+max(0,top-24)
            touches |= x<=px<x+16*scale and y<=py<y+16*scale
            for dr,dc in ((-1,0),(1,0),(0,-1),(0,1)):
                nr,nc=r+dr,c+dc
                if 0<=nr<mask.shape[0] and 0<=nc<mask.shape[1] and unseen[nr,nc]:
                    unseen[nr,nc]=False;pending.append((nr,nc))
        if touches:
            for r,c in component:selected[r,c]=True
    yy,xx=np.nonzero(selected)
    assert len(xx)>40,f'Slot {slot}: invisible item icon'
    xx=xx+x-2*scale;yy=yy+max(0,top-24)
    assert xx.min()>=x-scale and xx.max()<x+17*scale, f'Slot {slot}: icon crosses side frame'
    assert yy.min()>=y-scale and yy.max()<y+17*scale, f'Slot {slot}: icon crosses top/bottom frame ({yy.min()}..{yy.max()})'
print('PASS: low/default/high Luxury and Military Seat icons and all three gear sizes fit their hotbar frames')
