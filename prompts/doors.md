We are going to create a door pack for minecraft. You are going to one-shot this. I have attached an image FOR INSPIRATION ONLY - use a similar style (sci-fi, industrial grey, cyan light strips, amber lit-up buttons). Note the hinges in the image, you will need those. The theme is sci-fi-industrial, stick to that. This is intended for a Minecraft mod, so don't include exceptionally tiny details as we do not want a very high resolution result. The results should look serious, not cartoon-y. Descriptions below list the MINIMUM things that should be included, but you can and should include more detail where you see fit, in an appropriate style and level for a sci-fi minecraft mod that has slightly more detail than vanilla minecraft, but it ultimately still quite blocky and pixel-art-like.

Specification:

- A square door frame with 4 thin sides that can fit double doors. This door frame is industrial gray, horizontally symmetrical, with thin cyan light strips. Should look good when occupying 1/16th of a Minecraft block. You will add a glass shimmer effect to the transparent area. This glass shimmer should tile well so don't concentrate it in the corners or the center.
- There should be several door designs in a consistent style: 
  - An Observation door with a fairly large rectangular porthole in the top half of the door. Grating near the bottom. The porthole area should be transparent and have a similar glass shimmer effect as the door frame's.
  - An Airlock door, which is more heavy-duty looking, without a window. No grating.
  - A Standard door, without a porthole but with grating at the bottom.
  - A Security door, which is like a heavier variety of the Standard door.
  - A random door design of your choosing that looks like it fits the rest of the pack.
- Each door needs to look good in three configurations: 1. inside a single door frame, 2. inside a double door frame (slightly wider), 3. without a door frame (wider and taller). There will be no resizing of doors - the door should look good when a little bit of the top, left and bottom textures are clipped away (assuming a left-opening door - the door handle side is always included, the side near the frame may get clipped out).
- Doors may be sliding or rotating, but we will use a single door texture for both. A model for the door hinges will be produced separately. This model should not be very detailed, it will be used in Minecraft to attach the door to the frame. The hinge will be animated.
- Multiple sizes of each asset should be produced. Assets should not be simply resized to achieve the smaller size. We will produce separate assets for each level of detail that are in the same spirit rather than an exact-pixel resize.

You will produce a zip file containing:

- A 256x256, 512x512 and 1024x1024 png with transparency for the double door frame including shimmering glass effect.
- Per door type, a single door texture in sizes 128x256, 256x512 and 512x1024, with the door handle on the right side. This door texture will look correct when none of its edges are covered by the door frame, and when all of its edges are covered by the door frame.
- A very simple 3d model and textures for the hinge that can be imported easily in Minecraft 1.12.2 and Forge. It will have instructions about where it should be attached to the door, and how to animate it.


---------------------------------------

Ok, there is a new door pack in LLMShareDrive/scifi_industrial_door_pack_v2.zip. We are going to create the doors in this pack as new doors that are entirely separate from our existing (Detailed) doors. The pack contains:

- Instructions, read these.
- A separate double-width door frame. We are going to use this for both the single door and double door framed variants.
- Several new door types. Let's prefix this with "Space" for block names. 

You will build:

- Framed and Bare variants for each of the door type. The same door texture is reused for bare, single, and double framed doors. It should be used in its original size and positioned correctly on the door model rather than being resized. For the framed doors, we will detect adjacent doors and drop the frame border where the doors connect, similar to our existing Detailed doors.
- Sliding and Rotating door variants. Similar to the Detailed doors we already have, the Rotating door variants should have hinges. The models, textures and instructions for the hinges are included in the pack. The hinges should only be used for the Rotating doors.
- A 'Space Glass' frame similar to our existing Framed Observation Glass. It should have the same logic for dropping borders when multiple Space Windows are placed side by side. For the border texture you will use the same frame as the doors, cutting and mirroring where required. Transparent glass shimmer texture is also provided.


--- 

Issues:
- Frameless doors go all the way into the next block when opened, we should leave 1px in the block to prevent Z fighting
- Rotating doors: like our existing Detailed rotating doors, these should appear at the edge of the block closest to the player when placed, rather than in the center of the block.
- The hinge moment and open position of the rotating doors seems wrong: for framed doors, the door opening motion goes through the frame, rather than being hinged on/against the frame. Also, the final position of the door in the open state is on the neighboring block rather than on the edge of the door block. Are the hinges on the correct side? Existing Detailed doors do this better.
- Door frames and space glass should be thicker, same thickness as the Detailed doors
- What size resolution are we using for the doors? Let's go one step up in terms of detail.

----------

Doors to delete:
- Glass Airlock Door
- Security Door
- Glass Sliding Airlock Door
- Sliding Security Door
- Detailed Engineering Rotating Door
- Detailed Engineering Sliding Door
- Detailed Observation Sliding Door
- Detailed Observation Rotating Door
- Detailed Split Rotating Double Door
- Detailed Split Rotating Door
- Detailed Split Sliding Double Door
- Detailed Split Sliding Door









 




