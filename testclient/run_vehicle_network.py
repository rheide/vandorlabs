#!/usr/bin/env python3
"""Isolated Java 8 dedicated server plus hidden/muted driver and observer clients.

Injects bounded application-message delay (0/50/100/200 ms), jitter and loss.
This is not an emulation of TCP packet loss or retransmission.
"""
from pathlib import Path
import os, shutil, subprocess, sys, time


def main():
    root=Path(__file__).resolve().parents[1]
    tooling=root/'build/tooling'
    output=root/'testclient'/('render-run.network-'+time.strftime('%Y%m%d-%H%M%S'))
    output.mkdir(parents=True)
    (output/'eula.txt').write_text('eula=true\n')
    (output/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25576\nlevel-type=FLAT\ngamemode=1\nview-distance=4\nspawn-protection=0\nallow-flight=true\nmax-tick-time=120000\n')
    (output/'log4j.xml').write_text('<Configuration><Appenders><Console name="Console" target="SYSTEM_OUT"><PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] %msg%n"/></Console></Appenders><Loggers><Root level="info"><AppenderRef ref="Console"/></Root></Loggers></Configuration>')
    cache=tooling/'gradle-user-home/caches/forge_gradle'
    forge=cache/'maven_downloader/net/minecraftforge/forge/1.12.2-14.23.5.2860/forge-1.12.2-14.23.5.2860-universal.jar'
    server=cache/'mcp_repo/de/oceanlabs/mcp/mcp_config/1.12.2/joined/downloadServer/server.jar'
    libraries=list((tooling/'minecraft/libraries').rglob('*.jar'))
    jars=[forge,server,root/'build/libs/vandorlabs-2.0.jar']+libraries
    java=tooling/'jdk8u504-b01/bin/java.exe'
    command=[str(java),'-Xmx2G','-Dvandorlabs.vehicleNetworkLab=true','-Dlog4j.configurationFile='+str(output/'log4j.xml'),'-cp',os.pathsep.join(map(str,jars)),'net.minecraftforge.fml.relauncher.ServerLaunchWrapper','nogui']
    print('Dedicated output: '+str(output),flush=True)
    clients=[]
    with (output/'server.log').open('w') as log:
        proc=subprocess.Popen(command,cwd=output,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            until=time.monotonic()+180
            while 'Done (' not in (output/'server.log').read_text(errors='replace'):
                if proc.poll() is not None:raise RuntimeError('Dedicated server failed: '+str(output/'server.log'))
                if time.monotonic()>until:raise TimeoutError('Dedicated startup timeout')
                time.sleep(.25)
            for role in ['driver','observer']:
                stream=(output/(role+'.log')).open('w')
                client=subprocess.Popen([sys.executable,str(root/'testclient/run_windows.py'),'--focus','vehicle-network','--role',role,'--server','127.0.0.1:25576','--timeout','600'],cwd=root,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
                clients.append((client,stream))
                time.sleep(2)
            until=time.monotonic()+600
            while any(client.poll() is None for client,_ in clients):
                if time.monotonic()>until:raise TimeoutError('Dedicated client timeout')
                time.sleep(.25)
            if any(client.returncode for client,_ in clients):raise RuntimeError('Dedicated client failed: '+str(output))
            print('PASS: dedicated driver and observer, four delay profiles, jitter and dropped application messages',flush=True)
        finally:
            for client,stream in clients:
                if client.poll() is None:client.terminate()
                stream.close()
            if proc.poll() is None:
                proc.stdin.write(b'stop\n');proc.stdin.flush()
                try:proc.wait(timeout=30)
                except subprocess.TimeoutExpired:proc.terminate();proc.wait(timeout=15)


if __name__=='__main__':main()
