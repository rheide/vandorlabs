"""Generate Pilot Seat armrest masks from the four authored height meshes."""
from pathlib import Path
import json
# Source meshes keep all indices, omitting only armrest faces on blocked sides.
assets=Path('src/main/resources/assets/vandorlabs');blockstates=assets/'blockstates/pilot_seat.json';data=json.loads(blockstates.read_text());variants=data['variants'];replacement={}
for key,value in variants.items():
 if key!='inventory':
  properties=dict(part.split('=') for part in key.split(','))
  if properties.pop('arms','0')!='0':continue
  key=','.join(k+'='+v for k,v in sorted(properties.items()))
 if key=='inventory':replacement[key]=value;continue
 for arms in range(4):
  changed=json.loads(json.dumps(value));model=changed.get('model','')
  if arms and model.endswith('.obj'):changed['model']=model[:-4]+'_arms_'+str(arms)+'.obj'
  replacement['arms='+str(arms)+','+key]=changed
for height in range(4):
 name='pilot_seat'+('_height_'+str(height) if height else '')
 text=(assets/'models/block'/(name+'.obj')).read_text()
 for arms in range(1,4):
  output=[];group='';vertices=[tuple(map(float,line.split()[1:])) for line in text.splitlines() if line.startswith('v ')]
  for line in text.splitlines():
   if line.startswith('g '):group=line[2:]
   skip=False
   if line.startswith('f ') and group.startswith('armrest'):
    points=[vertices[int(ref.split('/')[0])-1][0] for ref in line.split()[1:]];center=sum(points)/len(points);skip=bool(arms&(1 if center<.5 else 2))
   if not skip:output.append(line)
  (assets/'models/block'/(name+'_arms_'+str(arms)+'.obj')).write_text('\n'.join(output)+'\n')
data['variants']=replacement;blockstates.write_text(json.dumps(data,indent=2)+'\n')
