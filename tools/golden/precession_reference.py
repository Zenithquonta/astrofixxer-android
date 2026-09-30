# Writes app/src/test/resources/precession_reference.json: astropy's altitude/azimuth for bright stars, used by
# PrecessionTest to check Pointing.rayFromPos against a full IAU 2006/2000A pipeline (ICRS -> AltAz, no refraction).
# Run from the repo root (needs astropy; IERS downloads are not needed):
#   python3 tools/golden/precession_reference.py
import json
import warnings

import astropy.units as u
from astropy.coordinates import AltAz, EarthLocation, SkyCoord
from astropy.time import Time
from astropy.utils import iers

warnings.filterwarnings("ignore")
iers.conf.auto_download = False
iers.conf.iers_degraded_accuracy = "warn"

# J2000 RA/Dec in degrees (proper motion ignored on both sides of the comparison).
STARS = {
    "Vega": (279.2347, 38.7837), "Polaris": (37.9546, 89.2641), "Sirius": (101.2872, -16.7161),
    "Canopus": (95.9880, -52.6957), "Deneb": (310.3580, 45.2803), "Achernar": (24.4285, -57.2368),
    "Arcturus": (213.9153, 19.1824), "Betelgeuse": (88.7929, 7.4071), "Rigel": (78.6345, -8.2016),
    "Altair": (297.6958, 8.8683), "Antares": (247.3519, -26.4320), "Fomalhaut": (344.4128, -29.6222),
}
DATES = ["2000-01-01T12:00:00", "2026-09-28T16:00:00", "2030-12-01T18:00:00", "2040-06-15T21:00:00"]
SITES = {"New Delhi": (28.6139, 77.2090), "Sydney": (-33.87, 151.21), "Tromso": (69.65, 18.96)}

cases = []
for iso in DATES:
    t = Time(iso, scale="utc")
    for site, (lat, lon) in SITES.items():
        frame = AltAz(obstime=t, location=EarthLocation(lat=lat * u.deg, lon=lon * u.deg), pressure=0)
        for name, (ra, dec) in STARS.items():
            aa = SkyCoord(ra * u.deg, dec * u.deg, frame="icrs").transform_to(frame)
            if aa.alt.deg < 5:
                continue
            cases.append({
                "star": name, "ra": ra, "dec": dec, "date": iso + "Z", "timeMillis": int(round(t.unix * 1000)),
                "site": site, "lat": lat, "lon": lon, "altDeg": float(aa.alt.deg), "azDeg": float(aa.az.deg),
            })

out = "app/src/test/resources/precession_reference.json"
with open(out, "w") as f:
    json.dump({"source": "astropy ICRS -> AltAz, pressure=0; tools/golden/precession_reference.py", "cases": cases}, f, indent=1)
    f.write("\n")
print(len(cases), "cases written to", out)
