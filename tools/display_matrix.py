"""Compare first frames across Classic, Cool and Gold, keeping alternatives visible."""
import html,re
from texture_matrix import render_page
STYLES=('Classic','Cool','Gold')

def generate_display_matrix(entries):
    cells={};counts={style:0 for style in STYLES};subjects=set()
    for entry in entries:
        if entry['category'] not in {'Displays/'+style for style in STYLES}:continue
        style=entry['category'].split('/')[1];match=re.fullmatch(r'(.+) \('+style+r'/(Full|Half)\)( Large)?',entry['label'])
        if not match:raise ValueError('Display label missing style/height: '+entry['label'])
        subject,height,large=match.groups();variant='Large ('+str(entry['pixelWidth'])+'px)' if large else 'Original'
        if style in ('Cool','Gold') and subject.endswith(' Bridge'):subject=subject.removesuffix(' Bridge');variant='Bridge'
        key=(subject,height,style);cell=cells.setdefault(key,[])
        if any(v==variant for v,e in cell):raise ValueError('Duplicate display variant: '+str(key))
        cell.append((variant,entry));subjects.add(subject);counts[style]+=1
    headings=['Display / height']+[style+' ('+str(counts[style])+')' for style in STYLES]
    md=['# Display texture matrix','', '[Open the visual comparison page](display-matrix.html) · [Texture browser](texture-browser/index.html)','',
        'First frames of Classic, Cool and Gold displays, with Full and Half layouts on separate rows. A dash means that style has no matching design.','',
        '| '+' | '.join(headings)+' |','|'+'|'.join('---' for h in headings)+'|']
    rendered=[]
    for subject in sorted(subjects,key=str.casefold):
        for height in ('Full','Half'):
            label=subject+' ('+height+')';search=label+' '+' '.join(entry['label'] for style in STYLES for variant,entry in cells.get((subject,height,style),[]));parts=['<tr data-search="'+html.escape(search.casefold(),quote=True)+'"><th scope="row">'+html.escape(label)+'</th>'];markdown=[label]
            for style in STYLES:
                variants=sorted(cells.get((subject,height,style),[]),key=lambda pair:pair[0]!='Original')
                if not variants:parts.append('<td class="missing" aria-label="Unavailable">—</td>');markdown.append('—');continue
                parts.append('<td><div class="variants">');previews=[]
                for variant,entry in variants:
                    path='texture-browser/'+entry['thumbnail'];alt=entry['label'];caption=variant if len(variants)>1 else ''
                    parts.append('<figure><a href="'+html.escape(path,quote=True)+'" title="'+html.escape(alt,quote=True)+'"><img width="128" height="'+str(128 if height=='Full' else 64)+'" src="'+html.escape(path,quote=True)+'" alt="'+html.escape(alt,quote=True)+'"></a>'+('<figcaption>'+caption+'</figcaption>' if caption else '')+'</figure>')
                    previews.append((caption+': ' if caption else '')+'[!['+alt+']('+path+')]('+path+')')
                parts.append('</div></td>');markdown.append('<br>'.join(previews))
            rendered.append(''.join(parts)+'</tr>');md.append('| '+' | '.join(markdown)+' |')
    page=render_page(title='Display texture matrix',summary=str(sum(counts.values()))+' textures · '+str(len(subjects))+' subjects · Full and Half · three styles',
        headers=''.join('<th scope="col">'+html.escape(h)+'</th>' for h in headings),rows='\n'.join(rendered),
        nav='<a href="texture-browser/index.html">Texture browser</a> · <a href="control-matrix.html">Control matrix</a> · <a href="display-matrix.md">Markdown matrix</a>',
        intro='Compare Classic, Cool and Gold artwork for each subject. Full and Half layouts have separate rows. Large choices are marked with their native width. Every preview shows the first animation frame. A dash means that style has no matching design. Select a texture to open its PNG.',
        filter_label='Find a display',placeholder='Biology, Station, Full, Half…',table_label='Scrollable display style comparison',unit='display layouts',
        footer='Standard Full artwork is 128 × 128 pixels; Half artwork is 128 × 64 pixels. Large choices use the captioned native width and retain the same aspect ratio. Previews are scaled for comparison. Headers stay visible while scrolling.')
    return {'../display-matrix.html':page,'../display-matrix.md':('\n'.join(md)+'\n').encode()}
