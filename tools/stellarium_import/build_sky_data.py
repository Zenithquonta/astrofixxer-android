#!/usr/bin/env python3
"""Build AstroFixxer sky data from Stellarium's open data files.

Inputs
  --stellarium  checkout made by fetch_stellarium.sh (DSO catalogue, DSO names,
                sky cultures, meteor showers)
  --hyg         hygdata_v3.csv (star positions; Stellarium's own star catalogues
                are binary downloads, so positions still come from HYG)

Outputs (under --out)
  web/jsdb_stellarium.js       same variables as the data block in astrofixxer.html
  android/sky_catalog.json.gz  full catalogue for the Android app
  events/meteor_showers.json   meteor shower calendar with dates for --years
"""
import argparse
import csv
import datetime as dt
import gzip
import json
import math
import os
import re
import subprocess

# The data uses SIMBAD-style codes (Gx, AGx, RNe, ...) as well as the ones in the file header.
DSO_TYPE_MAP = {
    **dict.fromkeys(['G', 'GX', 'Gx', 'AGx', 'rG', 'RG', 'IG', 'IG2', 'IG3', 'GiG', 'PoG',
                     'ClG', 'CGG', 'GxCL'], 'Ga'),
    'GC': 'Gc',
    **dict.fromkeys(['OC', 'OpC', 'CL', 'Cl', 'SA', 'SC'], 'Oc'),
    **dict.fromkeys(['C+N', 'NB', 'GNe', 'PN', 'PN?', 'PPN', 'PNB', 'DN', 'DNe', 'RN', 'RNe',
                     'HA', 'HII', 'SNR', 'SNRG', 'BN', 'EN', 'ISM', 'MoC', 'SFR', 'bub'], 'Ne'),
}
# Clusters with nebulosity are drawn as nebulae, except where the cluster is what people see.
TYPE_OVERRIDES = {'M45': 'Oc'}

# (catalog.txt column, names.dat prefix, display format, searchable in the web app)
DESIGNATIONS = [
    (18, 'M', 'M{}', True),
    (16, 'NGC', 'NGC{}', True),
    (17, 'IC', 'IC{}', True),
    (19, 'C', 'C{}', True),
    (20, 'B', 'B{}', True),
    (21, 'SH2', 'Sh2-{}', True),
    (26, 'CR', 'Cr{}', True),
    (27, 'MEL', 'Mel{}', True),
    (41, 'TR', 'Tr{}', True),
    (42, 'ST', 'St{}', True),
    (43, 'RU', 'Ru{}', True),
    (31, 'ARP', 'Arp{}', True),
    (36, 'ACO', 'Abell{}', True),
    (37, 'HCG', 'HCG{}', True),
    (22, 'VDB', 'vdB{}', False),
    (23, 'RCW', 'RCW{}', False),
    (24, 'LDN', 'LDN{}', False),
    (25, 'LBN', 'LBN{}', False),
    (28, 'PGC', 'PGC{}', False),
    (29, 'UGC', 'UGC{}', False),
    (30, 'CED', 'Ced{}', False),
    (32, 'VV', 'VV{}', False),
    (33, 'PK', 'PK{}', False),
    (34, 'PNG', 'PN G{}', False),
    (35, 'SNRG', 'SNR G{}', False),
    (38, 'ESO', 'ESO{}', False),
    (39, 'VDBH', 'vdBH{}', False),
    (40, 'DWB', 'DWB{}', False),
    (44, 'VDBHA', 'vdB-Ha{}', False),
]
CATALOG_COLUMNS = 45
WEB_TYPE_ORDER = ['Oc', 'Ga', 'Ne', 'Gc', 'P', 'Ca', 'S']
PLANETS = ['Moon', 'Mercury', 'Venus', 'Mars', 'Jupiter', 'Saturn', 'Neptune', 'Uranus']

_zeros = re.compile(r'^(.*)(([A-Z]+)[ 0]+)([^0].*)$')


def normalize_name(name):
    """Same rule as normalizeName() in astrofixxer.html, so search keys match."""
    name = name.upper()
    m = _zeros.match(name)
    if m:
        return normalize_name(m.group(1) + m.group(3) + m.group(4))
    return name


def _norm_id(value):
    value = ' '.join(value.split())
    if value in ('', '0'):
        return None
    return str(int(value)) if value.isdigit() else value


def _mag(v):
    v = float(v)
    return None if v >= 90 else v


# ---------------------------------------------------------------- DSO

def read_dso_names(path):
    """names.dat -> {(prefix, id): [common names]}; blank prefix means Stellarium's own id."""
    names = {}
    pat = re.compile(r'_\("(.*?)"\)')
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            prefix = line[0:5].strip()
            oid = _norm_id(line[5:20])
            m = pat.search(line[20:])
            if not oid or not m:
                continue
            names.setdefault((prefix, oid), []).append(m.group(1))
    return names


def read_dso_catalog(path, names):
    objects = []
    with open(path, encoding='utf-8') as f:
        for line in f:
            if line.startswith('#') or not line.strip():
                continue
            c = line.rstrip('\n').split('\t')
            c += [''] * (CATALOG_COLUMNS - len(c))
            stype = c[5].strip()
            designations, searchable, common = [], [], []
            keys = [('', _norm_id(c[0]))]
            for col, prefix, fmt, web in DESIGNATIONS:
                oid = _norm_id(c[col])
                if oid is None:
                    continue
                label = fmt.format(oid)
                designations.append(label)
                if web:
                    searchable.append(label)
                keys.append((prefix, oid))
            for k in keys:
                for n in names.get(k, []):
                    if n not in common:
                        common.append(n)
            vmag, bmag = _mag(c[4]), _mag(c[3])
            objects.append({
                'id': int(c[0]),
                'ra': float(c[1]),
                'dec': float(c[2]),
                'vmag': vmag,
                'bmag': bmag,
                'mag': vmag if vmag is not None else bmag,
                'stype': stype,
                't': TYPE_OVERRIDES.get(designations[0] if designations else None, DSO_TYPE_MAP.get(stype)),
                'major': float(c[7] or 0),
                'minor': float(c[8] or 0),
                'angle': int(float(c[9] or 0)),
                'designations': designations,
                'searchable': searchable,
                'names': common,
            })
    return objects


# ---------------------------------------------------------------- stars & constellations

def read_hyg(path):
    """-> {hip: (ra_deg, dec_deg, mag, proper_name)} and list of all rows with mag."""
    by_hip, rows = {}, []
    with open(path, encoding='utf-8') as f:
        for i, row in enumerate(csv.reader(f)):
            if i <= 1:  # header and the Sun
                continue
            ra, de, mag = float(row[7]) * 15.0, float(row[8]), float(row[13])
            hip = int(row[1]) if row[1] else None
            proper = row[6] or None
            if hip:
                by_hip[hip] = (ra, de, mag, proper)
            rows.append((hip, ra, de, mag, proper))
    return by_hip, rows


def read_skyculture(path):
    with open(os.path.join(path, 'index.json'), encoding='utf-8') as f:
        return json.load(f)


def hip_of(key):
    m = re.match(r'HIP (\d+)$', key)
    return int(m.group(1)) if m else None


def culture_star_names(culture):
    out = {}
    for key, entries in culture.get('common_names', {}).items():
        hip = hip_of(key)
        if hip:
            out[hip] = entries
    return out


def polylines(lines):
    """Stellarium lines are lists of HIP ids; anything that is not an int breaks the line."""
    for poly in lines:
        run = []
        for v in poly:
            if isinstance(v, int):
                run.append(v)
            else:
                yield run
                run = []
        yield run


def centre(points):
    sx = sy = sz = 0.0
    for ra, de in points:
        r, d = math.radians(ra), math.radians(de)
        sx += math.cos(d) * math.sin(r)
        sy += math.cos(d) * math.cos(r)
        sz += math.sin(d)
    n = math.sqrt(sx * sx + sy * sy + sz * sz)
    ra = math.degrees(math.atan2(sx / n, sy / n)) % 360
    return ra, math.degrees(math.asin(sz / n))


def constellations(culture, by_hip, culture_id):
    labels, segments, missing = [], [], set()
    for con in culture['constellations']:
        pts = set()
        for run in polylines(con.get('lines', [])):
            for a, b in zip(run, run[1:]):
                if a not in by_hip or b not in by_hip:
                    missing.update(h for h in (a, b) if h not in by_hip)
                    continue
                r0, d0 = by_hip[a][:2]
                r1, d1 = by_hip[b][:2]
                segments.append({'r0': r0, 'd0': d0, 'r1': r1, 'd1': d1})
                pts.update([(r0, d0), (r1, d1)])
        if not pts:
            continue
        cn = con.get('common_name', {})
        if culture_id == 'indian':
            label = cn.get('pronounce') or cn.get('english') or cn.get('native')
            extra = [x for x in (cn.get('native'), cn.get('english')) if x and x != label]
        else:
            label = cn.get('native') or cn.get('english')
            extra = [x for x in (cn.get('english'),) if x and x != label]
        ra, de = centre(pts)
        labels.append({'id': con['id'], 'RA': ra, 'DE': de, 'name': label, 'n2': extra,
                       'lines': con.get('lines', [])})
    return labels, segments, missing


# ---------------------------------------------------------------- web output

def _fmt(v):
    if isinstance(v, float):
        return ('%.4f' % v).rstrip('0').rstrip('.')
    if isinstance(v, list):
        return '[' + ','.join(_fmt(x) for x in v) + ']'
    if isinstance(v, dict):
        return '{' + ','.join(json.dumps(k) + ':' + _fmt(x) for k, x in v.items()) + '}'
    return json.dumps(v, ensure_ascii=False)


def build_web(dsos, stars_rows, star_names, indian_names, con_labels, segments,
              dso_mag_limit, star_mag_limit, indian_star_names):
    blocks = {t: [] for t in WEB_TYPE_ORDER}
    search = {t: [] for t in WEB_TYPE_ORDER}

    for o in dsos:
        if o['t'] is None or o['mag'] is None or o['mag'] > dso_mag_limit or not o['designations']:
            continue
        e = {'RA': o['ra'], 'DE': o['dec'], 'AM': o['mag'], 'name': o['designations'][0], 't': o['t']}
        if o['major'] > 0:
            e['s'] = o['major']
        if o['names']:
            e['n2'] = o['names']
        blocks[o['t']].append(e)
        search[o['t']].append(o['names'] + o['searchable'][1:])

    for name in PLANETS:
        blocks['P'].append({'DE': -1, 'RA': -1, 'AM': -1, 'name': name, 't': 'P'})
        search['P'].append([])

    for c in con_labels:
        blocks['Ca'].append({'DE': c['DE'], 'RA': c['RA'], 'AM': -1, 'name': c['name'], 't': 'Ca'})
        search['Ca'].append([])

    for hip, ra, de, mag, proper in stars_rows:
        if mag > star_mag_limit:
            continue
        e = {'DE': de, 'RA': ra, 'AM': mag, 't': 'S'}
        alt = []
        entries = star_names.get(hip, []) if hip else []
        primary = (entries[0].get('english') or entries[0].get('native')) if entries else proper
        if primary:
            e['name'] = primary
        for x in entries[1:]:
            n = x.get('english') or x.get('native')
            if n and n != primary and n not in alt:
                alt.append(n)
        n2 = []
        if indian_star_names and hip in indian_names:
            for x in indian_names[hip]:
                label = x.get('pronounce') or x.get('english')
                n2.append('%s (%s)' % (label, x['native']) if x.get('native') and label else (label or x.get('native')))
        if n2:
            e['n2'] = n2
        blocks['S'].append(e)
        search['S'].append(alt + [x.get('pronounce') for x in indian_names.get(hip, [])
                                  if indian_star_names and x.get('pronounce')])

    allstars, index, names, poss = [], {}, [], []
    for t in WEB_TYPE_ORDER:
        order = sorted(range(len(blocks[t])), key=lambda i: blocks[t][i]['AM'])
        for i in order:
            pos = len(allstars)
            e = blocks[t][i]
            allstars.append(e)
            if t == 'Ca':
                continue
            seen = set()
            for n in [e.get('name')] + e.get('n2', []) + search[t][i]:
                if not n:
                    continue
                key = normalize_name(n)
                if key in seen:
                    continue
                seen.add(key)
                names.append(key)
                poss.append(pos)
        index[t] = len(allstars)
    index['U'] = len(allstars)
    return allstars, index, {'names': names, 'index': poss}, segments


def write_web_js(path, allstars, index, nindex, segments, source_note):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write('// Generated by tools/stellarium_import/build_sky_data.py - do not edit.\n')
        f.write('// ' + source_note + '\n')
        f.write("// types: 'S' star, 'Ca' constellation, 'Oc' open cluster, 'Gc' globular cluster, "
                "'Ga' galaxy, 'Ne' nebula, 'P' planet\n")
        f.write('var allstars_index = ' + json.dumps(index) + ';\n')
        f.write('var allstars = ' + _fmt(allstars) + ';\n')
        f.write('var constellation_lines = ' + _fmt(segments) + ';\n')
        f.write('var allstars_index_name = ' + json.dumps(nindex, ensure_ascii=False) + ';\n')
        f.write('var allstars_db_specs={"items":%d,"index_size":%d,"index_name_size":%d};\n'
                % (len(allstars), len(index), len(nindex['names'])))


def apply_to_html(html_in, js_path, html_out):
    """Swap the data block (var allstars_index ... var allstars_db_specs) in astrofixxer.html."""
    with open(html_in, encoding='utf-8') as f:
        lines = f.readlines()
    start = next(i for i, l in enumerate(lines) if l.startswith('var allstars_index ='))
    end = next(i for i, l in enumerate(lines) if l.startswith('var allstars_db_specs'))
    with open(js_path, encoding='utf-8') as f:
        block = [l for l in f.readlines() if l.startswith('var ')]
    with open(html_out, 'w', encoding='utf-8') as f:
        f.writelines(lines[:start] + block + lines[end + 1:])


# ---------------------------------------------------------------- meteor showers

def jd_from_datetime(t):
    return t.timestamp() / 86400.0 + 2440587.5


def datetime_from_jd(jd):
    return dt.datetime.fromtimestamp((jd - 2440587.5) * 86400.0, tz=dt.timezone.utc)


def solar_longitude_j2000(jd):
    """Sun's geometric longitude referred to the J2000 ecliptic, degrees (Meeus ch. 25, ~0.01 deg)."""
    t = (jd - 2451545.0) / 36525.0
    l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
    m = math.radians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
    c = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * math.sin(m)
         + (0.019993 - 0.000101 * t) * math.sin(2 * m) + 0.000289 * math.sin(3 * m))
    year = 2000.0 + t * 100.0
    return (l0 + c - 0.01397 * (year - 2000.0)) % 360.0


def jd_for_solar_longitude(target, guess_jd):
    jd = guess_jd
    for _ in range(6):
        diff = (target - solar_longitude_j2000(jd) + 180.0) % 360.0 - 180.0
        jd += diff / 0.98564736
        if abs(diff) < 1e-6:
            break
    return jd


def _iso(jd):
    t = datetime_from_jd(jd) + dt.timedelta(minutes=30)
    return t.replace(minute=0, second=0, microsecond=0).strftime('%Y-%m-%dT%H:00Z')


def meteor_showers(path, years):
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    out = []
    for code, s in data['showers'].items():
        generic = next((a for a in s['activity'] if a['year'] == 'generic'), None)
        if not generic or (generic['finish'] - generic['start']) % 360 == 0:
            continue  # no generic activity, or a year-round source (Antihelion)
        for year in years:
            spec = next((a for a in s['activity'] if a['year'] == str(year)), {})
            peak_l = spec.get('peak', generic['peak'])
            jan1 = jd_from_datetime(dt.datetime(year, 1, 1, tzinfo=dt.timezone.utc))
            guess = jan1 + ((peak_l - solar_longitude_j2000(jan1)) % 360.0) / 0.98564736
            peak = jd_for_solar_longitude(peak_l, guess)
            start = jd_for_solar_longitude(
                generic['start'], peak - ((peak_l - generic['start']) % 360.0) / 0.98564736)
            end = jd_for_solar_longitude(
                generic['finish'], peak + ((generic['finish'] - peak_l) % 360.0) / 0.98564736)
            zhr = spec.get('zhr', generic['zhr'])
            variable = spec.get('variable', generic.get('variable')) if zhr == -1 else None
            out.append({
                'code': code,
                'name': s['designation'],
                'iau_number': s.get('IAUNo'),
                'year': year,
                'peak_utc': _iso(peak),
                'start_utc': _iso(start),
                'end_utc': _iso(end),
                'peak_solar_longitude': peak_l,
                'zhr': None if zhr == -1 else zhr,
                'zhr_variable': variable,
                'speed_km_s': s.get('speed'),
                'radiant_ra': s.get('radiantAlpha'),
                'radiant_dec': s.get('radiantDelta'),
                'drift_ra_per_deg_sol_long': s.get('driftAlpha'),
                'drift_dec_per_deg_sol_long': s.get('driftDelta'),
                'population_index': s.get('pidx'),
                'parent_body': s.get('parentObj'),
            })
    out.sort(key=lambda e: e['peak_utc'])
    return out


# ---------------------------------------------------------------- main

def git_rev(path):
    try:
        return subprocess.check_output(['git', '-C', path, 'rev-parse', '--short', 'HEAD'],
                                       text=True, stderr=subprocess.DEVNULL).strip()
    except (OSError, subprocess.CalledProcessError):
        return 'unknown'


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--stellarium', required=True)
    ap.add_argument('--hyg', required=True)
    ap.add_argument('--out', default='data')
    ap.add_argument('--sky-culture', choices=['modern', 'indian'], default='modern')
    ap.add_argument('--dso-mag-limit', type=float, default=14.0)
    ap.add_argument('--star-mag-limit', type=float, default=6.0)
    ap.add_argument('--no-indian-star-names', action='store_true')
    ap.add_argument('--years', default=None, help='e.g. 2026-2028 (default: this year and next two)')
    ap.add_argument('--apply-to', help='astrofixxer.html to read')
    ap.add_argument('--apply-out', help='where to write the patched html (default: next to --out web js)')
    args = ap.parse_args()

    st = args.stellarium
    rev = git_rev(st)
    names = read_dso_names(os.path.join(st, 'nebulae/default/names.dat'))
    dsos = read_dso_catalog(os.path.join(st, 'nebulae/default/catalog.txt'), names)
    by_hip, star_rows = read_hyg(args.hyg)
    modern = read_skyculture(os.path.join(st, 'skycultures/modern'))
    indian = read_skyculture(os.path.join(st, 'skycultures/indian'))
    culture = modern if args.sky_culture == 'modern' else indian
    con_labels, segments, missing = constellations(culture, by_hip, args.sky_culture)
    star_names = culture_star_names(modern)
    indian_names = culture_star_names(indian)

    note = ('Stellarium %s: DSO catalogue + names (GPL-2.0-or-later), sky culture "%s" (CC BY-SA 4.0); '
            'star positions: HYG v3 (CC BY-SA)' % (rev, args.sky_culture))
    allstars, index, nindex, segs = build_web(
        dsos, star_rows, star_names, indian_names, con_labels, segments,
        args.dso_mag_limit, args.star_mag_limit, not args.no_indian_star_names)
    web_js = os.path.join(args.out, 'web', 'jsdb_stellarium.js')
    write_web_js(web_js, allstars, index, nindex, segs, note)

    android = {
        'source': {'stellarium_commit': rev, 'note': note},
        'dso': [{k: v for k, v in o.items() if k != 'searchable'} for o in dsos if o['t']],
        'stars': [
            {'hip': h, 'ra': ra, 'dec': de, 'mag': mag,
             'names': [x.get('english') or x.get('native') for x in star_names.get(h, [])] or ([p] if p else []),
             'indian_names': indian_names.get(h, [])}
            for h, ra, de, mag, p in star_rows if mag <= args.star_mag_limit + 0.5
        ],
        'constellations': {
            cid: [{k: c[k] for k in ('id', 'name', 'n2', 'RA', 'DE', 'lines')}
                  for c in constellations(cult, by_hip, cid)[0]]
            for cid, cult in (('modern', modern), ('indian', indian))
        },
    }
    os.makedirs(os.path.join(args.out, 'android'), exist_ok=True)
    with gzip.open(os.path.join(args.out, 'android', 'sky_catalog.json.gz'), 'wt', encoding='utf-8') as f:
        json.dump(android, f, ensure_ascii=False, separators=(',', ':'))

    if args.years:
        a, _, b = args.years.partition('-')
        years = list(range(int(a), int(b or a) + 1))
    else:
        y = dt.date.today().year
        years = [y, y + 1, y + 2]
    showers = meteor_showers(os.path.join(st, 'plugins/MeteorShowers/resources/MeteorShowers.json'), years)
    os.makedirs(os.path.join(args.out, 'events'), exist_ok=True)
    with open(os.path.join(args.out, 'events', 'meteor_showers.json'), 'w', encoding='utf-8') as f:
        json.dump({'source': 'Stellarium %s plugins/MeteorShowers (GPL-2.0-or-later)' % rev,
                   'times': 'UTC, rounded to the hour; peak dates are typical and can shift by a day',
                   'years': years, 'showers': showers}, f, ensure_ascii=False, indent=1)

    if args.apply_to:
        out_html = args.apply_out or os.path.join(args.out, 'web', 'astrofixxer_stellarium.html')
        apply_to_html(args.apply_to, web_js, out_html)
        print('patched html:', out_html)

    counts = {t: index[t] - (index[WEB_TYPE_ORDER[i - 1]] if i else 0) for i, t in enumerate(WEB_TYPE_ORDER)}
    print('stellarium', rev, '| web objects', len(allstars), counts, '| search keys', len(nindex['names']))
    print('android dso', len(android['dso']), 'stars', len(android['stars']),
          '| constellation stars missing from HYG:', len(missing))
    print('meteor shower entries', len(showers), 'for', years)


if __name__ == '__main__':
    main()
