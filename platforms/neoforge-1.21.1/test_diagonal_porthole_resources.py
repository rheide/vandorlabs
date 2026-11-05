#!/usr/bin/env python3
"""Inactive diagonal porthole assets retain the current authored item and recipe."""
import copy
import json
import unittest
from generate_panel_resources import SOURCE, diagonal_porthole_projection


class ProjectionChecks(unittest.TestCase):
    def test_projection(self):
        assets = diagonal_porthole_projection()
        name = 'programmable_diagonal_porthole'
        item = assets['assets/vandorlabs/models/item/' + name + '.json']
        original = json.loads((SOURCE / ('models/item/' + name + '.json')).read_text())
        self.assertEqual(original['elements'], item['elements'])
        self.assertEqual(original['display'], item['display'])
        self.assertEqual('vandorlabs:glass-palette/clear', item['textures']['glass'])
        self.assertEqual('vandorlabs:frame-default/metal_side', item['textures']['side'])
        variants = assets['assets/vandorlabs/blockstates/' + name + '.json']['variants']
        self.assertEqual(8, len(variants))
        self.assertTrue(all('depth=' not in key for key in variants))
        recipe = copy.deepcopy(assets['data/vandorlabs/recipe/' + name + '.json'])
        recipe['result']['item'] = recipe['result'].pop('id')
        self.assertEqual(json.loads((SOURCE / ('recipes/' + name + '.json')).read_text()), recipe)


if __name__ == '__main__':
    unittest.main()
