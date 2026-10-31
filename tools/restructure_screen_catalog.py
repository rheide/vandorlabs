"""Curate screen/control folders while retaining saved artwork identifiers and indices."""
import argparse,copy,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
DATA=ROOT/'generated-resources/assets/vandorlabs'
SEYR_CLASSIC={'communications','navigation','reactor'}
CONTROL_PREFIXES=('input_surfaces_','console_controls_','sliders_buttons_')

def restructure(catalog,blocks,language):
    result=copy.deepcopy(catalog);definitions=copy.deepcopy(blocks);names={};retired=set();active=set()
    for entry in result:
        category=entry.get('category','')
        if category.startswith('Controls/'):
            entry['category']=category.replace('Controls/Corporate/','Controls/').replace('Controls/Dense/','Controls/').replace('Controls/Half-height','Controls/Classic')
            entry['label']=re.sub(r'^\d+[\s._-]+','',entry['label'])
            continue
        if category!='Screens':continue
        half=entry['source'].startswith('console_inputs/');source=entry['source'].removeprefix('console_inputs/').removesuffix('_static');height='Half' if half else 'Full'
        if half and source.startswith(CONTROL_PREFIXES):continue
        company=re.fullmatch(r'company_(ashiro_mai|paladyne|pinnacle|seyr|vandor|vektor)_(full|half)_(.+)',source)
        if company:
            company,tier,subject=company.groups();kept='pinnacle' if company in ('pinnacle','vektor') else 'paladyne'
            if company=='seyr' and subject in SEYR_CLASSIC:
                for key in ('hidden','alias','retireAnimation'):entry.pop(key,None)
                entry.update(menuGroup='Classic',label=subject.title()+f' (Classic/{height})');active.add(source)
            elif company!=kept:
                entry.update(hidden=True,alias=f'screen_company_{kept}_{tier}_{subject}',retireAnimation=True);retired.add(source)
            else:
                group='Gold' if kept=='pinnacle' else 'Cool';entry.update(menuGroup=group,label=f'{subject.replace("_"," ").title()} ({group}/{height})')
        elif source in ('bridge_bare_sensors','bridge_framed_sensors'):
            variant='framed' if '_framed_' in source else 'bare';prefix='screen_console_inputs_' if half else 'screen_'
            entry.update(hidden=True,alias=f'{prefix}specialist_{variant}_sensors_static',retireAnimation=True);retired.add(source)
        elif source.startswith(('ship_','cruiser_')):entry['menuGroup']='Ship'
        elif source.startswith(('analysis_','bridge_','extra_','specialist_')):
            subject=re.sub(r'^(analysis|bridge|extra|specialist)_(bare_|framed_)?','',source)
            if source in ('specialist_bare_sensors','specialist_framed_sensors'):subject='radar'
            entry.update(menuGroup='Classic',label=subject.replace('_',' ').title()+f' (Classic/{height})')
        elif source in ('tactical_starmap','science_biology_wide_left','science_biology_wide_right') or source.startswith('console_'):
            entry.update(hidden=True,retireAnimation=True);retired.add(source)
        if not entry.get('hidden'):
            suffix=f'({entry["menuGroup"]}/{height})' if entry.get('menuGroup') else f'({height})'
            if not entry['label'].endswith(suffix):entry['label']=re.sub(r'\s*\((?:[^()]+/)?(?:Full|Half)\)$','',entry['label'])+' '+suffix
        key=('gui.vandorlabs.console.input.' if half else 'tile.vandorlabs.')+source+('' if half else '.name');names[key]=entry['label']
    assert [e['id'] for e in result]==[e['id'] for e in catalog]
    for entry in definitions:
        if entry['id'] in retired:entry['retired']=True
        elif entry['id'] in active:entry.pop('retired',None)
    lines=[line.split('=',1)[0]+'='+names[line.split('=',1)[0]] if line.split('=',1)[0] in names else line for line in language.splitlines()]
    existing={line.split('=',1)[0] for line in language.splitlines() if '=' in line};lines.extend(key+'='+value for key,value in sorted(names.items()) if key not in existing)
    return result,definitions,'\n'.join(lines)+'\n'

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    paths=[DATA/'data/unified_textures.json',DATA/'data/blocks.json',DATA/'lang/en_us.lang'];before=[p.read_text() for p in paths]
    catalog,blocks,language=restructure(json.loads(before[0]),json.loads(before[1]),before[2]);after=[json.dumps(catalog,indent=2)+'\n',json.dumps(blocks,indent=2)+'\n',language]
    if args.check:assert before==after,'Screen/control taxonomy requires regeneration'
    else:
        for path,text in zip(paths,after):path.write_text(text)
    visible=[e for e in catalog if e.get('category')=='Screens' and not e.get('hidden')]
    print('Screen taxonomy:',{g:sum(e.get('menuGroup')==g for e in visible) for g in ('Gold','Cool','Classic','Ship')},'; saved indices and separate input controls preserved')
if __name__=='__main__':main()
