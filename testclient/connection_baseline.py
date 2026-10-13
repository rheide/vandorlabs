#!/usr/bin/env python3
"""Create immutable accepted connection baselines, or compare a later capture."""
import argparse,csv,hashlib,html,json,statistics,sys,math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
try:
    from PIL import Image,ImageFilter
except ImportError:
    sys.path.insert(0,str(ROOT/'build/tooling/python-packages'))
    from PIL import Image,ImageFilter
SCHEMA='vandorlabs-connection-baseline-v1'
DEFAULT=ROOT/'testclient/baselines/regular-wall-connections'
ACTIVE=ROOT/'testclient/baselines/active.json'
CAMERA=['posX','posY','posZ','camX','camY','camZ','yaw','pitch']
ROI=(480,216,1440,918)
SETTINGS={'roi':list(ROI),'sample_scale':2,'mask_dilation_pixels':2,'channel_tolerance':12,'max_changed_fraction':.003,'max_mean_error':2.0,'max_lighting_bias':6}
GRASS_LIGHTING={'min_samples':1000,'min_channel_std':3,'min_r_squared':.985,'max_mean_residual':1.5,'max_p95_residual':3,'min_gain':.65,'max_gain':1.55,'max_offset':6}

def default_baseline(topology='l',config=ACTIVE):
    if topology not in ('l','t','x'):raise ValueError('Unknown connection topology')
    fallback='regular-wall-connections'+('' if topology=='l' else '-'+topology)
    if not config.is_file():return config.parent/fallback
    active=json.loads(config.read_text(encoding='utf-8'))
    if active.get('schema')!='vandorlabs-active-connection-baselines-v1':raise ValueError('Unknown active baseline schema')
    name=active.get('baselines',{}).get(topology)
    if name is None:return config.parent/fallback
    if not isinstance(name,str) or Path(name).name!=name or name in ('.','..'):raise ValueError('Active baseline must name one sibling directory')
    selected=config.parent/name
    if not (selected/'baseline.json').is_file():raise ValueError('Active accepted baseline is missing: '+name)
    return selected

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def read_manifest(folder):
    with (folder/'manifest.tsv').open(encoding='utf-8-sig',newline='') as stream:
        reader=csv.DictReader(stream,delimiter='\t')
        if reader.fieldnames!=['name']+CAMERA:raise ValueError('Capture manifest camera columns differ')
        result={}
        for row in reader:
            name=row.pop('name')
            if name in result:raise ValueError('Duplicate capture case '+name)
            result[name]={key:float(row[key]) for key in CAMERA}
            if not all(math.isfinite(value) for value in result[name].values()):raise ValueError('Nonfinite capture camera value')
        return result

def create(feedback,capture,baseline):
    if baseline.exists():raise ValueError('Baseline already exists; original accepted images will not be overwritten')
    review=json.loads(feedback.read_text(encoding='utf-8'))
    if review.get('schema')!='vandorlabs-connection-review-v1':raise ValueError('Unknown review schema')
    if review.get('captureId')!=capture.name:raise ValueError('Feedback captureId does not match capture folder')
    catalog=json.loads((capture/'connection-review.json').read_text(encoding='utf-8'))['cases']
    cameras=read_manifest(capture);rows=[];excluded={}
    known={row['file'] for row in catalog}
    if len(known)!=len(catalog) or len({row['name'] for row in catalog})!=len(catalog):raise ValueError('Duplicate review case or image')
    if set(review.get('reviews',{}))-known:raise ValueError('Feedback references unknown screenshot files')
    displayed=review.get('displayedFiles')
    if displayed is not None:
        if not isinstance(displayed,list) or not all(isinstance(file,str) for file in displayed):raise ValueError('displayedFiles must list screenshot filenames')
        if len(set(displayed))!=len(displayed) or set(displayed)-known:raise ValueError('displayedFiles contains duplicate or unknown screenshots')
        displayed=set(displayed)
    for row in catalog:
        file=row['file']
        status=review.get('reviews',{}).get(file,{}).get('status',review.get('defaultStatus','unreviewed')) if displayed is None or file in displayed else 'unreviewed'
        if status!='ok':excluded[status]=excluded.get(status,0)+1;continue
        path=capture/file
        if Path(file).name!=file or not path.is_file():raise ValueError('Accepted source image missing or unsafe: '+file)
        name=row['name']
        if name not in cameras:raise ValueError('Accepted case absent from camera manifest: '+name)
        with Image.open(path) as image:
            if image.format!='PNG':raise ValueError('Accepted source is not PNG')
            image.verify()
        with Image.open(path) as image:dimensions=list(image.size)
        if dimensions!=[1920,1080]:raise ValueError('Baseline requires 1920x1080 capture')
        rows.append(dict(name=name,file=file,sha256=sha(path),dimensions=dimensions,camera=cameras[name],title=row['title'],roles=row['roles']))
    if not rows:raise ValueError('No explicitly accepted or default-ok cases to preserve')
    baseline.mkdir(parents=True)
    try:
        for row in rows:(baseline/row['file']).write_bytes((capture/row['file']).read_bytes())
        manifest=dict(schema=SCHEMA,source_capture_id=review['captureId'],acceptance='Reviewer status ok only; defaultStatus honored when explicitly present',comparison=SETTINGS,cases=rows,excluded_status_counts=excluded)
        (baseline/'baseline.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    except BaseException:
        # Keep any partial directory as evidence; never silently overwrite it on retry.
        raise
    print(f'Preserved {len(rows)} accepted originals; excluded={excluded}; baseline={baseline}')

def pixels(image):
    return image.get_flattened_data() if hasattr(image,"get_flattened_data") else image.getdata()

def foreground(pixel):
    r,g,b=pixel
    return b>=r+6 and b>=20 or max(pixel)-min(pixel)<=12 and 20<=max(pixel)<=230

def grass(pixel):
    r,g,b=pixel
    return g>r+10 and g>b+10

def grass_lighting(ap,bp):
    """Estimate illumination only from corresponding green terrain, never wall colors."""
    pairs=[(x,y) for x,y in zip(ap[::7],bp[::7]) if grass(x) and grass(y)]
    policy=GRASS_LIGHTING
    if len(pairs)<policy['min_samples']:return None
    channels=[]
    for c in range(3):
        mx=statistics.mean(x[c] for x,y in pairs);my=statistics.mean(y[c] for x,y in pairs)
        vx=sum((x[c]-mx)**2 for x,y in pairs);vy=sum((y[c]-my)**2 for x,y in pairs)
        if min(vx,vy)/len(pairs)<policy['min_channel_std']**2:return None
        covariance=sum((x[c]-mx)*(y[c]-my) for x,y in pairs)
        gain=covariance/vx;offset=my-gain*mx;r_squared=covariance**2/(vx*vy)
        residuals=sorted(abs(y[c]-gain*x[c]-offset) for x,y in pairs)
        mean=sum(residuals)/len(residuals);p95=residuals[int(.95*len(residuals))]
        if not (policy['min_gain']<=gain<=policy['max_gain'] and abs(offset)<=policy['max_offset']
                and r_squared>=policy['min_r_squared'] and mean<=policy['max_mean_residual'] and p95<=policy['max_p95_residual']):return None
        channels.append(dict(gain=gain,offset=offset,r_squared=r_squared,mean_residual=mean,p95_residual=p95))
    if not any(abs(c['gain']-1)>.03 or abs(c['offset'])>2 for c in channels):return None
    return dict(samples=len(pairs),channels=channels)

def metrics(before,after,settings=SETTINGS):
    if before.size!=after.size or before.size!=(1920,1080):raise ValueError('Screenshot dimensions differ or are not 1920x1080')
    box=tuple(settings['roi']);size=((box[2]-box[0])//2,(box[3]-box[1])//2)
    a=before.convert('RGB').crop(box).resize(size,Image.Resampling.BOX)
    b=after.convert('RGB').crop(box).resize(size,Image.Resampling.BOX)
    ap=list(pixels(a));bp=list(pixels(b))
    normalization=grass_lighting(ap,bp)
    predicted=[tuple(max(0,min(255,pixel[c]*channel['gain']+channel['offset'])) for c,channel in enumerate(normalization['channels'])) for pixel in ap] if normalization else ap
    core=[foreground(x) or foreground(y) for x,y in zip(ap,bp)]
    mask=Image.new('L',size);mask.putdata([255 if value else 0 for value in core])
    # Union sees added geometry beyond the old silhouette; dilation includes edge pixels.
    mask=mask.filter(ImageFilter.MaxFilter(2*settings['mask_dilation_pixels']+1))
    selected=[bool(m) and not (grass(x) and grass(y)) for m,x,y in zip(pixels(mask),ap,bp)]
    lighting=[sum(y)/3-sum(expected)/3 for use,x,y,expected in zip(selected,ap,bp,predicted) if use and foreground(x) and foreground(y)]
    bias=max(-settings['max_lighting_bias'],min(settings['max_lighting_bias'],statistics.median(lighting))) if lighting else 0
    errors=[max(abs(y[c]-x[c]-bias) for c in range(3)) for use,x,y in zip(selected,predicted,bp) if use]
    if not errors:raise ValueError('No junction foreground found in baseline/current union')
    changed=sum(error>settings['channel_tolerance'] for error in errors)
    mean=sum(errors)/len(errors);fraction=changed/len(errors)
    result=dict(mask_pixels=len(errors),changed_pixels=changed,changed_fraction=fraction,mean_error=mean,lighting_bias=bias,grass_lighting=normalization)
    result['passed']=fraction<=settings['max_changed_fraction'] and mean<=settings['max_mean_error']
    # Diagnostic heat map includes only evaluated junction pixels, never alters originals.
    heat=Image.new('RGB',size)
    heat.putdata([(min(255,int(max(abs(y[c]-x[c]-bias) for c in range(3))*5)),0,0) if use else (12,18,24) for use,x,y in zip(selected,predicted,bp)])
    return result,a,b,heat

def compare(baseline,capture,output):
    data=json.loads((baseline/'baseline.json').read_text(encoding='utf-8'))
    if data.get('schema')!=SCHEMA:raise ValueError('Unknown baseline schema')
    if not data.get('cases'):raise ValueError('Baseline contains no accepted cases')
    output.mkdir(parents=True,exist_ok=True);cameras=read_manifest(capture);results=[];cards=[]
    for index,row in enumerate(data['cases']):
        result=dict(name=row['name'],file=row['file'],title=row['title'],passed=False)
        try:
            old=baseline/row['file'];new=capture/row['file']
            if sha(old)!=row['sha256']:raise ValueError('Accepted baseline original SHA256 differs')
            if row['name'] not in cameras:raise ValueError('Accepted case missing from new capture manifest')
            if any(abs(cameras[row['name']][key]-row['camera'][key])>1e-6 for key in CAMERA):raise ValueError('Capture camera/fixture position differs from accepted baseline')
            if not new.is_file():raise ValueError('Accepted case screenshot missing from new capture')
            with Image.open(old) as before,Image.open(new) as after:measurement,a,b,diff=metrics(before,after,data['comparison'])
            result.update(measurement)
            for label,image in [('accepted',a),('current',b),('difference',diff)]:image.save(output/f'{index:03d}_{label}.png')
            cards.append(f'<section><h2>{html.escape(row["title"])}</h2><p>{html.escape(row["name"])}: {"PASS" if result["passed"] else "REVIEW REQUIRED"}; changed={result["changed_fraction"]:.3%}, mean={result["mean_error"]:.3f}</p><div class="images">'+''.join(f'<figure><figcaption>{label}</figcaption><img src="{index:03d}_{label}.png"></figure>' for label in ['accepted','current','difference'])+'</div></section>')
        except (OSError,ValueError,KeyError) as error:
            result['error']=str(error);cards.append(f'<section><h2>{html.escape(row["name"])}</h2><p class="fail">{html.escape(str(error))}</p></section>')
        results.append(result)
    passed=all(row['passed'] for row in results)
    report=dict(schema='vandorlabs-connection-regression-v1',passed=passed,accepted_cases=len(results),passed_cases=sum(row['passed'] for row in results),comparison=data['comparison'],grass_lighting_policy=GRASS_LIGHTING,cases=results)
    (output/'comparison.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    (output/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Accepted connection regression</title><style>body{font:15px system-ui;background:#111923;color:#eee;margin:24px}.images{display:flex;gap:8px}figure{margin:0;flex:1;min-width:0}img{width:100%;image-rendering:auto}section{margin:24px 0}.fail{color:#ff9b93}</style><h1>Accepted connection regression: '+('PASS' if passed else 'REVIEW REQUIRED')+'</h1><p>Only previously accepted cases are checked. Colored differences need human review; no baseline is updated automatically.</p>'+''.join(cards),encoding='utf-8')
    print(f'Accepted connection regression: {sum(r["passed"] for r in results)}/{len(results)} PASS; report={output/"index.html"}')
    return 0 if passed else 2

def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='command',required=True)
    make=sub.add_parser('create',help='Preserve only reviewer-accepted originals in a NEW baseline directory')
    make.add_argument('--feedback',type=Path,required=True);make.add_argument('--capture',type=Path,required=True);make.add_argument('--baseline',type=Path,default=DEFAULT)
    check=sub.add_parser('compare',help='Compare accepted case IDs; exit2 requests review, without promoting images')
    check.add_argument('--capture',type=Path,required=True);check.add_argument('--baseline',type=Path);check.add_argument('--output',type=Path)
    args=parser.parse_args()
    if args.command=='create':create(args.feedback,args.capture,args.baseline);return 0
    return compare(args.baseline or default_baseline(),args.capture,args.output or ROOT/'build/connection-regression'/args.capture.name)
if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,KeyError) as error:raise SystemExit(str(error))
