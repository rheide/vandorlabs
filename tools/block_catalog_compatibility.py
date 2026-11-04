"""Pin previous-release block order while allowing only explicit 2.8 migrations."""
import hashlib
import json
from pathlib import Path

RETIRED = {'clear_cockpit_glass', 'pale_cyan_cockpit_glass', 'smoked_cockpit_glass'}
RENAMED = {'programmable_diagonal_half_console': 'programmable_diagonal_half_screen'}


def historical_blocks(entries):
    previous = json.loads((Path(__file__).parent / 'fixtures/block_ids_2.7.json').read_text())
    assert len(previous) == 512
    assert hashlib.sha256('\n'.join(previous).encode()).hexdigest() == 'c33c9eb2e9f899a29010cdd09d10bb793790d35fc88b50066d7443d2e3d9533e'
    expected = [RENAMED.get(name, name) for name in previous if name not in RETIRED]
    current = [entry['id'] for entry in entries]
    assert len(current) == len(set(current)), 'Duplicate block catalog ID'
    assert not RETIRED.intersection(current), 'Retired cockpit glass returned to the catalog'
    assert not RENAMED.keys() & set(current), 'Old block name returned without canonical migration'
    assert current[:len(expected)] == expected, 'Unrelated block removed, renamed or reordered'
    # Existing artwork audit hashes still describe their original historical IDs.
    # Return those IDs only after validating the entire migrated 2.7 prefix.
    return [{'id': name} for name in previous]
