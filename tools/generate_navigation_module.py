"""Reuse native module geometry and Navigation Chip artwork for the single table bay."""
import copy,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'src/main/resources/assets/vandorlabs/models'
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,separators=(',',':'))+'\n')
source=json.loads((BASE/'block/module_rack/blank_module.json').read_text())
source['textures']['navigation']='vandorlabs:items/data_chip_navigation'
for element in source['elements']:
 if element.get('name')=='cassette_body':element['faces']['up']={'texture':'#navigation','uv':[0,0,16,16]}
source['elements'].append({'name':'navigation_front','from':[5.5,6.5,16.015],'to':[8.5,9.5,16.025],'shade':False,'faces':{'south':{'texture':'#navigation','uv':[0,0,16,16]}}})
write(BASE/'block/module_rack/navigation_data_module.json',source)
write(BASE/'item/navigation_data_module.json',{'parent':'vandorlabs:block/module_rack/navigation_data_module'})
bay=copy.deepcopy(source);bay.pop('display',None)
for element in bay['elements']:
 oldfrom,oldto=element['from'],element['to'];element['from']=[oldfrom[0]+1,oldfrom[1]-4.25,16-oldto[2]+.928];element['to']=[oldto[0]+1,oldto[1]-4.25,16-oldfrom[2]+.928]
 faces=element['faces'];faces['north'],faces['south']=faces.get('south'),faces.get('north');element['faces']={k:v for k,v in faces.items() if v is not None}
write(BASE/'block/module_rack/navigation_bay_module.json',bay)
frame={'ambientocclusion':False,'textures':source['textures'],'elements':[]}
def cube(name,a,b,texture):frame['elements'].append({'name':name,'from':a,'to':b,'faces':{f:{'texture':'#'+texture,'uv':[0,0,16,16]} for f in ['north','south','east','west','up','down']}})
cube('recess',[2,2,.944],[14,5.5,.976],'dark')
cube('upper_rail',[1.75,5.5,.912],[14.25,5.75,1.04],'trim')
cube('lower_rail',[1.75,1.75,.912],[14.25,2,1.04],'trim')
cube('left_rail',[1.75,2,.912],[2,5.5,1.04],'trim')
cube('right_rail',[14,2,.912],[14.25,5.5,1.04],'trim')
write(BASE/'block/module_rack/navigation_bay_frame.json',frame)
print('Navigation module generated: native cassette, reused chip artwork, one low bay')
