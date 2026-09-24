"""Run: STELLARIUM_DIR=.cache/stellarium python3 -m unittest tools/stellarium_import/test_build_sky_data.py"""
import datetime as dt
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import build_sky_data as b  # noqa: E402

ST = os.environ.get('STELLARIUM_DIR', '.cache/stellarium')


class Pure(unittest.TestCase):
    def test_normalize_matches_web_app(self):
        self.assertEqual(b.normalize_name('M 041'), 'M41')
        self.assertEqual(b.normalize_name('ngc 7000'), 'NGC7000')
        self.assertEqual(b.normalize_name('Orion Nebula'), 'ORIONNEBULA')

    def test_solar_longitude_known_dates(self):
        # Perseids peak (solar longitude 140.0) falls on 12-13 August.
        jan1 = b.jd_from_datetime(dt.datetime(2026, 1, 1, tzinfo=dt.timezone.utc))
        peak = b.datetime_from_jd(b.jd_for_solar_longitude(140.0, jan1 + 220))
        self.assertEqual((peak.month, peak.day), (8, 13))
        # March equinox: longitude 0 around 20 March.
        eq = b.datetime_from_jd(b.jd_for_solar_longitude(0.0, jan1 + 78))
        self.assertEqual((eq.month, eq.day), (3, 20))

    def test_polylines_break_on_non_int(self):
        self.assertEqual(list(b.polylines([[1, 2, 'x', 3, 4]])), [[1, 2], [3, 4]])


@unittest.skipUnless(os.path.isdir(os.path.join(ST, 'nebulae')), 'Stellarium data not fetched')
class WithStellariumData(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        names = b.read_dso_names(os.path.join(ST, 'nebulae/default/names.dat'))
        cls.dso = b.read_dso_catalog(os.path.join(ST, 'nebulae/default/catalog.txt'), names)
        cls.by = {o['designations'][0]: o for o in cls.dso if o['designations']}

    def test_messier_objects_typed_and_named(self):
        expected = {'M31': 'Ga', 'M42': 'Ne', 'M44': 'Oc', 'M45': 'Oc', 'M13': 'Gc', 'M57': 'Ne', 'M20': 'Ne'}
        for m, t in expected.items():
            self.assertEqual(self.by[m]['t'], t, m)
        self.assertIn('Andromeda Galaxy', self.by['M31']['names'])
        self.assertIn('Orion Nebula', self.by['M42']['names'])

    def test_all_110_messier_present_with_type(self):
        found = {o['designations'][0] for o in self.dso if o['designations'] and o['designations'][0].startswith('M')
                 and o['designations'][0][1:].isdigit() and o['t']}
        missing = sorted({'M%d' % i for i in range(1, 111)} - found, key=lambda s: int(s[1:]))
        # M40 is a double star and M73 an asterism; neither is a DSO type the app draws.
        self.assertTrue(set(missing) <= {'M40', 'M73'}, missing)

    def test_m42_position(self):
        o = self.by['M42']
        self.assertAlmostEqual(o['ra'], 83.82, delta=0.1)
        self.assertAlmostEqual(o['dec'], -5.39, delta=0.1)

    def test_meteor_showers(self):
        s = b.meteor_showers(os.path.join(ST, 'plugins/MeteorShowers/resources/MeteorShowers.json'), [2026])
        by = {x['code']: x for x in s}
        self.assertTrue(by['GEM']['peak_utc'].startswith('2026-12-14'))
        self.assertTrue(by['QUA']['start_utc'].startswith('2025-12'))  # activity wraps the new year
        self.assertNotIn('ANT', by)


if __name__ == '__main__':
    unittest.main()
