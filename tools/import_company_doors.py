"""Import company door artwork into stable appended texture choices."""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
COMPANIES = {"pinnacle": "Pinnacle", "ashiro_mai": "Ashiro-Mai", "paladyne": "Paladyne",
             "vektor": "Vektor", "seyr": "Seyr", "vandor": "Vandor"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    catalog = ROOT / "generated-resources/assets/vandorlabs/data/unified_textures.json"
    entries = json.loads(catalog.read_text())
    destination = ROOT / "texture-packs/additional/assets/vandorlabs/textures/blocks/company_doors"
    for company, label in COMPANIES.items():
        folder = destination / company
        folder.mkdir(parents=True, exist_ok=True)
        for design in ("standard", "security", "light"):
            with Image.open(args.source / company / (design + ".png")) as source:
                if source.size != (256, 512):
                    raise ValueError(f"Unexpected source dimensions: {company}/{design}: {source.size}")
                source.convert("RGBA").resize((128, 256), Image.Resampling.LANCZOS).save(folder / (design + ".png"))
            identifier = f"company_door_{company}_{design}"
            entry = dict(id=identifier, label=design.title(), category="Doors", menuGroup=label,
                         source=f"company_doors/{company}/{design}", rectangular=True)
            old = next((i for i, item in enumerate(entries) if item["id"] == identifier), None)
            if old is None:
                entries.append(entry)
            else:
                entries[old] = entry
    catalog.write_text(json.dumps(entries, indent=2) + "\n")
    print("Imported 18 company doors at 128x256; alpha preserved; stable Doors/company choices")


if __name__ == "__main__":
    main()
