"""Keep wheel GUI sizes distinct and center oversized geometry in held views."""
import itertools,json,math
from pathlib import Path
root=Path(__file__).resolve().parents[1]/"src/main/resources/assets/vandorlabs/models"
def transformed(point,rotation):
 x,y,z=(v-8 for v in point);rx,ry,rz=map(math.radians,rotation)
 x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
 x,z=x*math.cos(ry)+z*math.sin(ry),z*math.cos(ry)-x*math.sin(ry)
 return x,y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
def view(points,rotation,scale):
 projected=[transformed(p,rotation) for p in points]
 center=[(min(p[a] for p in projected)+max(p[a] for p in projected))/2 for a in range(3)]
 return {"rotation":rotation,"translation":[round(-v*scale,6) for v in center],"scale":[round(scale,6)]*3}
for index,size in enumerate(("small","medium","large")):
 model=json.loads((root/f"block/wheel_{size}.json").read_text());points=[p for e in model["elements"] for p in itertools.product(*zip(e["from"],e["to"]))]
 rotation=[30,225,0];projected=[transformed(p,rotation) for p in points]
 span=max(max(p[a] for p in projected)-min(p[a] for p in projected) for a in (0,1))
 gui=view(points,rotation,(8,10.5,12.5)[index]/span)
 held=(.32,.28,.18)[index]
 display={"gui":gui,"firstperson_righthand":view(points,[0,45,0],held),"firstperson_lefthand":view(points,[0,45,0],held),"thirdperson_righthand":view(points,[75,45,0],held),"thirdperson_lefthand":view(points,[75,45,0],held),"ground":view(points,[0,0,0],(.25,.25,.18)[index]),"fixed":view(points,[0,0,0],(.4,.35,.22)[index])}
 for hand in ("firstperson_righthand","firstperson_lefthand"):display[hand]["translation"][1]+=4
 (root/f"item/wheel_{size}.json").write_text(json.dumps({"parent":f"vandorlabs:block/wheel_{size}","display":display},indent=2)+"\n")
