"""Publish asserted in-game build stages into the per-multiblock documentation folders."""
import argparse,hashlib,json,re,shutil
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TITLES={'assembly_press':'Assembly Press','synthesis_tower':'Synthesis Tower','enclosed_gantry':'Enclosed Gantry',
        'meridian_core':'Pinnacle Meridian Drive','vector_fold_array':'Pinnacle Vector Fold Array','longburn_drive':'Vektor Longburn Drive',
        'paladyne_navigation_computer':'Paladyne Navigation Computer','seyr_navigation_computer':'Seyr Navigation Computer',
        'navigation_table_flat':'Flat Navigation Table','navigation_table_angled':'Angled Navigation Console'}


def frame(spec,x,y,z):
    w,h,d=spec['width'],spec['height'],spec['depth']
    return y==0 or x in (0,w-1) and z in (0,d-1) or y==h-1 and (x in (0,w-1) or z in (0,d-1))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('capture',type=Path);args=p.parse_args()
    records=json.loads((args.capture/'machine-build-steps.json').read_text())
    specs=json.loads((ROOT/'src/main/resources/assets/vandorlabs/data/frame_machines.json').read_text())
    by_design={}
    for record in records:
        assert record['asserted'] and (args.capture/record['image']).is_file()
        steps=by_design.setdefault(record['design'],{})
        assert record['step'] not in steps;steps[record['step']]=record
    expected={s['design']:(5 if s.get('chip_anchor') else 4) for s in specs}
    expected.update(navigation_table_flat=4,navigation_table_angled=4)
    assert set(by_design)==set(expected)
    for design,count in expected.items():assert set(by_design[design])==set(range(1,count+1)),design
    root=ROOT/'docs/multiblocks';root.mkdir(parents=True,exist_ok=True)
    rows=[]
    for spec in specs:
        design=spec['design'];slug=design.replace('_','-');folder=root/slug;folder.mkdir(exist_ok=True)
        w,h,d=spec['width'],spec['height'],spec['depth'];matter=sum(frame(spec,x,y,z) for y in range(h) for x in range(w) for z in range(d))-1
        core_name='Meridian Drive Core' if design=='meridian_core' else TITLES[design].replace('Pinnacle ','').replace('Vektor ','')+' Core'
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
        if spec.get('chip_anchor'):
            instructions.append(('Insert a Data Chip','Right-click the modeled chip holder with a Data Chip. The holder uses the existing Data Chip Mount behavior: one physical chip, company filtering, channels and the four insertion/return modes. The active appearance follows the reader output. Use the Configurizer or sneak-right-click the holder to configure it.'))
        for index,(heading,body) in enumerate(instructions,1):
            image=f'step-{index}.png';shutil.copy2(args.capture/by_design[design][index]['image'],folder/image)
            text+=f'## {index}. {heading}\n\n{body}\n\n![{TITLES[design]}: {heading.lower()}]({image})\n\n'
        text+='## Facing and removal\n\nThe same frame works along either horizontal axis and with the Core in any bottom corner. The placed facing is preferred when it fits. Sneak-right-click the Core to dismantle; on a machine with a holder, aim away from the holder to dismantle rather than configure it. Mining a reserved part dismantles the frame and removes that cell. Breaking the Core restores the remaining Matter Blocks.\n\n'
        if spec.get('chip_anchor'):text+='A retained chip stays in the Core during manual dismantling and works again after reassembly. Breaking the Core releases it once. Remove chips before rebuilding a machine when you want to keep them in your inventory.\n\n'
        text+='These models do not yet perform crafting, propulsion, navigation calculations or dimension transfer. Their status appearances are visual states.\n\n[All multiblock guides](../README.md) · [Data Chip Mount behavior](../../data-mounts.md)\n'
        (folder/'README.md').write_text(text)
        rows.append(f'| [{TITLES[design]}]({slug}/README.md) | {w} × {d} × {h} | {matter} + one Core |')
    for design in ['navigation_table_flat','navigation_table_angled']:
        flat=design.endswith('flat');folder=root/design.replace('_','-');folder.mkdir(exist_ok=True)
        descriptions=[('Place the first module','Place a navigation module facing the operating side. A standalone module has one chip holder.'),
                      ('Extend the front row','Place matching modules side by side, facing the same way. '+('Complete a three-module front row.' if flat else 'The screenshot shows two modules joined into one sloped screen.')),
                      ('Complete the layout','Add a second complete row behind the first to make a 3 × 2 rectangle.' if flat else 'Add the third matching module to extend the sloped display. Angled consoles join sideways only, not front to back.'),
                      ('Use the shared chip mount','Insert a Data Chip into the one front-center holder. Use the Configurizer or sneak-right-click for the standard company filter, channels and insertion/return behavior.')]
        text=f'# {TITLES[design]}\n\nThese are placeable modules, so they use **no Core block and no Matter frame**. The example needs **'+('six Flat Navigation Tables' if flat else 'three Angled Navigation Consoles')+'** and optionally one Data Chip.\n\n'
        for index,(heading,body) in enumerate(descriptions,1):
            shutil.copy2(args.capture/by_design[design][index]['image'],folder/f'step-{index}.png')
            text+=f'## {index}. {heading}\n\n{body}\n\n![{TITLES[design]}: {heading.lower()}](step-{index}.png)\n\n'
        text+='## Joining and editing\n\nAll members must have the same type, facing and height. Flat tables require a filled rectangle; holes and L shapes remain unjoined. Angled consoles require one row. Assemblies are limited to 256 loaded modules. A group touching an unloaded boundary waits for complete discovery rather than inventing another chip owner.\n\nThere is exactly one holder on a valid assembly, at the center of its front row. Even widths choose the smaller local-X cell. If resizing moves the holder, its chip and configuration move with it. Remove chips before merging occupied tables: only one retained chip can occupy the shared holder, and extras are dropped once. Chips awaiting return keep their original return owner.\n\nScreens remain blank for future navigation gameplay. Ordinary redstone output comes from the holder module; configured virtual channels use the existing dimension-wide channel bank.\n\n[All multiblock guides](../README.md) · [Data Chip Mount behavior](../../data-mounts.md)\n'
        (folder/'README.md').write_text(text)
    index='# Multiblock build guides\n\nEach guide shows successive stages captured in Minecraft. All frame machines reuse the same Core-and-Programmable-Matter assembly rules.\n\n| Assembly | Width × depth × height | Matter Blocks |\n| --- | --- | --- |\n'+'\n'.join(rows)+'\n\nNavigation displays join as ordinary placed modules:\n\n- [Flat Navigation Table](navigation-table-flat/README.md)\n- [Angled Navigation Console](navigation-table-angled/README.md)\n\n[Universal Constructor rules](../universal-constructors.md) · [Data Chip Mounts](../data-mounts.md)\n'
    (root/'README.md').write_text(index)
    print(f'Published {len(expected)} build guides and {len(records)} asserted stage screenshots')


if __name__=='__main__':main()
