#!/usr/bin/env python3
"""Plot the recorded programmable rendering comparison without rerunning Minecraft."""
import csv
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np

root = Path(__file__).resolve().parents[1] / 'docs' / 'performance'

def read(name):
    with (root / name).open() as source:
        return {(r['block'], r['variant'], r['count']): r for r in csv.DictReader(source)}

before, after = read('before.csv'), read('after.csv')
controls = {'stone', 'stone_slab', 'redstone_lamp', 'glass_pane', 'chest', 'iron_door'}
keys = [k for k in after if k[2] == '64' and (
    k[1] == 'round_joined' or k[1] == 'default' and (
        k[0].startswith('vandorlabs:') and not k[0].endswith(('_hexagonal', '_wedge'))
        or k[0].split(':')[1] in controls))]
keys.sort(key=lambda k: float(after[k]['submit_p50_ms']))
labels = []
for key in keys:
    label = key[0].replace('vandorlabs:programmable_', '').replace('vandorlabs:', '')
    label = label.replace('minecraft:', 'Vanilla ').replace('_', ' ')
    if key[1] != 'default':
        label += ' (round, joined)'
    labels.append(label)
y = np.arange(len(keys))
fig, ax = plt.subplots(figsize=(10, 12))
ax.barh(y-.18, [float(before[k]['submit_p50_ms']) for k in keys], .35,
        color='#aab5bf', label='Original')
ax.barh(y+.18, [float(after[k]['submit_p50_ms']) for k in keys], .35,
        color='#087f8c', label='Optimized')
ax.set_yticks(y, labels, fontsize=9)
ax.invert_yaxis()
ax.set_xlabel('Median submission time for 64 instances (ms)')
ax.set_title('Programmable blocks and vanilla controls\n'
             'Mesa llvmpipe; warmed rendering batches, not gameplay FPS', loc='left', pad=15)
ax.set_axisbelow(True)
ax.grid(axis='x', alpha=.2)
ax.spines[['top', 'right']].set_visible(False)
ax.legend(loc='lower right', frameon=False)
fig.tight_layout()
fig.savefig(root / 'render-comparison.png', dpi=150)
plt.close(fig)
