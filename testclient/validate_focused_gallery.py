#!/usr/bin/env python3
"""Validate fresh captures and the runtime contracts relevant to a focused run."""
import sys
from pathlib import Path
from PIL import Image
from export_gallery import SHOTS

source, prefix, target = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
selected = [name for name in SHOTS if name.startswith(prefix)]
if target == 'gallery_distant_geometry':
    selected=['gallery_distant_geometry']
if target == 'dialogs':
    selected = ['screen_gui', 'console_gui', 'half_console_gui', 'input_gui', 'full_input_gui', 'programmable_wall_gui', 'programmable_diagonal_width_gui', 'programmable_block_gui', 'programmable_face_overrides_gui', 'space_door_gui', 'programmable_light_gui', 'programmable_trigger_gui', 'thruster_gui', 'diagonal_trapdoor_gui', 'trapdoor_gui', 'trapdoor_surface_gui']
if target == 'door-selection':
    selected=['large_door_selection_'+str(i) for i in range(8)]+['regular_door_selection_'+str(i) for i in range(4)]+['large_door_x_margin_'+str(i) for i in range(4)]
if target == 'signals':
    shapes=[0,1,2,3,7,17,21,22,23,25,26,27]
    selected=['redstone_screen_'+str(shape)+'_'+suffix for shape in shapes for suffix in ('slider','slider_gui','slider_0','slider_1','slider_2','slider_3')]
if target == 'redstone-dialogs':
    selected=['channels_'+str(index) for index in range(13)]+['controls_hotbar_empty','controls_hotbar']
    for index in range(4):
        mounts=('', 'floor','ceiling') if index==0 else ('','south','east','west','floor','ceiling')
        for mount in mounts:
            rotations=range(4) if index>0 and mount in ('floor','ceiling') else range(1)
            for rotation in rotations:
                suffix=('_'+mount if mount else '')+('_r'+str(rotation) if rotation else '')
                selected += ['controls_'+str(index)+'_'+str(level)+suffix for level in range(4)]

if target == 'control-icons':
    selected=['controls_hotbar_empty','controls_hotbar','gallery_close_control_small_power_lever','gallery_close_control_large_power_lever','gallery_close_control_toggle_switch']
assert selected, 'No mapped screenshots match ' + prefix
log = (source / 'client.log').read_text()

def require(marker):
    assert marker in log, 'Missing runtime result: ' + marker

if target == 'door-selection': require('moved-door-selection PASS large=40 regular=24 xTips=16')
if target == 'control-icons' or target == 'gallery_close_control_': require('twin-power-lever-models PASS poses=32')
if target == 'control-icons':
    require('signal-control-mount-gallery PASS shots=24')
    require('binary-control-gallery PASS shots=3')
    require('toggle-switch-models PASS poses=48')
    require('toggle-switch-runtime PASS mounts=12')

# Actual Dynmap startup complements the structural checks when installed.
for control in ('thruster_lever','wall_slider','airliner_throttle','fighter_throttle'):
    assert not any('Block vandorlabs:'+control+'[' in line and 'not enough textures for faces' in line for line in log.splitlines()), 'Dynmap texture-slot mismatch: '+control

for name in selected:
    image = source / ('shot_' + name + '.png')
    assert image.exists(), 'Missing focused screenshot: ' + str(image)
    with Image.open(image) as png:
        assert png.size == (1280, 720), 'Unexpected screenshot dimensions: ' + name
        png.verify()
    if target not in ('signals','redstone-dialogs','control-icons'): require('wrote shot_' + name + '.png')
    if name.startswith('gallery_trapdoor_followup_'):
        require('trapdoor-material-runtime PASS ' + name[len('gallery_trapdoor_followup_'):])
    if name.startswith('gallery_trapdoor_patch_'):
        require('diagonal-partial-patch-runtime PASS client ' + name[len('gallery_trapdoor_'):])
if target == 'signals':
    for shape in shapes:
        for step in range(4):
            with Image.open(source / ('shot_redstone_screen_'+str(shape)+'_slider_'+str(step)+'.png')) as png:
                pixels=png.convert('RGB').getdata()
                if step==0: count=sum(1 for r,g,b in pixels if r>220 and 120<g<210 and b<100)
                else: count=sum(1 for r,g,b in pixels if r<100 and g>170 and b>190)
                assert count>=40, 'Missing '+('amber Off' if step==0 else 'cyan powered')+' indicator: '+str(shape)+'/'+str(step)
if target == 'diagonal-opening-modes':
    selected=['gallery_trapdoor_new_'+shape+'_'+motion+'_'+pose for shape in ('bentnorth','bentsouth','benteast','bentwest','joinednorth','joinedsouth','joinedeast','joinedwest') for motion in ('left','right','horizontal','vertical','x') for pose in ('closed','open')]
if target == 'gallery_distant_geometry':
    require('distant-geometry-runtime PASS')
    import numpy as np
    pixels=np.asarray(Image.open(source/'shot_gallery_distant_geometry.png').convert('RGB'),dtype=np.int16)
    for label,box,minimum in [('gear',(668,334,680,363),25),
                              ('walls',(635,349,644,373),50),
                              ('ramp',(581,347,619,363),80)]:
        x1,y1,x2,y2=box;region=pixels[y1:y2,x1:x2]
        neutral=(region.max(axis=2)-region.min(axis=2)<24)&(region.mean(axis=2)<180)
        assert np.count_nonzero(neutral)>=minimum, 'Distant geometry not visible: '+label
    print('PASS: gear, diagonal walls and ramp surfaces visible beyond 64 blocks')
if target == 'signals':
    for shape in shapes: require('redstone-slider-runtime PASS shape='+str(shape))
    require('integrated-screen-duplifier-runtime PASS')
if target == 'redstone-dialogs':
    require('channel-gui-runtime PASS')
    require('signal-control-runtime PASS')
    require('signal-control-models PASS')
    require('signal-control-visuals PASS')
require('custom-materials-runtime PASS')
require('imported-materials-runtime PASS')
if target == 'dialogs':
    for marker in ('landing-gear-reload PASS', 'diagonal-screen-item-runtime PASS', 'short-trapdoor-labels PASS', 'industrial-category-runtime PASS'):
        require(marker)
    for gui in ('GuiProgrammableWall', 'GuiProgrammableTrapdoor', 'GuiSpaceDoor', 'GuiProgrammableLight', 'GuiAnimatedScreenSelector', 'GuiProgrammableInput', 'GuiProgrammableHalfConsole', 'GuiProgrammableTrigger', 'GuiRedstoneChannel'):
        require('programmable-dialog-layout PASS ' + gui)
    for family in ('block', 'slab', 'door', 'trapdoor'):
        require('custom-picker-reopen-runtime PASS ' + family)
if target == 'control-icons':
    require('signal-control-icons PASS')
    require('signal-control-models PASS')
if target == 'storage':
    for marker in ('storage-inventory-runtime PASS','storage-material-runtime PASS','storage-gui-runtime PASS','storage-hotbar-runtime PASS','storage-faces-gui PASS override','storage-faces-gui PASS inherited'):
        require(marker)
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
