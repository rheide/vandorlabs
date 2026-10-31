"""Generate visual control comparisons from the same visible catalog as the browser."""
import html,re
from pathlib import Path

COLUMNS=('Classic','Ashiro Mai','Paladyne','Pinnacle','Seyr','Vandor','Vektor','Root Controls')

def generate_control_matrix(entries):
    cells={};counts={column:0 for column in COLUMNS}
    for entry in entries:
        category=entry['category']
        if category=='Controls':column='Root Controls'
        elif category.startswith('Controls/'):column=category.split('/',1)[1]
        else:continue
        if column not in counts:raise ValueError('Unknown control folder: '+column)
        name=re.sub(r'^(Console Controls|Input Surfaces|Sliders Buttons) ','',entry['label'])
        key=(name,column)
        if key in cells:raise ValueError('Duplicate control name in folder: '+str(key))
        cells[key]=entry;counts[column]+=1
    rows=sorted({name for name,column in cells},key=str.casefold)
    def esc(value):return html.escape(value,quote=True)
    headings=['Control type']+[column+' ('+str(counts[column])+')' for column in COLUMNS]
    md=['# Control texture matrix','', '[Open the visual comparison page](control-matrix.html) · [Company control guide](company-controls.md)','',
        'Columns are texture folders; rows group matching control names. Every cell shows the first frame. A dash means that folder has no texture with that name. Root family prefixes are omitted from row names; the original menu label is available on each preview. Similar layouts retain their own artwork.','',
        '| '+' | '.join(headings)+' |','|'+'|'.join('---' for _ in headings)+'|']
    rendered=[]
    for name in rows:
        parts=['<tr data-search="'+esc(name+' '+' '.join(cells[(name,c)]['label'] for c in COLUMNS if (name,c) in cells)).casefold()+'"><th scope="row">'+esc(name)+'</th>'];markdown=[name]
        for column in COLUMNS:
            entry=cells.get((name,column))
            if entry:
                path='texture-browser/'+entry['thumbnail'];label=entry['label']+' — '+column
                parts.append('<td><a href="'+esc(path)+'" title="'+esc(label)+'"><img width="128" height="64" src="'+esc(path)+'" alt="'+esc(label)+'"></a></td>')
                markdown.append('[!['+label+']('+path+')]('+path+')')
            else:parts.append('<td class="missing" aria-label="Unavailable">—</td>');markdown.append('—')
        rendered.append(''.join(parts)+'</tr>');md.append('| '+' | '.join(markdown)+' |')
    template=(Path(__file__).parent/'control_matrix.html').read_text()
    page=template.replace('<!--HEADERS-->',''.join('<th scope="col">'+esc(h)+'</th>' for h in headings)).replace('<!--ROWS-->','\n'.join(rendered)).replace('<!--SUMMARY-->',str(len(cells))+' textures · '+str(len(rows))+' control types · '+str(len(COLUMNS))+' folders')
    return {'../control-matrix.html':page.encode(),'../control-matrix.md':('\n'.join(md)+'\n').encode()}
