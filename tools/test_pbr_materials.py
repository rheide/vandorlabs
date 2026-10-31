"""Check semantic material choices and pixel masks without a GL context."""
import unittest
from PIL import Image
from generate_pbr_materials import classify, companion


class MaterialChecks(unittest.TestCase):
    def test_named_finishes_override_categories(self):
        cases = [
            ('blocks/bluegray_carpet.png', '', 'fabric'),
            ('blocks/midnight_matte_hull.png', 'Metal', 'matte'),
            ('blocks/midnight_satin_hull.png', '', 'satin'),
            ('blocks/light_alloy_hull.png', '', 'brushed_alloy'),
            ('blocks/modular_polished_metal_floor_1.png', '', 'polished_metal'),
            ('blocks/textures2_t3_r1_c7.png', 'Weathered Hull 1', 'matte'),
            ('blocks/composite_nano_fiber_hull_1.png', '', 'composite'),
            ('blocks/frames/company/vandor_full.png', '', 'frame'),
            ('blocks/programmable_glass/medium/cyan_strong.png', '', 'glass'),
            ('blocks/console_inputs/example_static.png', '', 'display'),
            ('blocks/some_unknown_artwork.png', '', 'paint'),
            ('gui/metal_frame.png', '', 'neutral'),
            ('items/industrial_alloy_ingot.png', '', 'brushed_alloy'),
            ('models/armor/roles/scientist_chestpiece.png', '', 'fabric'),
            ('block/ramp_slab_side_on.png', '', 'paint'),
            ('blocks/materials/satin_silver.png', '', 'satin_silver'),
            ('blocks/materials/cool_steel.png', '', 'brushed_steel'),
            ('blocks/materials/pale_titanium.png', '', 'titanium'),
        ]
        for name, hint, expected in cases:
            self.assertEqual(classify(name, hint), expected, name)

    def test_metal_excludes_paint_recesses_and_transparency(self):
        image = Image.new('RGBA', (5, 1))
        image.putdata([(160, 160, 160, 255), (220, 20, 20, 255), (10, 10, 10, 255),
                       (160, 160, 160, 0), (160, 160, 160, 90)])
        encoded = companion(image, 'brushed_alloy')
        result = [encoded.getpixel((x, 0)) for x in range(5)]
        self.assertEqual([p[1] for p in result], [232, 10, 10, 0, 232])
        self.assertEqual(result[3], (0, 0, 0, 255))
        self.assertTrue(all(pixel[3] == 255 for pixel in result))

    def test_glass_and_fabric_are_dielectrics(self):
        image = Image.new('RGBA', (2, 1), (220, 220, 220, 255))
        self.assertEqual(companion(image, 'glass').getpixel((0, 0)), (228, 10, 0, 255))
        fabric = companion(image, 'fabric').getpixel((0, 0))
        self.assertLess(fabric[0], 20)
        self.assertEqual(fabric[1:], (10, 38, 255))

    def test_coated_finishes_share_maps_across_diffuse_colors(self):
        for profile in ('paint', 'matte', 'satin', 'fabric', 'rubber', 'composite', 'display', 'glass'):
            dark=Image.new('RGBA', (16, 16), (20, 35, 50, 255))
            light=Image.new('RGBA', (16, 16), (230, 200, 100, 255))
            self.assertEqual(companion(dark, profile).tobytes(), companion(light, profile).tobytes(), profile)


if __name__ == '__main__':
    unittest.main()
