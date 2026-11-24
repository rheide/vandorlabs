#!/usr/bin/env python3
"""Run two isolated Forge clients against a loopback integrated server."""
import argparse
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import tempfile
import time


ROOT = Path(__file__).resolve().parents[1]


def start(directory, peer, port, mode):
    directory.mkdir()
    env = os.environ.copy()
    env.pop("VANDOR_LABS_TEST_GAME_DIR", None)
    env.pop("VANDOR_LABS_CABIN_PEER_PORT", None)
    env["VANDOR_LABS_TEST_RUN_ROOT"] = str(directory)
    env["VANDOR_LABS_TEST_SKIP_BUILD"] = "true"
    env["VANDOR_LABS_REENTRY_MODE"] = mode
    env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") +
                                " -Dvandorlabs.cabinCrewChecks=true -Dvandorlabs.cabinWalkChecks=true" +
                                " -Dvandorlabs.cabinCrewPort=" + str(port))
    if peer:
        env["JAVA_TOOL_OPTIONS"] += " -Dvandorlabs.cabinCrewPeer=true"
        env["VANDOR_LABS_CABIN_PEER_PORT"] = str(port)
    if os.name == "nt":
        command = [sys.executable, "testclient/run_windows.py", "--focus", "cruise-physical"]
        if peer:
            command.extend(["--server", "127.0.0.1:" + str(port)])
        if env.get("VANDOR_LABS_COMPAT_MODS"):
            command.extend(["--compat-mods", env["VANDOR_LABS_COMPAT_MODS"]])
        if env.get("VANDOR_LABS_TEST_JAR"):
            command.extend(["--jar", env["VANDOR_LABS_TEST_JAR"]])
    else:
        command = ["bash", "testclient/test_viewscreen.sh", "--focus", "cruise-physical"]
    with (directory / "launcher.log").open("w") as log:
        return subprocess.Popen(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT,
                                start_new_session=os.name != "nt")


def stop(process):
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(process.pid)], check=False)
    else:
        os.killpg(process.pid, signal.SIGTERM)
    process.wait(timeout=15)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=["anchored", "physical"], default="physical")
    args = parser.parse_args()
    base = ROOT / "build/tests/cabin-crew"
    base.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix=args.mode + "-", dir=str(base)))
    print("Crew test output:", directory, flush=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    processes = []
    try:
        host = start(directory / "host", False, port, args.mode)
        processes.append(host)
        deadline = time.monotonic() + 300
        offset = 0
        while time.monotonic() < deadline and host.poll() is None:
            logs = list((directory / "host").glob("render-run.*/client.log"))
            if logs:
                with logs[0].open(errors="replace") as log:
                    log.seek(offset)
                    if "cabin crew READY" in log.read():
                        break
                    offset = max(0, log.tell() - 64)
            time.sleep(.5)
        else:
            raise RuntimeError("Host did not open its loopback world. See " + str(directory))
        processes.append(start(directory / "peer", True, port, args.mode))
        deadline = time.monotonic() + 480
        while any(p.poll() is None for p in processes):
            if any(p.returncode not in (0, None) for p in processes) or time.monotonic() > deadline:
                raise RuntimeError("Crew test failed. See " + str(directory))
            time.sleep(1)
        if any(p.returncode for p in processes):
            raise RuntimeError("Crew test failed. See " + str(directory))
        print("Two connected clients PASS", args.mode)
    finally:
        for process in reversed(processes):
            stop(process)


if __name__ == "__main__":
    main()
