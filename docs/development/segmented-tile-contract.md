# Segmented Door Tile mapping and body depth

The previous fast solid-cell emitter and the clipped window emitter disagree for nonsquare artwork. With the actual Pinnacle Light atlas entry (aspect 1:2), the solid path repeats one complete image per block vertically while the window path repeats half an image per block. A mixed framebuffer therefore shows twice the vertical repetition rate in solid columns. This is the placement-independent rendering defect addressed by the Tile correction.

The focused Source fixture renders both actual paths, captures the mixed result and compares front/back vertical UV density. Existing pre-change panel, eligible-fast, header, selection/collision and fluid corpora remain independently fingerprinted. The correction must retain header depth at four pixels while reducing section bodies from three to two pixels, and must keep the body renderer, collision, rays and fluid-plane geometry in agreement. Saved setting/control indices are unchanged.

## Shared implementation

Fast solid bodies and window frames now use the same aspect-preserving Tile transform. A normalized GPU-precision row/column phase reaches cached terrain quads through immutable render-only state. Repeating phases share terrain meshes; complete-door dimensions do not rescale Tile artwork. Square solid sections retain their 24-vertex fast mesh. Reveals use metric cropped UVs, including the two-pixel body edge.

The body is centered on its existing saved Center/Front/Back plane and is now two pixels deep. Narrowing adds half a pixel of clearance at each former face; the existing plane centers and four-pixel header placement are unchanged. Collision, moving selection rays and the contained-fluid plane use the same body-depth constant. The item model uses the same two-pixel depth and retains its authored GUI pose.

No registry ID, listed property, saved setting, control index or packet field is added. The settings scalar remains 15 bytes, with the existing 14-byte legacy decoder. UV phase exists only in Source unlisted render state and the native client terrain snapshot.

## Intentional reference changes

The pre-change Source corpora were frozen before changing the emitter. The current strict reference hashes establish the intentional two-pixel and Tile-mapping contract; header, opening-fill and water-outlet corpora remain byte-identical.

| Corpus | Previous SHA-256 | Current SHA-256 |
| --- | --- | --- |
| Panel/window | `9be5efca42920f58d153056273414746bd8e5210b7b09fb57c6a47ec37a806c3` | `e4e0a797c087c2d4fb61fec9155b807312bd321633e8326a37bfe63073f38a9e` |
| Eligible solid | `57ca7ba7b3d107f651a36343faf4494525e42a7070f99456705a648f35418d57` | `a850a7a79d5fadd5a6c063ee95c762b0b5732b33b6bb67e965d272631b76c802` |
| Terrain | `0f7090fd1d49dec254bb01173246bef1d42c196e2264c658cd30191cf5632b30` | `8a457bb78e1cf4e108c918fb539f64152ff6c56248613bd4aa8dedbb441883eb` |
| Selection/collision | `999f5ff21f32b772a6abf31aec1bea31a7a054e6b4433ea09be8c66f61390585` | `6ead83660b533edeca426e9768a64cfa82c1e5260814b8dd2f671bd7d0c8b771` |
| Split Door artwork | `3c59b113276aadb83fb99bf8481ac1629b5857ea779abd81d0d2eb8e0e433a8b` | `1e2f6d2c97c554f575f3935ed19c33107c1624a995070d34b15d780d9c2f02d0` |

With the retained Front/Back centers, the narrower body exposes additional valid fluid regions. The Source 17,856-profile volume corpus changes from 124,160 cells/725,120 faces to 158,976 cells/889,984 faces; native geometry matches all profiles exactly. Stored amounts and ownership are unchanged. The 28,672 opening-fill and 64,512 outlet cases retain their original results.
