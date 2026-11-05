# Test artifact retention

The legacy viewscreen harness and native focused launchers automatically reclaim their generated game/server payloads after the **whole suite** completes. Restart phases and nested checks keep their shared worlds until the outer launcher finishes.

Set `VANDOR_LABS_TEST_KEEP_ARTIFACTS=1` before launching to keep full runtime directories. This opt-out is recorded in the run metadata and survives later cleanup sweeps. Use it when a test world must be reopened manually.

Successful runs discard disposable worlds, copied mods, resource packs and caches. Compact logs, crash reports, screenshots and assertion reports remain at their original paths; the remaining runtime directories are evidence-only, not resumable worlds. World headers such as `level.dat` are archived under `evidence/`, rather than left as apparent saved worlds. Top-level logs, artifact hashes, screenshots and reference exports are not deleted or moved.

Failed runtime payloads are bounded to the newest two completed failures per output parent, at most 256 MiB each and 512 MiB combined. Oversized or older failed payloads are removed after diagnostic extraction. The diagnostic archive has a 64 MiB/256-file budget; oversized text logs contribute a labelled 4 MiB tail. Original compact diagnostic paths remain complete for 24 hours so external analyzers can finish. On later cleanup sweeps after that grace period, helper-owned diagnostics are reduced to their bounded archive; the newest 20 older compact evidence sets remain. Explicitly kept runs are exempt. Unregistered paths and later edits to retained diagnostic files are preserved.

Cleanup requires an ownership marker created in a fresh empty directory and an explicit completion marker. Unmarked directories, unfinished runs, active phase leases and surviving tracked processes/groups are preserved. A killed launcher is never assumed complete merely because its parent PID disappeared. Symlink and Windows junction/reparse-point escapes are refused; custom game directories outside the run's registered disposable paths are never cleaned.

Completed cleanup records `retention.json` with reclaimed bytes and status. A cleanup error preserves diagnostics and is reported separately from the gameplay test result. The helper does not migrate or delete historical unmarked output directories.

Safety checks run without Minecraft:

```sh
python3 tools/test_test_artifacts.py
bash -n testclient/test_viewscreen.sh
```
