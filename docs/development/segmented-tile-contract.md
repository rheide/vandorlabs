# Segmented Door Tile mapping and body depth

The previous fast solid-cell emitter and the clipped window emitter disagree for nonsquare artwork. With the actual Pinnacle Light atlas entry (aspect 1:2), the solid path repeats one complete image per block vertically while the window path repeats half an image per block. A mixed framebuffer therefore shows twice the vertical repetition rate in solid columns. This is the placement-independent rendering defect addressed by the Tile correction.

The focused Source fixture renders both actual paths, captures the mixed result and compares front/back vertical UV density. Existing pre-change panel, eligible-fast, header, selection/collision and fluid corpora remain independently fingerprinted. The correction must retain header depth at four pixels while reducing section bodies from three to two pixels, and must keep the body renderer, collision, rays and fluid-plane geometry in agreement. Saved setting/control indices are unchanged.
