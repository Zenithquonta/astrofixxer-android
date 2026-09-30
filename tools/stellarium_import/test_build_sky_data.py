"""Run: STELLARIUM_DIR=.cache/stellarium python3 -m unittest tools/stellarium_import/test_build_sky_data.py

The solver-star tests also need hygdata_v3.csv: set HYG_CSV (default .cache/hygdata_v3.csv)."""
import datetime as dt
import math
import os
import struct
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import build_sky_data as b  # noqa: E402

ST = os.environ.get('STELLARIUM_DIR', '.cache/stellarium')
HYG = os.environ.get('HYG_CSV', '.cache/hygdata_v3.csv')
STAR_DIR = os.path.join(ST, 'stars', 'hip_gaia3')


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

    def test_precession_b1875_to_j2000_matches_astropy(self):
        # Reference values from astropy's FK5 transform (B1875 equinox to J2000).
        for (ra, de), (ra2, de2) in [((343.0, 34.5), (344.46516, 35.16819)), ((82.5, -62.0), (82.80489, -61.91098))]:
            r, d = b.precess(ra, de, b.B1875)
            self.assertAlmostEqual(r, ra2, delta=0.0003)  # about 1 arcsecond
            self.assertAlmostEqual(d, de2, delta=0.0003)

    def test_m40_completes_the_messier_list(self):
        self.assertEqual([s['names'][0] for s in b.EXTRA_STARS], ['M40'])

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

    def test_dark_nebulae_have_no_magnitude(self):
        dark = [o for o in self.dso if o['stype'] in b.DARK_TYPES]
        self.assertTrue(dark)
        self.assertTrue(all(o['mag'] is None for o in dark))

    def test_m42_position(self):
        o = self.by['M42']
        self.assertAlmostEqual(o['ra'], 83.82, delta=0.1)
        self.assertAlmostEqual(o['dec'], -5.39, delta=0.1)

    def test_minor_bodies(self):
        bodies = b.minor_bodies(os.path.join(ST, 'data/ssystem_minor.ini'))
        by = {x['name']: x for x in bodies}
        self.assertNotIn('artificial', {x['type'] for x in bodies})
        comets = [x for x in bodies if x['type'] == 'comet']
        self.assertGreater(len(comets), 50)
        self.assertTrue(all({'q', 'e', 'tp_jd', 'h'} <= x.keys() for x in comets))
        ceres = by['Ceres']
        self.assertAlmostEqual(ceres['a'], 2.77, delta=0.02)

    def test_constellation_boundaries(self):
        edges = b.boundaries(b.read_skyculture(os.path.join(ST, 'skycultures/modern')))
        self.assertEqual(len(edges), 781)
        for e in edges:
            self.assertEqual(len(e['c']), 2)
            p = e['p']
            self.assertEqual(len(p) % 2, 0)
            self.assertTrue(all(0 <= p[i] < 360 and -90 <= p[i + 1] <= 90 for i in range(0, len(p), 2)))
        self.assertEqual(sum('LYR' in e['c'] for e in edges), 17)

    def test_meteor_showers(self):
        s = b.meteor_showers(os.path.join(ST, 'plugins/MeteorShowers/resources/MeteorShowers.json'), [2026])
        by = {x['code']: x for x in s}
        self.assertTrue(by['GEM']['peak_utc'].startswith('2026-12-14'))
        self.assertTrue(by['QUA']['start_utc'].startswith('2025-12'))  # activity wraps the new year
        self.assertNotIn('ANT', by)


class SolverFormat(unittest.TestCase):
    def test_round_trip_within_quantisation(self):
        import random
        rnd = random.Random(7)
        stars = [(rnd.uniform(0, 360), math.degrees(math.asin(rnd.uniform(-1, 1))), rnd.uniform(-1.4, 10.5))
                 for _ in range(3000)]
        stars += [(0.0, -90.0, 3.0), (359.99999, 90.0, 5.0), (180.0, 0.0, 10.5)]
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, 'solver_stars.bin')
            n, size = b.write_solver_stars(path, stars)
            self.assertEqual(n, len(stars))
            self.assertEqual(size, os.path.getsize(path))
            with open(path, 'rb') as f:
                self.assertEqual(f.read(4), b'AFSS')
            back = b.read_solver_stars(path)
        self.assertEqual(len(back), len(stars))
        # every original star has a decoded partner within half a quantisation step, and the magnitude within 0.03
        cells = {}
        for r, dc, m in back:
            cells.setdefault((int(r), int(dc + 90)), []).append((r, dc, m))
        for r, dc, m in stars:
            best = min(math.hypot(((r - r2 + 180) % 360 - 180) * math.cos(math.radians(dc)), dc - d2)
                       for r2, d2, _ in [c for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                                         for c in cells.get(((int(r) + dx) % 360, int(dc + 90) + dy), [])]) * 3600
            self.assertLess(best, 0.12)
        self.assertLessEqual(max(abs(m - m2) for m, m2 in
                                 zip(sorted(s[2] for s in stars), sorted(x[2] for x in back))), 0.026)

    def test_stars_fainter_than_the_limit_are_dropped(self):
        with tempfile.TemporaryDirectory() as d:
            n, _ = b.write_solver_stars(os.path.join(d, 'x.bin'), [(10, 10, 10.4), (10, 10.001, 10.6)])
        self.assertEqual(n, 1)


@unittest.skipUnless(os.path.isdir(STAR_DIR) and os.path.isfile(HYG), 'Stellarium stars/hip_gaia3 or HYG csv not available')
class SolverStars(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = {}
        for name in b.STELLARIUM_STAR_FILES:
            cls.files[name] = b.read_stellarium_star_file(os.path.join(STAR_DIR, name))
        cls.by_hip, cls.rows = b.read_hyg(HYG)
        cls.bright = cls.files[b.STELLARIUM_STAR_FILES[0]][1]

    def test_counts_match_headers_config_and_file_sizes(self):
        import json
        with open(os.path.join(STAR_DIR, 'defaultStarsConfig.json')) as f:
            cfg = {c['fileName']: c for c in json.load(f)['catalogs']}
        for name, (header, stars) in self.files.items():
            self.assertEqual(len(stars), header['count'], name)
            with open(os.path.join(STAR_DIR, name), 'rb') as f:
                zone_sum = sum(struct.unpack_from('<%dI' % header['zones'], f.read(), 28))
            self.assertEqual(zone_sum, header['count'], name)
            self.assertEqual(os.path.getsize(os.path.join(STAR_DIR, name)), 28 + 4 * header['zones'] + 48 * header['count'])
            # config counts are in millions, rounded to 2-3 digits
            expected = cfg[name]['count'] * 1e6
            self.assertAlmostEqual(header['count'] / expected, 1.0, delta=0.06, msg=name)
            lo, hi = cfg[name]['magRange']
            # Only Hipparcos stars may lie outside the file's magnitude range (stars_2 keeps faint HIP entries).
            outside = [s for s in stars if not lo - 0.05 <= s[2] <= hi + 0.05]
            self.assertLessEqual(sum(1 for s in outside if not s[3]), 3, name)

    def test_bright_stars_agree_with_hyg_at_epoch_2000(self):
        import collections
        grid = collections.defaultdict(list)
        for hip, r, d, m, _p, _bv in self.rows:
            grid[(int(r), int(d + 90))].append((r, d, m))
        seps = []
        for ra, de, m, _hip, _g in self.bright:
            if m >= 6.0:
                continue
            best = None
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for r, d, m2 in grid.get(((int(ra) + dx) % 360, int(de + 90) + dy), []):
                        s = math.hypot(((ra - r + 180) % 360 - 180) * math.cos(math.radians(de)), de - d) * 3600
                        if abs(m - m2) < 0.7 and (best is None or s < best):
                            best = s
            if best is not None and best < 60:
                seps.append(best)
        self.assertGreater(len(seps), 4500)
        seps.sort()
        print('HYG cross-match: %d stars, median %.3f", 99%% %.2f", max %.1f"' % (
            len(seps), seps[len(seps) // 2], seps[int(len(seps) * 0.99)], seps[-1]))
        self.assertLess(seps[len(seps) // 2], 1.0)
        self.assertLess(seps[int(len(seps) * 0.99)], 5.0)

    def test_proper_motion_is_applied(self):
        # Arcturus moves 2.3"/yr: without the 16-year step back to J2000 it would be ~37" off HYG.
        ra, de, m, _hip, _g = min((s for s in self.bright if s[3] == 69673), key=lambda s: s[2])
        r2, d2, m2, _ = self.by_hip[69673]
        sep = math.hypot(((ra - r2 + 180) % 360 - 180) * math.cos(math.radians(de)), de - d2) * 3600
        self.assertLess(sep, 2.0)
        moved = [s for s in b.read_stellarium_star_file(os.path.join(STAR_DIR, b.STELLARIUM_STAR_FILES[0]),
                                                        target_jd=b.STELLARIUM_STAR_EPOCH_JD)[1] if s[3] == 69673][0]
        self.assertGreater(math.hypot(((moved[0] - ra + 180) % 360 - 180) * math.cos(math.radians(de)), moved[1] - de) * 3600, 30.0)

    def test_named_stars(self):
        for name, hip in [('Vega', 91262), ('Sirius', 32349), ('Polaris', 11767), ('Betelgeuse', 27989)]:
            cand = [s for s in self.bright if s[3] == hip]
            self.assertTrue(cand, name)
            ra, de, m, _h, _g = min(cand, key=lambda s: s[2])
            r2, d2, m2, _ = self.by_hip[hip]
            self.assertAlmostEqual(m, m2, delta=0.1, msg=name)
            sep = math.hypot(((ra - r2 + 180) % 360 - 180) * math.cos(math.radians(de)), de - d2) * 3600
            self.assertLess(sep, 3.0, name)
        vega = [s for s in self.bright if s[3] == 91262][0]
        self.assertAlmostEqual(vega[2], 0.03, delta=0.1)
        self.assertAlmostEqual(vega[0], 279.2347, delta=0.001)  # 18h36m56.3s
        sirius = min((s for s in self.bright if s[3] == 32349), key=lambda s: s[2])
        self.assertAlmostEqual(sirius[2], -1.46, delta=0.1)

    def test_written_file_holds_every_star_up_to_10_5(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, 'solver_stars.bin')
            counts, n, size = b.build_solver_stars(STAR_DIR, path)
            expected = sum(1 for _h, stars in self.files.values() for s in stars if s[2] <= 10.5)
            self.assertEqual(n, expected)
            self.assertLess(size, 6 * 1024 * 1024)
            back = b.read_solver_stars(path)
        self.assertEqual(len(back), expected)
        self.assertLessEqual(max(m for _r, _d, m in back), 10.5 + 0.026)
        self.assertEqual(sum(counts.values()), sum(h['count'] for h, _s in self.files.values()))


if __name__ == '__main__':
    unittest.main()
