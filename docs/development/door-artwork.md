# Ordinary and Large Door artwork

The shared catalog appends twelve Utility material sheets after its original
815 entries. The original IDs/order and all thirty geometry-design indices stay
unchanged. White, Dark and Slim Glass retain their existing eight entries,
including retired high-detail aliases, under `Doors/Glass`. Utility sheets use
`Doors/Utility`; they are full-leaf material overrides for existing geometry,
not new mechanisms or block IDs.

Imported PNG bytes retain their supplied RGB/RGBA data and native 128×256 size.
The `doorArtwork` descriptor enables full-content previews and paired right-leaf
mirroring in both adapters. Source uses its existing rectangular sprite loader;
native sprites reserve square slots with explicit used U/V extents and extended
edge texels. Native preview descriptors increase from 48 to 60, and Door sprite
descriptors from 68 to 80. The 1,924 authored models remain unchanged. A warmed
100,000-preview lookup batch allocates zero bytes.

Door subcategories preserve their parent's eligibility through the shared
`HousingCategoryPolicy`. Dedicated ordinary/Large pickers admit them; general
housing, navigation and armor filters continue to exclude Door artwork.
Existing Segmented Door eligibility is preserved by its category-prefix adapter;
this addition does not introduce Segmented artwork or settings.

Focused checks cover unchanged catalog IDs, exact imported PNG bytes, all eighty
native sprite content projections, unchanged authored model inheritance, and
browser/collage consistency. Forge checks inspect all 393,216 actual atlas RGBA
texels within one channel unit, ordinary/Large search and clicks, 24 rendered
leaf scenes and paired symmetry. Native checks use an empty gallery to exclude
animated title text, retaining 24 selected-material negatives and exact reload
image equality. A connected native editor checks all twelve search results and
actual selected-material authority roundtrip alongside Glass, Frame and
credential options.

Dynmap tests check every Utility material on ordinary and Large leaf patches.
The isolated exact-standard-JAR smoke includes WorldEdit 6.1.10, all twelve saved
Utility fixtures and both server phases. Runtime retention finalizes after the
actual server exits. These are focused software-rendered/adapter checks; they do
not establish a full release gate or hardware shader performance.
