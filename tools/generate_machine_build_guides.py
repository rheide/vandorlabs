"""Publish asserted in-game build stages into the per-multiblock documentation folders."""
import argparse,hashlib,json,re,shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TITLES={'assembly_press':'Assembly Press','synthesis_tower':'Synthesis Tower','enclosed_gantry':'Enclosed Gantry',
        'meridian_core':'Seyr Meridian Drive','vector_fold_array':'Pinnacle Vector Fold Array','longburn_drive':'Maxwell Drive',
        'paladyne_navigation_computer':'Paladyne Navigation Computer','seyr_navigation_computer':'Seyr Navigation Computer',
        'oculus_drive':'Pinnacle Oculus Drive','survey_dish':'Survey Dish','sentinel_array':'Sentinel Array',
        'standalone_dish':'Standalone Dish','standalone_antenna':'Standalone Antenna',
        'navigation_table_flat':'Flat Navigation Table','navigation_table_angled':'Angled Navigation Console'}


def frame(spec,x,y,z):
    w,h,d=spec['width'],spec['height'],spec['depth']
    return y==0 or x in (0,w-1) and z in (0,d-1) or y==h-1 and (x in (0,w-1) or z in (0,d-1))


NEW_GUIDES={'oculus_drive','survey_dish','sentinel_array','standalone_dish','standalone_antenna'}
CHIP_STEP=('Insert a Data Chip','The native Data Chip Mount is always visible on this model. Right-click it with any Data Chip to insert one physical item; empty-hand right-click removes it. The chip stays inserted and does not gate activation. Redstone settings alone control optional power activation. The machine never emits a redstone signal.')
DRIVES={'longburn_drive','meridian_core','oculus_drive'}
ENGINE_NOTES=('Open the **Engine** tab with the Configurizer. **Redstone: Disabled** keeps the drive powered at level 15. With **Redstone: Enabled**, the strongest physical input anywhere on the assembly or configured input channel supplies a level from 0 to 15. **Activate on: ON** uses that level; **Activate on: OFF** uses 15 minus that level. A resulting level of zero switches the drive off. Input channel 0 means no channel.\n\n'
 '**Brightness: On/Off** gives active effects full brightness; **Brightness: Signal** scales their brightness by the resulting level divided by 15. **Particles** selects Off, Light, Medium or Heavy. **Particles at level** sets a 0–15 threshold: in Signal mode, exhaust starts at or above this level, while the drive is active. On/Off brightness ignores the threshold. Particle intensity is selected separately from brightness. Defaults are Redstone Disabled, Activate on ON, On/Off brightness, Light particles and threshold 8. Right-click an option to select its previous value.\n\n'
 'These effects work on placed machinery and aboard vehicles, following the drive’s facing and the vehicle’s pose. The drive supplies **no vehicle thrust** and does not qualify a craft for Taxi or Hover. Data Chips are optional retained data, accept every company and never authorize or gate operation; there are no integrated Security settings.\n\n')
ACTIVITY={
 'longburn_drive':'The active exhaust is yellow/white, with the native casing and nozzle rings retained.',
 'meridian_core':'The active front cyan lights pulse. The cosmetic pulse pauses with the client; signal brightness scales its intensity.',
 'oculus_drive':'The default activity is **Active**. Right-click away from the reader to cycle Standby → Charging → Active. The two shutters open or close over one second, travelling inside the reserved assembly; an interrupted stroke reverses from its current position. The energized field is fullbright, while the enclosure stays world-lit.',
 'survey_dish':'The default activity is **Scanning**. Right-click away from the reader to cycle Off → Idle → Scanning. The dish, feed and yoke sweep together through ±55° on an eight-second loop. Starting and stopping fades the sweep over half a second; the base stays fixed.',
 'standalone_dish':'The default activity is **Scanning**. Right-click away from the reader to cycle Off → Idle → Scanning. The dish, feed and yoke sweep together through ±55° on an eight-second loop. Starting and stopping fades the sweep over half a second; the base stays fixed.',
 'sentinel_array':'The default activity is **Scanning**. Right-click away from the reader to cycle Off → Idle → Scanning. The mast stays fixed while the cyan and amber indicators animate.',
 'standalone_antenna':'The default activity is **Scanning**. Right-click away from the reader to cycle Off → Idle → Scanning. The mast stays fixed while the cyan and amber indicators animate.'}

def new_instructions(spec):
    if spec['design'] not in NEW_GUIDES:return None
    if spec['width']==2 and spec['depth']==2:
        return [('Build the base','Place a solid **2 × 2** bottom layer: one matching Core in the front-left corner, facing you, and **three Programmable Matter Blocks**.'),
                ('Add the two middle layers','Add **two solid 2 × 2 layers** of Programmable Matter Blocks above the base: **eight Matter Blocks** in total. The construction is now three blocks high; leave the fourth layer empty for the next step.'),
                ('Complete the top layer','Add **four Matter Blocks** as the final 2 × 2 layer. The finished construction is **four blocks high**. Every position in each 2 × 2 layer is a frame corner, so this design has **no hollow interior or open roof center**.'),
                ('Assemble','Right-click the Core to form the machine. Its occupied cells are reserved. If formation fails, correct the cell reported in chat and retry.')]
    return [('Build the base','Place a solid **3 × 3** bottom layer: one matching Core in the front-left corner, facing you, and **eight Programmable Matter Blocks**.'),
            ('Raise the corner pillars','Add one Matter Block above each of the **four corners**. This adds **four Matter Blocks** and makes each pillar two blocks high. Leave the other five positions in this middle layer empty.'),
            ('Connect the top rim','Add a **3 × 3 perimeter** as the third and final layer: **eight Matter Blocks**, with the center left empty. The completed frame uses twenty Matter Blocks plus its Core. Keep the middle-layer interior and the roof center empty.'),
            ('Assemble','Right-click the Core to form the machine. Its occupied cells are reserved. If formation fails, correct the cell reported in chat and retry.')]

def activity_notes(spec):
    if spec['design'] not in NEW_GUIDES|DRIVES:return ''
    if spec['design'] in DRIVES:
        return '## Operation and activation\n\n'+ACTIVITY[spec['design']]+'\n\n'+ENGINE_NOTES+'Select screen and keyboard artwork independently in their surface tabs. Model artwork preserves the supplied design.\n\n'
    text='## Operation and activation\n\n'+ACTIVITY[spec['design']]+'\n\nUse the Configurizer on the Core or any reserved cell to open the shared machine dialog. Enable Redstone to require physical power or configured input channels. Data Chips are optional retained data items and never gate activation; integrated mounts accept every Data Chip company.\n\n'
    if not spec.get('surfaces'):text+='This standalone design has no operator screen or keyboard. Its native Data Chip Mount is always visible on the front of the compact base; inserting a chip is optional.\n\n'
    else:text+='Select screen and keyboard artwork independently in the same dialog. Model artwork preserves the supplied design; Black and the standard material/screen catalog are also available.\n\n'
    text+='The machine emits no physical or channel redstone power. These appearances do not perform propulsion, dimensional travel, radar detection or navigation calculations.\n\n'
    return text

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('capture',type=Path);p.add_argument('--text-only',action='store_true',help='Update guide prose without replacing existing stage images');args=p.parse_args()
    records=json.loads((args.capture/'machine-build-steps.json').read_text())
    specs=json.loads((ROOT/'src/main/resources/assets/vandorlabs/data/frame_machines.json').read_text())
    by_design={}
    for record in records:
        assert record['asserted'] and (args.capture/record['image']).is_file()
        steps=by_design.setdefault(record['design'],{})
        assert record['step'] not in steps;steps[record['step']]=record
    expected={s['design']:(5 if s.get('chip_anchor') else 4) for s in specs}
    expected.update(navigation_table_flat=4,navigation_table_angled=4)
    for spec in specs:
        matter=sum(frame(spec,x,y,z) for y in range(spec['height']) for x in range(spec['width']) for z in range(spec['depth']))-1
        for record in by_design[spec['design']].values():
            assert all(record.get(key)==value for key,value in dict(width=spec['width'],depth=spec['depth'],height=spec['height'],matter=matter,core=spec['block']).items()), 'Capture construction metadata mismatch: '+spec['design']
    assert set(by_design)==set(expected)
    for design,count in expected.items():assert set(by_design[design])==set(range(1,count+1)),design
    root=ROOT/'docs/multiblocks';root.mkdir(parents=True,exist_ok=True)
    rows=[]
    for spec in specs:
        design=spec['design'];slug=design.replace('_','-');folder=root/slug;folder.mkdir(exist_ok=True)
        w,h,d=spec['width'],spec['height'],spec['depth'];matter=sum(frame(spec,x,y,z) for y in range(h) for x in range(w) for z in range(d))-1
        language=dict(line.split('=',1) for line in (ROOT/'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines() if '=' in line and not line.startswith('#'))
        core_name=language['tile.vandorlabs.'+spec['block']+'.name']
        text=f"# {TITLES[design]}\n\nBuild a **{w} wide × {d} deep × {h} high** assembly with **{matter} Programmable Matter Blocks** and **one {core_name}**. Core blocks are available in Creative, following the existing Universal Constructor system.\n\n"
        if w==1 and d==1:
            instructions=[('Place the Core','Place the matching Core on the ground, with its operating face toward you.'),
                          ('Add the middle block','Place one Programmable Matter Block directly above the Core.'),
                          ('Add the top block','Place the second Programmable Matter Block above the first. The column is now three blocks high.'),
                          ('Assemble','Right-click the Core to form the complete computer. The normal appearance is Idle. Right-click the formed computer to cycle Idle → Computing → Off; sneak-right-click to restore the three-block construction.')]
        else:
            instructions=[('Build the base',f'Lay out a solid {w} × {d} bottom layer. Put the matching Core in the front-left corner, facing you, and fill every other cell with Programmable Matter Blocks.'),
                          ('Raise the corner pillars',f'At each of the four corners, build up to a total height of {h} blocks, including the bottom layer. Leave the interior empty.'),
                          ('Connect the top rim','Connect the four pillar tops with a rectangular rim of Programmable Matter Blocks. Keep the middle of the roof open; do not fill the interior.'),
                          ('Assemble','Right-click the Core. The frame becomes the complete model, and its occupied cells are reserved. If formation fails, use the reported cell to check the frame and its empty interior.')]
        instructions=new_instructions(spec) or instructions
        if spec.get('chip_anchor'):instructions.append(CHIP_STEP)
        for index,(heading,body) in enumerate(instructions,1):
            image=f'step-{index}.png'
            if not args.text_only:shutil.copy2(args.capture/by_design[design][index]['image'],folder/image)
            text+=f'## {index}. {heading}\n\n{body}\n\n![{TITLES[design]}: {heading.lower()}]({image})\n\n'
        text+=activity_notes(spec)
        text+='## Facing and removal\n\nThe same frame works along either horizontal axis and with the Core in any bottom corner. The placed facing is preferred when it fits. Sneak-right-click the Core to dismantle; on a machine with a holder, aim away from the holder to dismantle rather than configure it. Mining a reserved part dismantles the frame and removes that cell. Breaking the Core restores the remaining Matter Blocks.\n\n'
        if spec.get('chip_anchor'):text+='A retained chip stays in the Core during manual dismantling and works again after reassembly. Breaking the Core releases it once. Remove chips before rebuilding a machine when you want to keep them in your inventory.\n\n'
        if spec.get('surfaces') and design not in NEW_GUIDES:text+='Use the Configurizer to select artwork independently for the fitted screen/half-screen and keyboard surfaces. Model artwork preserves the original design; Black and the standard material/screen/panel catalog are also available. See [Machine display settings](../../machine-displays.md) for redstone activation and optional retained Data Chips.\n\n'
        if design not in NEW_GUIDES:text+='These models do not yet perform crafting, propulsion, navigation calculations or dimension transfer. Their status appearances are visual states.\n\n[All multiblock guides](../README.md) · [Data Chip Mount behavior](../../data-mounts.md)\n'
        (folder/'README.md').write_text(text.rstrip()+'\n')
        rows.append(f'| [{TITLES[design]}]({slug}/README.md) | {w} × {d} × {h} | {matter} + one Core |')
    for design in ['navigation_table_flat','navigation_table_angled']:
        flat=design.endswith('flat');folder=root/design.replace('_','-');folder.mkdir(exist_ok=True)
        descriptions=[('Place the first module','Place a navigation module facing the operating side. Its native chip holder is already visible.'),
                      ('Extend the front row','Place matching modules side by side, facing the same way. '+('Complete a three-module front row.' if flat else 'The screenshot shows two modules joined into one sloped screen.')),
                      ('Complete the layout','Add a second complete row behind the first, facing the opposite direction, to make a 3 × 2 rectangle. Keep the buttons around the outside; the inner edges merge into one screen.' if flat else 'Add the third matching module to extend the sloped display. Angled consoles join sideways only, not front to back.'),
                      ('Use the shared chip mount','Use the Configurizer or sneak-right-click to choose screen artwork and optional redstone activation. Insert any Data Chip into the one shared exterior holder. The chip stays inserted and is not required for the selected image to appear.')]
        text=f'# {TITLES[design]}\n\nThese are placeable modules, so they use **no Core block and no Matter frame**. The example needs **'+('six Flat Navigation Tables' if flat else 'three Angled Navigation Consoles')+'** and optionally one Data Chip.\n\n'
        for index,(heading,body) in enumerate(descriptions,1):
            if not args.text_only:shutil.copy2(args.capture/by_design[design][index]['image'],folder/f'step-{index}.png')
            text+=f'## {index}. {heading}\n\n{body}\n\n![{TITLES[design]}: {heading.lower()}](step-{index}.png)\n\n'
        text+='## Joining and editing\n\nAll members must have the same type and height. Flat tables accept mixed facings and require a filled rectangle; holes and L shapes remain unjoined. Angled consoles require one row with a common facing. Assemblies are limited to 256 loaded modules. A group touching an unloaded boundary waits for complete discovery rather than inventing another chip owner.\n\nThere is always exactly one visible native-size Data Chip Mount on a valid assembly, just below the top of an exterior front panel. It accepts every Data Chip company and keeps the chip inserted until removed. Uniform rows choose the front-center module; even widths choose the smaller local-X cell. Mixed facings choose a deterministic exterior-facing module. If resizing moves the holder, its chip and configuration move with it. Remove chips before merging occupied tables: only one retained chip can occupy the shared holder, and extras are dropped once. Chips awaiting return keep their original return owner.\n\nChoose artwork from the Programmable Block materials and Programmable Screen designs, including static or animated playback. The default is black. These displays only consume redstone: no module emits physical or channel power. Enabling Redstone requires physical or channel power. Data Chips never gate the display, and integrated holders have no Security, company-filter or return-mode options. See [Navigation display settings](../../navigation-displays.md) for the full behavior table.\n\n[All multiblock guides](../README.md) · [Data Chip Mount behavior](../../data-mounts.md)\n'
        (folder/'README.md').write_text(text.rstrip()+'\n')
    index='# Multiblock build guides\n\nEach guide shows successive stages captured in Minecraft. All frame machines reuse the same Core-and-Programmable-Matter assembly rules.\n\n| Assembly | Width × depth × height | Matter Blocks |\n| --- | --- | --- |\n'+'\n'.join(rows)+'\n\nNavigation displays join as ordinary placed modules:\n\n- [Flat Navigation Table](navigation-table-flat/README.md)\n- [Angled Navigation Console](navigation-table-angled/README.md)\n\n[Navigation display settings](../navigation-displays.md) · [Universal Constructor rules](../universal-constructors.md) · [Data Chip Mounts](../data-mounts.md)\n'
    (root/'README.md').write_text(index)
    print(f'Updated {len(expected)} guide texts; '+('existing stage images unchanged' if args.text_only else f'published {len(records)} asserted stage screenshots'))


if __name__=='__main__':main()
