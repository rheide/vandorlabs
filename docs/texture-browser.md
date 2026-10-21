# Texture browser

[Open the GitHub Pages texture browser](https://rheide.github.io/vandorlabs/texture-browser/) once Pages is enabled for this repository. Its HTML, thumbnails and collages are served directly from the committed `docs/` files.

The [interactive browser](texture-browser/index.html) contains the built-in texture catalog, including native glass finishes. Categories and labels come from the same catalogs as the in-game picker. Search by name, ID or category; expand nested categories; click a texture for a larger preview. Each category links to a downloadable collage. The [texture overview](texture-overview.md) embeds the top-level collages directly in the repository's documentation.

The browser works by opening `docs/texture-browser/index.html` locally. It does not require a server or external scripts. To publish it through GitHub Pages, select **Settings → Pages → Deploy from a branch → master → /docs**. The site root opens the browser. Repository Pages configuration is separate from committing the static files.

Regenerate after changing selectable artwork, labels, visibility or categories:

```text
python tools/generate_texture_browser.py
python tools/generate_texture_browser.py --check
```

The generator reads the Java legacy-material and native-glass lists, `texture_menu.json`, `unified_textures.json`, and the active default/additional artwork. It keeps nested category paths, skips hidden compatibility aliases and duplicate resolution tiers, and writes thumbnails, category collages and the overview page together. Light states remain separate. Transparent textures use a checkerboard preview. Installation-specific Custom textures are excluded.

The GitHub Pages URL and publishing source are recorded in `tools/texture_browser_hosting.json`. Once the repository setting above is enabled, pushes to `master` publish the committed documentation automatically. No separate hosting service, asset upload or application build is required. The generated thumbnails avoid downloading full animation sheets and duplicate texture-resolution variants, while using the mod's existing artwork as their source.
