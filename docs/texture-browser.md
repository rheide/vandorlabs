# Texture browser

The [interactive browser](texture-browser/index.html) contains the built-in texture catalog, including native glass finishes. Categories and labels come from the same catalogs as the in-game picker. Search by name, ID or category; expand nested categories; click a texture for a larger preview. Each category links to a downloadable collage. The [texture overview](texture-overview.md) embeds the top-level collages directly in the repository's documentation.

The browser works by opening `docs/texture-browser/index.html` locally. It does not require a server or external scripts. To publish it through GitHub Pages, select **Settings → Pages → Deploy from a branch → master → /docs**. The site root opens the browser. Repository Pages configuration is separate from committing the static files.

Regenerate after changing selectable artwork, labels, visibility or categories:

```text
python tools/generate_texture_browser.py
python tools/generate_texture_browser.py --check
```

The generator reads the Java legacy-material and native-glass lists, `texture_menu.json`, `unified_textures.json`, and the active default/additional artwork. It keeps nested category paths, skips hidden compatibility aliases and duplicate resolution tiers, and writes thumbnails, category collages and the overview page together. Light states remain separate. Transparent textures use a checkerboard preview. Installation-specific Custom textures are excluded.
