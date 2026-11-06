#!/usr/bin/env python3
"""Retain canonical authored card/chip geometry and textures under two native item IDs."""
import json
from pathlib import Path
import re
import shutil


def companies(root: Path):
    source = (root / 'src/portable/java/com/vandorlabs/credentials/CredentialCatalog.java').read_text()
    match = re.search(r'DESIGNS\s*=\s*\{([^}]+)\}', source)
    if not match:
        raise ValueError('Canonical credential company catalog missing')
    companies = json.loads('[' + match.group(1) + ']')
    if len(companies) != 7:
        raise ValueError('Credential company IDs must remain 0..6')
    return companies


def generate(root: Path, out: Path):
    assets = out / 'assets/vandorlabs'
    names = companies(root)
    sources = set()
    for kind in ('access_card', 'data_chip'):
        for company in names:
            name = kind + '_' + company
            model_path = root / 'src/main/resources/assets/vandorlabs/models/item' / (name + '.json')
            model = json.loads(model_path.read_text())
            target = assets / 'models/item' / model_path.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(model_path, target)
            for texture in model['textures'].values():
                if not texture.startswith('vandorlabs:items/' ):
                    raise ValueError('Unexpected credential texture: ' + texture)
                sources.add(texture)
        model = {'parent': 'vandorlabs:item/' + kind + '_' + names[0], 'overrides': [
            {'predicate': {'custom_model_data': index}, 'model': 'vandorlabs:item/' + kind + '_' + company}
            for index, company in enumerate(names) if index
        ] + [{'predicate': {'custom_model_data': 7}, 'model': 'vandorlabs:item/' + kind + '_' + names[0]}]}
        (assets / 'models/item' / (kind + '.json')).write_text(json.dumps(model, separators=(',', ':')) + '\n')
    for texture in sorted(sources):
        relative = texture.split(':', 1)[1] + '.png'
        target = assets / 'textures' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / 'texture-packs/additional/assets/vandorlabs/textures' / relative, target)
    return [{'type': 'minecraft:single', 'resource': texture} for texture in sorted(sources)]


def translations(root: Path):
    wanted = {'item.vandorlabs.' + kind + '.' + company + '.name' for kind in ('access_card', 'data_chip') for company in companies(root)}
    lang = {}
    for line in (root / 'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines():
        key, separator, value = line.partition('=')
        if separator and key in wanted:
            lang[key.removesuffix('.name')] = value
    if len(lang) != 14:
        raise ValueError('Missing canonical credential item translations')
    return lang
