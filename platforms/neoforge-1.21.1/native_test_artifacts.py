"""Native launcher lifetime boundary for generated runtimes.

Cleanup is after the entire launcher, including all restart/client phases. Set
VANDOR_LABS_TEST_KEEP_ARTIFACTS=1 to retain an owned run and its payload explicitly.
"""
import contextvars
import functools
from pathlib import Path
import sys
import tempfile
import traceback

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from test_artifacts import ArtifactRun

# Only generated runtime roots used by the native launchers are disposable.
# Top-level logs, exact-JAR hashes, exports and reports are never registered.
RUNTIMES = ('saved-world', 'server', 'client-server', 'matrix-server', 'client',
            'installed-server', 'missing-server', 'restored-server',
            'installed-client', 'missing-client')
_CURRENT = contextvars.ContextVar('vandorlabs_native_artifact_run', default=None)


class _Context:
    def __init__(self):
        self.runs = []

    def finish(self, success):
        errors = []
        for directory, run, processes in self.runs:
            try:
                active = tuple(process for process in processes if process.poll() is None)
                summary = run.finish(success, active_processes=active)
                if summary is not None:
                    print('Native runtime retention: ' + summary['status'] + ' (' + str(directory) + ')', flush=True)
            except Exception as error:
                errors.append(error)
                print('Native artifact finalization failed: ' + str(directory), file=sys.stderr)
                traceback.print_exception(type(error), error, error.__traceback__)
        if errors and success:
            raise errors[0]


def retain_artifacts(function):
    """One outer lifetime: nested launcher calls cannot prune a parent's restart data."""
    @functools.wraps(function)
    def wrapped(*args, **kwargs):
        if _CURRENT.get() is not None:
            return function(*args, **kwargs)
        context = _Context()
        token = _CURRENT.set(context)
        success = False
        try:
            result = function(*args, **kwargs)
            success = True
            return result
        finally:
            try:
                context.finish(success)
            finally:
                _CURRENT.reset(token)
    return wrapped


def artifact_directory(*, prefix, dir):
    """Allocate and mark a fresh owned output before any test payload is written."""
    context = _CURRENT.get()
    if context is None:
        raise RuntimeError('Native artifact allocation requires @retain_artifacts')
    directory = Path(tempfile.mkdtemp(prefix=prefix, dir=dir))
    run = ArtifactRun(directory, disposable_paths=RUNTIMES)
    context.runs.append((directory, run, []))
    return str(directory)


def track_process(process, log):
    """Track only children launched for an explicitly owned generated artifact root."""
    context = _CURRENT.get()
    if context is not None:
        path = Path(log).absolute()
        for directory, run, processes in context.runs:
            try:
                path.relative_to(directory.absolute())
            except ValueError:
                continue
            run.track_process(process)
            processes.append(process)
            break
    return process
