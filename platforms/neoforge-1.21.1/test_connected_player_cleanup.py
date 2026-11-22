#!/usr/bin/env python3
"""Exercise failed connected-launcher cleanup with two Python processes and no game runtime."""
import json
import os
from pathlib import Path
import signal
import sys
import tempfile
import unittest
from unittest.mock import patch

import connected_player_fixture as fixture
from native_test_artifacts import retain_artifacts
from test_artifacts import FINISHED, LEASES, _alive, _processes, _signal_owned_process

CHILD = """
from pathlib import Path
import os, signal, sys, time

if os.name != 'nt':
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
Path(sys.argv[1]).write_text(str(os.getpid()), encoding='utf-8')
time.sleep(20)
"""

LEADER = """
from pathlib import Path
import os, subprocess, sys, time

path = Path(sys.argv[1])
options = (dict(creationflags=subprocess.CREATE_NEW_PROCESS_GROUP) if os.name == 'nt'
           else dict(start_new_session=True))
subprocess.Popen([sys.executable, '-c', sys.argv[2], str(path)], **options)
deadline = time.monotonic() + 4
while not path.exists() and time.monotonic() < deadline:
    time.sleep(.025)
print('Stopping server', flush=True)
# Give the real lease watcher a chance to observe the detached child before reparenting.
time.sleep(.8)
sys.exit(17)
"""


class ConnectedCleanupChecks(unittest.TestCase):
    @unittest.skipUnless(os.name == 'nt' or (hasattr(os, 'pidfd_open') and hasattr(signal, 'pidfd_send_signal')),
                         'safe process handles are unavailable')
    def test_failed_launcher_leaves_no_detached_fixture_process(self):
        with tempfile.TemporaryDirectory(prefix='connected process cleanup ') as directory:
            root = Path(directory)
            child_pid = root / 'child.pid'

            def command(task, *_args):
                self.assertEqual(task, 'runServer')
                return [sys.executable, '-c', LEADER, str(child_pid), CHILD]

            @retain_artifacts
            def connected():
                fixture.run_connected_player('synthetic', False, ())

            record = None
            try:
                with patch.object(fixture, 'ROOT', root), patch.object(fixture, 'command', command), patch.object(
                        fixture, 'jdk_home', return_value=root):
                    with self.assertRaisesRegex(RuntimeError, 'Native synthetic server startup'):
                        connected()
                run, = (root / 'build/test-artifacts').glob('synthetic-player-*')
                lease, = (run / LEASES).glob('*.json')
                owner = json.loads(lease.read_text(encoding='utf-8'))
                pid = int(child_pid.read_text(encoding='utf-8'))
                record, = [process for process in owner['processes'] if process['pid'] == pid]
                self.assertIsNotNone(record['birth'])
                self.assertEqual(len(owner['processes']), 2)
                self.assertFalse(_alive(record, _processes()))
                self.assertFalse(json.loads((run / FINISHED).read_text(encoding='utf-8'))['success'])
            finally:
                if record is not None:
                    _signal_owned_process(record, force=True)


if __name__ == '__main__':
    unittest.main()
