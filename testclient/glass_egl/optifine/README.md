# Actual OptiFine G5 binary probe

`../run_optifine.py` runs the official patched Minecraft/OptiFine classes in the
same real EGL compatibility context used by the other glass checks. The probe
does not load the test shader facade. Supply the OptiFine binary locally; no
OptiFine or Minecraft JAR belongs in the patch or repository.

The probe checks the actual 56-byte `SVertexFormat`, real `SVertexBuilder`
normal/midpoint-UV/tangent/entity generation, distinct block IDs and metadata,
and the production retained merge. Its negative control demonstrates that G5's
ordinary `addVertexData` rewrites entity IDs while the retained merge preserves
the original records. The production `BlockMeshVbo` then calls the actual G5
`ShadersRender` hooks and draws a small GLSL program that makes these attributes
visible in framebuffer pixels. Assertions check program, framebuffer, blend,
alpha threshold, depth mask and attribute cleanup in main and shadow-selected
states.

This is an isolated binary and GL compatibility check. It does not start a full
Forge world, load a shader pack, or establish hardware framerate. The probe uses
an EGL pbuffer and initializes OpenGlHelper's core capability fields for that
context because OptiFine's window initialization requires an X display. The
OptiFine writer, vertex buffer, format, hooks and GL methods are unmodified.

Run from the repository with Java 8, Linux EGL, gcc, LWJGL natives, current
compiled mod classes, the exported runtime classpath, and a privately prepared
G5 client whose classes **and members** use this project's MCP mappings:

```sh
python testclient/glass_egl/run_optifine.py \
  --java-home /path/to/java8 \
  --classpath-file build/glass-egl/runtime-classpath.txt \
  --natives /path/to/lwjgl-natives \
  --dev-client /path/to/optifine-dev-client.jar
```

Alternatively, supply `--patched-client` instead of `--dev-client`, plus
`--mapping-file` (obfuscated-to-SRG `.srg` with MCP class names), `--mcp-names`
(the project's MCP names ZIP), and `--special-source` (the local shaded
SpecialSource JAR). The runner translates SRG member names to MCP names before
remapping; a JAR with named classes but `func_`/`field_` members is insufficient.
`--patched-client` is the output of the official OptiFine patcher, not the
standalone OptiFine installer.

The runner verifies that every tested OptiFine class comes from the supplied
client JAR, checks the exact G5 version, records its mapped JAR hash, and writes
`build/test-artifacts/glass-performance/actual-optifine-g5.log`. Mesa software
rendering is forced by default; `--hardware` leaves renderer selection to the
environment, and the actual renderer is always printed.
