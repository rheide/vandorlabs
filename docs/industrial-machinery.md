# Industrial machinery

The machinery selection contains five Rivet Dynamics models (Computer Core, Reactor, Cross-flow Core, Inertial Dampener and Tractor Beam), Kestrel Warp Cassette and Phase Drive, Hadron Sensor / Analysis Unit, and Hadron and Vektor Navigation Consoles. The compact Rivet models replace their Reference alternatives. Removed machinery has no crafting recipe or inventory entry. Replace removed blocks in existing worlds before upgrading; those registry IDs are no longer available.

Use the configuration tool to choose redstone Disabled, ON or OFF and channels. **ON animation** enables moving light intensity on the active colored elements. It defaults to OFF and is saved with the machine. Redstone still determines whether the machine is active; animation does not produce block updates or redstone signals. Cross-flow elements pulse sequentially across the machine, while other lit surfaces brighten and dim in spatial bands. Vehicle propulsion follows its existing throttle state.

Rivet hull surfaces use a medium grey with a cyan tint. Decorative grey micro fasteners and their black caps have been removed. Structural supports remain.

The original authored structural struts remain. The additional dynamic connecting studs have been removed.

Vektor consoles combine into complete rectangles up to 3 by 3. The top display uses a continuous texture mapping across both triangles, including 2 by 3 assemblies.

Rendering reuses compiled static geometry for each model and mounting direction, rebuilding it after the texture atlas reloads. It performs no per-frame transformation or normal generation for opaque/emissive machinery. Transparent surfaces remain sorted. Animation changes light intensity without rebuilding geometry.

The curated mesh catalogs contain 51,226 faces, down from 182,618. For retained Rivet OFF models, removal of micro fasteners reduces Reactor faces from 3,887 to 1,647 and Cross-flow Core faces from 3,357 to 1,636. These counts measure geometry reduction; frame rates depend on hardware, view distance and scene contents.

Machinery collision geometry is transformed and partitioned by occupied cell once per model and mount orientation. Each tile caches its world collision list and render/selection bounds; repeated camera culling and ray queries reuse these objects. Reactor selection outlines cover the complete assembly, and all four floor cells retain collision and ray selection. This avoids rebuilding the Reactor's 1,008 authored collision cuboids for every occupied tile during each rendered frame.

The Reactor uses 1,647 visible faces and 1,008 collision cuboids; Kestrel Phase Drive uses 871 faces and 112 collision cuboids. Hadron Sensor / Analysis Unit has 4,222 visible faces. Collision data density therefore differs substantially from visible mesh detail. Caching preserves the detailed models and exact collision union rather than introducing a lower-detail distance model.
