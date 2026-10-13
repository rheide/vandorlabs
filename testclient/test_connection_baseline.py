#!/usr/bin/env python3
"""Regression checks for screenshot comparison decisions, independent of a live client."""
import unittest
import json,tempfile,contextlib,io
from pathlib import Path
import connection_baseline as baseline
from PIL import Image,ImageDraw

class ComparisonTests(unittest.TestCase):
    def fixture(self):
        image=Image.new('RGB',(1920,1080),(60,95,30))
        ImageDraw.Draw(image).rectangle((800,300,1120,760),fill=(70,80,95))
        return image
    def measure(self,before,after):return baseline.metrics(before,after)[0]
    def test_identical(self):
        image=self.fixture();result=self.measure(image,image.copy())
        self.assertTrue(result['passed']);self.assertEqual(result['changed_pixels'],0)
    def test_added_geometry_outside_original_mask(self):
        image=self.fixture();after=image.copy()
        ImageDraw.Draw(after).rectangle((1220,340,1310,650),fill=(70,80,95))
        self.assertFalse(self.measure(image,after)['passed'])
    def test_removed_component(self):
        image=self.fixture();after=image.copy()
        ImageDraw.Draw(after).rectangle((800,300,900,760),fill=(60,95,30))
        self.assertFalse(self.measure(image,after)['passed'])
    def test_nonuniform_grass_only_noise(self):
        image=self.fixture();after=image.copy();draw=ImageDraw.Draw(after)
        for x in range(480,1440,7):
            for y in range(216,918,11):
                # The whole noise rectangle must remain outside the wall and its edge.
                if x+5>=796 and x<=1124 and y+9>=296 and y<=764:continue
                draw.rectangle((x,y,x+5,y+9),fill=(35+x%20,90+y%35,20+x%15))
        self.assertTrue(self.measure(image,after)['passed'])
    def test_small_uniform_lighting_change(self):
        image=self.fixture();after=image.copy()
        ImageDraw.Draw(after).rectangle((800,300,1120,760),fill=(74,84,99))
        self.assertTrue(self.measure(image,after)['passed'])
    def test_material_color_change(self):
        image=self.fixture();after=image.copy()
        ImageDraw.Draw(after).rectangle((800,300,1120,760),fill=(120,70,90))
        self.assertFalse(self.measure(image,after)['passed'])
    def textured_fixture(self):
        image=self.fixture();draw=ImageDraw.Draw(image)
        for x in range(480,1440,8):
            for y in range(216,918,12):
                if x+7>=796 and x<=1124 and y+11>=296 and y<=764:continue
                draw.rectangle((x,y,x+7,y+11),fill=(35+x%27,80+y%40,18+(x+y)%21))
        return image
    def illuminate(self,image):
        result=Image.new('RGB',image.size)
        result.putdata([tuple(round(channel*gain) for channel,gain in zip(pixel,(1.35,1.35,1.2))) for pixel in baseline.pixels(image)])
        return result
    def test_coherent_background_illumination(self):
        image=self.textured_fixture();result=self.measure(image,self.illuminate(image))
        self.assertIsNotNone(result['grass_lighting']);self.assertTrue(result['passed'])
    def test_material_change_with_coherent_illumination(self):
        image=self.textured_fixture();after=self.illuminate(image)
        ImageDraw.Draw(after).rectangle((800,300,1120,760),fill=(145,100,112))
        self.assertFalse(self.measure(image,after)['passed'])
    def test_added_geometry_with_coherent_illumination(self):
        image=self.textured_fixture();after=self.illuminate(image)
        ImageDraw.Draw(after).rectangle((1220,340,1310,650),fill=(94,108,114))
        self.assertFalse(self.measure(image,after)['passed'])
    def test_unsafe_illumination_requires_review(self):
        image=self.textured_fixture();after=Image.new('RGB',image.size)
        after.putdata([tuple(min(255,round(channel*1.8)) for channel in pixel) for pixel in baseline.pixels(image)])
        result=self.measure(image,after)
        self.assertIsNone(result['grass_lighting']);self.assertFalse(result['passed'])
    def test_dimensions(self):
        with self.assertRaises(ValueError):self.measure(self.fixture(),Image.new('RGB',(960,540)))
    def test_active_baseline_resolution(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);config=root/'active.json';selected=root/'approved-v2';selected.mkdir()
            (selected/'baseline.json').write_text('{}',encoding='utf-8')
            config.write_text(json.dumps({'schema':'vandorlabs-active-connection-baselines-v1','baselines':{'l':'approved-v2'}}),encoding='utf-8')
            self.assertEqual(baseline.default_baseline('l',config),selected)
            self.assertEqual(baseline.default_baseline('t',config),root/'regular-wall-connections-t')
            self.assertEqual(baseline.default_baseline('x',config),root/'regular-wall-connections-x')
            for scope in baseline.DIAGONAL_SCOPES:
                self.assertEqual(baseline.default_baseline(scope,config),root/scope)
            config.write_text(json.dumps({'schema':'vandorlabs-active-connection-baselines-v1',
                                          'baselines':{'l':'approved-v2',baseline.DIAGONAL_SCOPES[0]:'approved-v2'}}),encoding='utf-8')
            self.assertEqual(baseline.default_baseline(baseline.DIAGONAL_SCOPES[0],config),selected)
            (selected/'baseline.json').unlink()
            with self.assertRaises(ValueError):baseline.default_baseline('l',config)
    def test_compact_review_approves_only_displayed_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);capture=root/'capture';capture.mkdir();target=root/'approved'
            cases=[]
            for name in ['visible','hidden']:
                file=name+'.png';self.fixture().save(capture/file)
                cases.append(dict(name=name,file=file,title=name,roles=[]))
            (capture/'connection-review.json').write_text(json.dumps({'cases':cases}),encoding='utf-8')
            (capture/'manifest.tsv').write_text('name\t'+'\t'.join(baseline.CAMERA)+'\n'+''.join(name+'\t'+'\t'.join(['0']*len(baseline.CAMERA))+'\n' for name in ['visible','hidden']),encoding='utf-8')
            feedback=root/'review.json'
            feedback.write_text(json.dumps({'schema':'vandorlabs-connection-review-v1','captureId':'capture','defaultStatus':'ok','reviews':{},'displayedFiles':['visible.png']}),encoding='utf-8')
            with contextlib.redirect_stdout(io.StringIO()):baseline.create(feedback,capture,target)
            manifest=json.loads((target/'baseline.json').read_text())
            self.assertEqual([row['file'] for row in manifest['cases']],['visible.png'])
            self.assertEqual(manifest['excluded_status_counts'],{'unreviewed':1})
            self.assertFalse((target/'hidden.png').exists())

if __name__=='__main__':unittest.main()
