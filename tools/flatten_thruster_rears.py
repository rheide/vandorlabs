"""Make hexagonal propulsion housings meet adjacent axial blocks without a rear bevel."""
from pathlib import Path
from generate_thruster_streams import inventory_obj

MODELS = Path(__file__).resolve().parents[1] / 'generated-resources/assets/vandorlabs/models/block'
FAMILIES = ('rocket_thruster', 'ion_drive', 'plasma_vent', 'impulse_engine')


def flatten(path):
    lines = path.read_text().splitlines()
    vertices = [tuple(map(float, line.split()[1:])) for line in lines if line.startswith('v ')]
    material = None
    output = []
    for line in lines:
        if line.startswith('usemtl '):
            material = line.split()[1]
        if line.startswith('f '):
            face = [vertices[int(token.split('/')[0])-1] for token in line.split()[1:]]
            if material == 'dark_trim' and min(p[2] for p in face) >= .96249:
                continue
        if line.startswith('v '):
            x, y, z = map(float, line.split()[1:])
            if abs(z - .9625) < 1e-7:
                z = 1
            elif abs(z - 1) < 1e-7:
                x, y = .5 + (x-.5)/.95, .5 + (y-.5)/.95
            line = f'v {x:.8f} {y:.8f} {z:.8f}'
        output.append(line)
    path.write_text('\n'.join(output)+'\n')


if __name__ == '__main__':
    for family in FAMILIES:
        base = family + '_hexagonal'
        # Skip already-flat files, including their unused former bevel vertices.
        for state in ('off', 'on', 'stream'):
            path = MODELS / f'{base}_{state}.obj'
            if any(line.startswith('v ') and abs(float(line.split()[3])-.9625)<1e-7 for line in path.read_text().splitlines()):
                flatten(path)
        inventory_obj(MODELS / f'{base}_on.obj', MODELS / f'{base}_inventory.obj')
    print('Flattened rear ends for all four hexagonal propulsion families and inventory models')
