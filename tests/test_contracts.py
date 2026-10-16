"""Pure production contracts. No API, model or publication mocks."""
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'factory'))
from publish import Ctx,Media,captions,safe_error,publish_all,PublishError
import render
from PIL import Image,ImageDraw

class Contracts(unittest.TestCase):
    def test_cta_replacement(self):
        c=Ctx(None,'audit','https://example.org/go/','ubt-family-shield-ru',promotional=True)
        self.assertEqual(captions({'captions':{'telegram':{'text':'Читать: {link}'}}},'telegram',c)['text'],'Читать: https://example.org/go/telegram-ubt-family-shield-ru')
    def test_no_promotion_is_default(self):
        c=Ctx(None,'audit','https://example.org/','audit')
        self.assertFalse(c.promotional)
        for value in ('Читать {link}', 'https://example.org', 'www.example.org', 't.me/channel'):
            with self.assertRaises(PublishError):
                captions({'captions':{'pinterest':{'description':value}}},'pinterest',c)
        self.assertEqual(captions({'captions':{'pinterest':{'title':'Памятка'}}},'pinterest',c), {'title':'Памятка'})
    def test_stage_rejects_outside_media_dir(self):
        from publish import stage_media_file
        with self.assertRaises(PublishError):
            stage_media_file(Ctx(None,'audit','','audit'), Media(Path('/tmp/approved-media'),'id',''), Path(__file__), 'YOUTUBE_UPLOAD_VIDEO','youtube')
    def test_media_url(self):
        self.assertEqual(Media(Path('.'),'id','https://example.org/m/').video_url,'https://example.org/m/media/id/video.mp4')
    def test_long_cyrillic_heading(self):
        render.ensure_fonts()
        d=ImageDraw.Draw(Image.new('RGB',(1080,1920)))
        f,ls=render.fit(d,'*Подозрительный звонок*',render.BOLD,920,118,60,5)
        self.assertTrue(all(d.textlength(x.replace('*',''),font=f)<=920 for x in ls))
    def test_overflow_fails_closed(self):
        render.ensure_fonts()
        d=ImageDraw.Draw(Image.new('RGB',(1080,1920)))
        with self.assertRaises(ValueError):render.fit(d,'ы'*100,render.BOLD,920,118,60,5)
    def test_error_redacts_url(self):
        self.assertNotIn('private-secret',safe_error(Exception('failed https://example.org/private-secret')))
    def test_no_reservation_no_send(self):
        a={'id':'audit','platform':'discord','meta':{'publish_enabled':True,'adapter_verified':True}}
        with self.assertRaises(PublishError):publish_all([a],{},Media(Path('.'),'id','https://example.org/'),Ctx(None,'audit','https://example.org/','audit'))

if __name__=='__main__':unittest.main()

class Briefs(unittest.TestCase):
    def setUp(self):
        import json
        self.topics=json.loads((Path(__file__).resolve().parents[1]/'factory'/'topics.json').read_text())
    def test_bank_shape(self):
        self.assertGreaterEqual(len(self.topics),20)
        ids=[t['id'] for t in self.topics]
        self.assertEqual(len(ids),len(set(ids)))
    def test_lint_and_enrich_roundtrip(self):
        import briefs,json
        for i,t in enumerate(self.topics):
            full=briefs.script_for(t,i)
            self.assertEqual(briefs.lint(full),[],t['id'])
            slim=briefs.slim_body(full)
            self.assertNotIn('captions',slim)
            back=briefs.enrich(dict(slim),self.topics)
            self.assertEqual(json.dumps(back['captions'],sort_keys=True,ensure_ascii=False),
                             json.dumps(full['captions'],sort_keys=True,ensure_ascii=False))
    def test_no_forbidden_anywhere(self):
        import briefs,json,re
        pat=re.compile(r'https?:|www\.|\{link\}|ссылк[^ ]*\s+(в|из)\s+(описани|профил)',re.I)
        for i,t in enumerate(self.topics):
            full=briefs.script_for(t,i)
            self.assertIsNone(pat.search(json.dumps({'beats':full['beats'],'captions':full['captions']},ensure_ascii=False)),t['id'])

class Plan(unittest.TestCase):
    def test_deterministic(self):
        import plan, datetime
        d = datetime.date(2026, 10, 19)
        self.assertEqual(plan.daily_plan(d), plan.daily_plan(d))
    def test_variety_and_combos(self):
        import plan, datetime
        fmts = [plan.daily_plan(datetime.date(2026, 10, 1) + datetime.timedelta(days=i))["instagram"] for i in range(30)]
        self.assertGreaterEqual(len(set(fmts)), 4)
        self.assertTrue(any("+" in f for f in fmts))
        self.assertTrue(all(f in plan.IG_FORMATS for f in fmts))
    def test_no_ig_repeat(self):
        import plan, datetime
        days = [plan.daily_plan(datetime.date(2026, 10, 1) + datetime.timedelta(days=i))["instagram"] for i in range(60)]
        self.assertTrue(all(a != b for a, b in zip(days, days[1:])))
    def test_slide_url(self):
        self.assertEqual(Media(Path('.'), 'abc', 'https://example.org/m/').slide_url(3),
                         'https://example.org/m/media/abc/slide_03.png')
    def test_unknown_ig_format_fails_closed(self):
        from publish import pub_instagram
        acc = {'id': 'audit', 'platform': 'instagram', 'meta': {'ig_user_id': '1'}}
        ctx = Ctx(None, 'audit', 'https://example.org/', 'audit', formats={'instagram': 'bogus'})
        with self.assertRaises(PublishError):
            pub_instagram(acc, {'captions': {}}, Media(Path('.'), 'id', 'https://example.org/m/'), ctx)
