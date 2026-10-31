"""Generate visual control comparisons from the same visible catalog as the browser."""
import html,re
from texture_matrix import render_page

COLUMNS=('Classic','Ashiro Mai','Paladyne','Pinnacle','Seyr','Vandor','Vektor')

def generate_control_matrix(entries):
    cells={};counts={column:0 for column in COLUMNS}
    for entry in entries:
        category=entry['category']
        if category.startswith('Controls/'):column=category.split('/',1)[1]
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
        'Columns are texture folders; rows group matching control names. Every cell shows the first frame. A dash means that folder has no texture with that name. Similar layouts retain their own artwork.','',
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
    page=render_page(title='Control texture matrix',summary=str(len(cells))+' textures · '+str(len(rows))+' control types · '+str(len(COLUMNS))+' folders',
        headers=''.join('<th scope="col">'+esc(h)+'</th>' for h in headings),rows='\n'.join(rendered),
        nav='<a href="texture-browser/index.html">Texture browser</a> · <a href="display-matrix.html">Display matrix</a> · <a href="company-controls.md">Control guide</a> · <a href="control-matrix.md">Markdown matrix</a>',
        intro='Classic and company folders are compared by control name. Each preview is a first frame. A dash means that design is unavailable in the folder. Select a texture to open its PNG.',
        filter_label='Find a control',placeholder='Keyboard, Fader Bank, Navigation…',table_label='Scrollable control texture comparison',unit='control types',
        footer='Shared names identify related layouts; textures retain their company-specific artwork. Scroll horizontally to compare every folder. Headers stay visible while scrolling.')
    return {'../control-matrix.html':page,'../control-matrix.md':('\n'.join(md)+'\n').encode()}
