#!/usr/bin/env python3
"""Focused Java 8/21 prepared gate join contract."""
from pathlib import Path
import subprocess,tempfile
from check_portable_boundary import ROOT,check_boundary,jdk_home,executable,lint_flags
source=ROOT/'src/portableTest/java/com/vandorlabs/segmented/SegmentedGateJoinTest.java'
outputs=[]
for version in (8,21):
 home=jdk_home(version)
 with tempfile.TemporaryDirectory(prefix='vandor-segmented-tile-') as temp:
  target=Path(temp)/'classes';check_boundary(home,target)
  subprocess.run([str(executable(home,'javac')),'-source','8','-target','8',lint_flags(home),'-Werror','-classpath',str(target),'-d',str(target),str(source)],check=True,cwd=ROOT)
  result=subprocess.check_output([str(executable(home,'java')),'-ea','-cp',str(target),'com.vandorlabs.segmented.SegmentedGateJoinTest'],text=True,cwd=ROOT)
  print('Java'+str(version)+' '+result.strip(),flush=True);outputs.append(result)
if outputs[0]!=outputs[1]:raise RuntimeError('Java8/21 gate joins differ')
