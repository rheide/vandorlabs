"""Guard the narrow exception against blanket footprint or render-box relaxation."""
import unittest
from bridge_model_bounds import attachment_bounds, valid_vertex
from generate_builder_attachments import presentation


class BridgeBoundsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.presentation = presentation()
        cls.attachments = attachment_bounds(cls.presentation)

    def spec(self, design):
        s = self.presentation[design]
        return dict(design=design, width=s['width'], height=4 if design=='enclosed_gantry' else 3, depth=s['depth'])

    def test_authored_attachments_and_all_rotations_fit_render_padding(self):
        for design, groups in self.attachments.items():
            spec = self.spec(design)
            for group, b in groups.items():
                for x in (b[0], b[3]):
                    for y in (b[1], b[4]):
                        for z in (b[2], b[5]):
                            with self.subTest(design=design, group=group, point=(x,y,z)):
                                self.assertTrue(valid_vertex(spec, (x,y,z), {group}, self.attachments))
                                w,d = spec['width'],spec['depth']
                                for X,Z,W,D in ((x,z,w,d),(w-x,d-z,w,d),(d-z,x,d,w),(z,w-x,d,w)):
                                    self.assertTrue(-.5<=X<=W+.5 and -.5<=Z<=D+.5)

    def test_only_named_attachment_faces_can_leave_footprint(self):
        for design in self.attachments:
            spec = self.spec(design)
            s = self.presentation[design]
            point = (s['keyboardX'], s['keyboardY'], s['keyboardZ'])
            for groups in (set(), {'static__base_front'}, {'static__keyboard_extra'},
                           {'static__keyboard','static__base_front'}, {'gantry_head'}):
                with self.subTest(design=design, groups=groups):
                    self.assertFalse(valid_vertex(spec, point, groups, self.attachments))
            other = dict(spec,design='synthesis_tower')
            self.assertFalse(valid_vertex(other,point,{'static__keyboard'},self.attachments))

    def test_attachment_names_cannot_hide_misplaced_geometry(self):
        for design in self.attachments:
            spec = self.spec(design)
            s = self.presentation[design]
            x,y,z=s['keyboardX'],s['keyboardY'],s['keyboardZ']
            for point in ((x,y,z-.001),(x-.001,y,z),(x,y+.001,z),
                          (-.01,y,z),(x,y,-.501),(float('nan'),y,z),(x,float('inf'),z)):
                with self.subTest(design=design, point=point):
                    self.assertFalse(valid_vertex(spec,point,{'static__keyboard'},self.attachments))
            self.assertFalse(valid_vertex(spec,(s['screenX'],s['screenY'],-.026),{'static__display'},self.attachments))

    def test_body_stays_inside_original_footprint(self):
        for design in self.attachments:
            spec = self.spec(design)
            self.assertTrue(valid_vertex(spec,(0,0,0),{'body'},self.attachments))
            self.assertTrue(valid_vertex(spec,(spec['width'],spec['height'],spec['depth']),{'body'},self.attachments))
            for p in ((-.001,1,1),(1,-.001,1),(1,1,-.001),
                      (spec['width']+.001,1,1),(1,spec['height']+.001,1),(1,1,spec['depth']+.001)):
                self.assertFalse(valid_vertex(spec,p,{'body'},self.attachments))


if __name__=='__main__':
    unittest.main()
