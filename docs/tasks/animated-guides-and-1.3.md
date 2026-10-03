# Animated guides and 1.3 highlights

## Owner request

> The trapdoor names are very long, come up with some shorter names. The door gif looks ok-ish, though animation is a bit jittery, but I'd say acceptable if you can't think of ways to improve it. Let's convert the rest of our door docs to use gifs as well, you can make the gifs a bit smaller, like 70% size. We just need them for each of the animations (sliding, rotating, all the various trapdoor and diagonal trapdoor motions). Let's add it for each of the programmable ramp block as well. We also should add a version-1.3.md doc that lists all the cool new things we added. Animations definitely a plus there. Lastly, I would like you to suggest a better categorization for the blocks in the Hull and Panels categories - should some of these be moved? Is there enough variation to add a third category?

## Work

- [x] Shorten the eight trapdoor finish labels; preserve IDs and sources.
- [x] Reduce GIF capture stalls and output size to 70%.
- [x] Capture all documented door, trapdoor, diagonal trapdoor and ramp motions.
- [x] Replace motion still comparisons with GIFs and keep design/configuration stills.
- [x] Add a linked `docs/gallery/version-1.3.md` feature guide with animations.
- [x] Review Hull/Panels artwork and suggest grouping and a third category, without applying those moves.
- [x] Java 8 build, relevant live checks, per-clip motion and timing validation, visual review, commits.

## Short labels

| Previous | New |
| --- | --- |
| Cyan-Lit Armored Sci-Fi Hatch-4 | Armored Hatch |
| Cyan-Lit Gunmetal Slit Hatch-6 | Slit Hatch |
| Cyan-Lit Gunmetal Utility Hatch-1 | Utility Hatch |
| Cyan-Lit Gunmetal Window Hatch-8 | Viewport Hatch |
| Cyan-Lit Industrial Sci-Fi Hatch-5 | Industrial Hatch |
| Gunmetal Service Hatch With Cyan Lights-3 | Service Hatch |
| Gunmetal Twin-Window Sci-Fi Hatch-7 | Twin-Window Hatch |
| Reinforced Cyan X Hatch-2 | Reinforced Hatch |

## Follow-up requests

> Also this has been bothering me for a while, but the hotbar icon for Programmable Diagonal Screen looks nothing like the block, let's fix that

> There might be a bug with the landing gear - every time I load up my world the landing gear seems to be retracted? Possible issue on load?

- [x] Match the diagonal screen inventory model to the current solid wedge and inspect its live hotbar rendering.
- [x] Reproduce and fix landing gear load/unload state resets; verify persistence and subsequent redstone edges.

## Completion evidence

- Fifty 420×350 GIFs: [validation and per-clip results](../animation-validation.md).
- Java 8 standard build and non-rendering checks passed; focused live dialogs passed in `testclient/render-run.0hQq8F`.
- Shared-material IDs, source paths and ordering were compared against the original catalog; only eight finish labels changed.
- Landing gear and Diagonal Screen fixes committed separately as `73665b65` and `a5f5551e`.

## Category approval

> Sounds good, apply all of that.

- [ ] Apply the full approved Hull/Panels/Industrial/Tech move list, preserving saved choices and validate the revised shared picker.
