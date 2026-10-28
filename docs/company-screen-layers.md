# Company screen layers

The Company groups contain six subjects for each company: reactor, biology, medical, radar, communications and navigation. Each subject has a six-frame animation. Full displays use 128×128 frames; half-height controls use 128×64 frames. Normal animation speed advances every four ticks, matching the authored 200 ms timing.

Choose the display from **Screens / Company / company name** and its independent outer border from **Frame / Company / company name**. Each company has one full and one half-height border, shared by all six subjects. The display provides its internal dividers and controls; the border adds the outer casing and company logo. Both cover the same complete surface, so they require no placement offsets.

Borders use 64-pixel runtime artwork. A half-height border retains its 64×32 aspect inside a square atlas sprite; its frame metadata selects the authored half. Display layers retain their transparency throughout animation, allowing the matching border to overlay the display.

The importer appends catalog entries without moving existing saved texture choices. `tools/import_company_screen_layers.py` consumes the supplied layer pack, and `tools/test_company_screen_layers.py` checks all twelve borders and 432 display frames for dimensions, alpha and timing. The normal texture browser lists the same catalog choices.
