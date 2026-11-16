"""Generate vector construction-stage diagrams for the four-high Enclosed Gantry."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/multiblocks/enclosed-gantry'
def point(x,y,z):return (155+(x-z)*24,205+(x+z)*12-y*24)
def polygon(points,color,stroke='#183b47'):
    return '<polygon points="'+' '.join('%.2f,%.2f'%point(*p) for p in points)+'" fill="'+color+'" stroke="'+stroke+'" stroke-width="1"/>'
def cube(x,y,z,core=False):
    colors=('#b8a0ea','#7855b2','#57398d') if core else ('#87d3da','#428d9b','#286474')
    return polygon([(x,y+1,z),(x+1,y+1,z),(x+1,y+1,z+1),(x,y+1,z+1)],colors[0])+polygon([(x,y,z),(x+1,y,z),(x+1,y+1,z),(x,y+1,z)],colors[1])+polygon([(x+1,y,z),(x+1,y,z+1),(x+1,y+1,z+1),(x+1,y+1,z)],colors[2])
def write(stage,coords,title,note):
    body=''.join(cube(x,y,z,(x,y,z)==(0,0,0)) for x,y,z in sorted(coords,key=lambda p:(p[1],-p[2],p[0])))
    text='<text x="16" y="26" font-size="17" fill="#e3f3f6">'+title+'</text><text x="16" y="51" font-size="12" fill="#b6cbd2">'+note+'</text><text x="16" y="288" font-size="12" fill="#b6cbd2">5 wide × 3 deep × 4 high · purple block = matching Core</text>'
    svg='<svg xmlns="http://www.w3.org/2000/svg" width="420" height="310" viewBox="0 0 420 310"><rect width="420" height="310" rx="5" fill="#13212b"/><g font-family="sans-serif">'+body+text+'</g></svg>\n'
    (OUT/('stage-'+str(stage)+'.svg')).write_text(svg)
base={(x,0,z) for x in range(5) for z in range(3)}
pillars=base|{(x,y,z) for x in (0,4) for z in (0,2) for y in range(1,4)}
frame=pillars|{(x,3,z) for x in range(5) for z in range(3) if x in (0,4) or z in (0,2)}
write(1,base,'1. Build the solid base','One Core and 14 Matter Blocks in the bottom layer.')
write(2,pillars,'2. Raise all four corner pillars','The tops are at height four, counting the base.')
write(3,frame,'3. Connect the top rim','34 Matter Blocks + one Core. Keep the roof centre open.')
write(4,frame,'4. Right-click the Core to form','Use the complete four-high frame before activation.')
