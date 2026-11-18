#!/usr/bin/env python3
"""Exercise the real map-image oracle with current assets and corrupted renders."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('dynmap_fixture', ROOT / 'testclient/test_dynmap_render.py')
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


class BatteryOracleChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='battery-oracle-')
        self.output = Path(self.temp.name)
        (self.output / 'mods').mkdir()
        self.models = [json.loads((ROOT / ('src/main/resources/assets/vandorlabs/models/block/'
                         'module_rack/slot_seated_battery_0_' + str(level) + '.json')).read_text())
                       for level in range(5)]

    def tearDown(self):
        self.temp.cleanup()

    def render(self, mode='correct'):
        # A synthetic orthographic raster tests the validator, not Minecraft or
        # Dynmap itself. Geometry is read from all five actual authored models.
        matrix = [40, 0, -40, 20, 48, 20]
        config = self.output / 'dynmap/web/standalone/dynmap_config.json'
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(json.dumps({'worlds': [{'name': 'world', 'maps': [
            {'name': 'battery_fixture', 'prefix': 'battery_fixture', 'worldtomap': matrix}]}]}))
        with ZipFile(self.output / 'mods/vandorlabs-fixture.jar', 'w') as jar:
            for level, model in enumerate(self.models):
                jar.writestr('assets/vandorlabs/models/block/module_rack/slot_seated_battery_0_' + str(level) + '.json', json.dumps(model))
        canvas = Image.new('RGB', (1536, 1408), (0, 0, 0))
        draw = ImageDraw.Draw(canvas)
        labels = []
        for row, facing in enumerate(('south', 'east', 'up')):
            for level in range(5):
                label = dict(x=level*3, y=12, z=row*4, module='battery', facing=facing,
                             energy=level*2500, label=facing+'-'+str(level))
                labels.append(label)
                selected = 4 if mode == 'always-full' else 0 if mode == 'always-empty' else level
                for part in self.models[selected]['elements']:
                    if not part.get('name', '').startswith('power_port_bar_'):
                        continue
                    is_on = part['name'] == 'power_port_bar_on'
                    lo, hi = list(part['from']), list(part['to'])
                    if is_on and mode == 'reversed':
                        lo[0], hi[0] = 14-hi[0], 14-lo[0]
                    points = []
                    for x, y, z in ((lo[0],hi[1],hi[2]), (lo[0],lo[1],hi[2]),
                                    (hi[0],lo[1],hi[2]), (hi[0],hi[1],hi[2])):
                        x, y, z = x/16, y/16, z/16
                        if facing == 'east':
                            x, z = z, 1-x
                        elif facing == 'up':
                            y, z = z, 1-y
                        x += label['x']; y += label['y']; z += label['z']
                        u, v = matrix[0]*x+matrix[2]*z, matrix[3]*x+matrix[4]*y+matrix[5]*z
                        points.append((u+512, 1407-v))
                    color = (30, 21, 20) if not is_on else ((40, 180, 40) if mode == 'old-green' else (180, 50, 50))
                    draw.polygon(points, fill=color)
        for tx in range(-4, 8):
            for ty in range(11):
                path = self.output / ('dynmap/web/tiles/world/battery_fixture/' + str(tx>>5) + '_' + str(ty>>5))
                path.mkdir(parents=True, exist_ok=True)
                canvas.crop((tx*128+512, 1408-(ty+1)*128, (tx+1)*128+512, 1408-ty*128)).save(path / (str(tx)+'_'+str(ty)+'.png'))
        return labels

    def test_current_continuous_bar_passes_all_five_levels_and_visible_facings(self):
        fixture.validate_battery_render(self.output, self.render())
        report = json.loads((self.output / 'battery-render-analysis.json').read_text())
        self.assertEqual(15, len(report))
        self.assertTrue(all(len(row['quarters']) == 4 for row in report))

    def test_bad_render_cannot_pass_with_correct_candidate_geometry(self):
        for mode in ('always-full', 'always-empty', 'reversed', 'old-green'):
            with self.subTest(mode=mode):
                with self.assertRaisesRegex(AssertionError, 'Battery projected fill'):
                    fixture.validate_battery_render(self.output, self.render(mode))

    def test_stretched_uv_or_wrong_clip_is_rejected_before_pixel_sampling(self):
        for field in ('uv', 'bounds'):
            with self.subTest(field=field):
                model = self.models[2]
                on = next(part for part in model['elements'] if part.get('name') == 'power_port_bar_on')
                previous = json.loads(json.dumps(on))
                if field == 'uv':
                    on['faces']['south']['uv'][2] += .5
                else:
                    on['to'][0] += .5
                with self.assertRaisesRegex(AssertionError, 'Battery fill must'):
                    fixture.validate_battery_render(self.output, self.render())
                on.clear(); on.update(previous)


if __name__ == '__main__':
    unittest.main()
