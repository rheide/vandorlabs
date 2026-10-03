# Animated documentation demonstration

Animated GIFs work with the documentation's existing Markdown image links.
The example uses the already documented **Observation Programmable Door**,
framed, rotating, with the existing medium-detail artwork. It shows one real
server-driven open/close cycle captured from Minecraft's live renderer.

![Observation door opening and closing](images/gallery/doors/observation-rotating.gif)

The capture helper is enabled only by `vandorlabs.documentationDoorGif` in
ReproLab. It uses the existing renderer and animation clock, changes the
server's normal OPEN blockstate, waits for client synchronization, and records
real frame times. No door behavior or animation speed changes are required.
A fixed camera, noon lighting, hidden HUD and cleared gallery backdrop keep
the example readable. The supplied demo uses the software-rendered test
client; it is documentation footage, with no GPU performance or shader claim.

The encoder crops screenshots to 600×500, uses a shared 256-color palette,
keeps recorded timing at GIF's 10ms resolution, and loops continuously. GIF
works in Markdown viewers that support animated images, including GitHub;
PDF/print exports may show only a still frame. Its palette cannot preserve all
24-bit screenshot colors. Short, cropped clips keep documentation downloads
small; longer demonstrations would be better served by video.

## Reproduce

With the existing testclient runtime installed:

```bash
bash testclient/capture_door_gif.sh
```

This builds only the standard mod JAR in `build/libs/`, installs it into the
disposable testclient runtime, captures PNG frames and `door-frames.tsv`, then
validates and writes `docs/images/gallery/doors/observation-rotating.gif`.
The source directory and encoded frame count, loop duration and byte size
are printed when the run finishes. Raw frames and the review contact sheet
stay in the ignored `testclient/door-animation.*` directory.

To re-encode an existing capture without restarting Minecraft:

```bash
python3 testclient/encode_door_gif.py testclient/door-animation.RUN
```

Validation requires synchronized closed/open/closed states, intermediate
opening and closing geometry, an animated file, a continuous loop, and the
expected dimensions and duration. The contact sheet supports visual review
of both transitions and the complete motion footprint.

## Demonstration result

Capture: `testclient/door-animation.beMkA1` (36 real PNG frames).
Validated output: **20 GIF frames, 3.4-second loop, 600×500, 282,513 bytes**
(about 276 KiB). Both transitions and the final closed state were verified
from the geometry, and decoded GIF frames were visually reviewed. The source
capture used the Java 8 standard JAR build.
