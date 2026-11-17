"""Verify that ModDev's production inputs match the frozen standard packaged artifact."""
import hashlib,json,re,zipfile
from pathlib import Path
PLATFORM=Path(__file__).resolve().parent
ROOT=PLATFORM.parents[1]
def snapshot(out,stage,expected=None,require_source_unchanged=True):
 version=re.search(r"(?m)^version\s*=\s*'([^']+)'",(ROOT/'build.gradle').read_text()).group(1)
 jar=PLATFORM/'build/libs'/('vandorlabs-neoforge-1.21.1-'+version+'.jar')
 digest=hashlib.sha256(jar.read_bytes()).hexdigest();production=hashlib.sha256();count=0
 with zipfile.ZipFile(jar) as archive:
  for base in (PLATFORM/'build/classes/java/main',PLATFORM/'build/resources/main'):
   for path in sorted(base.rglob('*')):
    if not path.is_file():continue
    name=path.relative_to(base).as_posix();data=path.read_bytes()
    try:packaged=archive.read(name)
    except KeyError:raise RuntimeError('Runtime production input absent from standard JAR: '+name)
    if data!=packaged:raise RuntimeError('Runtime production input differs from standard JAR: '+name)
    production.update(name.encode()+b'\0'+data);count+=1
 source=hashlib.sha256()
 for base in (PLATFORM/'src/main/java',ROOT/'src/portable/java'):
  for path in sorted(base.rglob('*.java')):source.update(str(path.relative_to(ROOT)).encode()+b'\0'+path.read_bytes())
 result={'schema':1,'artifact':jar.name,'artifact_sha256':digest,'runtime_input_sha256':production.hexdigest(),'runtime_input_count':count,'source_sha256':source.hexdigest(),'mode':'ModDev main inputs byte-identical to standard JAR; separate smoke fixture classes'}
 if expected is not None:
  keys=('artifact','artifact_sha256','runtime_input_sha256','runtime_input_count')+(('source_sha256',) if require_source_unchanged else ())
  if any(result[k]!=expected[k] for k in keys):raise RuntimeError('Frozen navigation artifact/runtime inputs changed between phases')
  if not require_source_unchanged:result['working_source_changed_during_run']=result['source_sha256']!=expected['source_sha256']
 out=Path(out);out.mkdir(parents=True,exist_ok=True);(out/('candidate-'+stage+'.json')).write_text(json.dumps(result,indent=2)+'\n')
 return result
