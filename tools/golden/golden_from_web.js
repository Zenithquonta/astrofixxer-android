// Runs the web app's own astronomy code in Node and writes golden values for the Android port's tests.
// Usage: node tools/golden/golden_from_web.js web/astrofixxer.html > golden.json
const fs = require('fs');
const vm = require('vm');

const html = fs.readFileSync(process.argv[2], 'utf8');

function extractBlock(startMarker) {
  const start = html.indexOf(startMarker);
  if (start < 0) throw new Error('not found: ' + startMarker);
  let depth = 0;
  for (let i = html.indexOf('{', start); i < html.length; i++) {
    if (html[i] === '{') depth++;
    else if (html[i] === '}' && --depth === 0) return html.slice(start, i + 1);
  }
  throw new Error('unbalanced: ' + startMarker);
}

const classes = ['class vsop87a_xsmall{', 'class vsop87a_milli_velocities{', 'class CPReduce {', 'class Vec {', 'class JulianDate {'];
const funcs = ['rayFromPos', 'getRotationMatrix', 'mvec', 'crossProd', 'normV', 'matMul', 'matAdd', 'matEye',
  'getCameraRays', 'selectAlign', 'cameraBearing', 'sprod'];
let src = classes.map(extractBlock).join('\n');
src += html.slice(html.indexOf('CPReduce.SUN = 0;'), html.indexOf('class Vec {'));
src += '\nvar vsop87a_full = vsop87a_xsmall; var vsop87a_full_velocities = vsop87a_milli_velocities;\n';
src += 'var degtorad = Math.PI / 180;\n';
src += funcs.map(f => extractBlock('function ' + f + '(')).join('\n');
src += `
var gdata, global_use_gyro = false, global_align_matrix = [1,0,0,0,1,0,0,0,1], global_align_index, global_expecting_select;
var allstars = [], RA, DE;
function setUseGyro(v) { global_use_gyro = v; }
function selectTarget() {}
this.api = { CPReduce, JulianDate, rayFromPos, getRotationMatrix, getCameraRays, selectAlign, cameraBearing,
  setState: s => { gdata = s; global_use_gyro = false; global_align_matrix = [1,0,0,0,1,0,0,0,1]; },
  setStars: s => { allstars = s; }, useGyro: v => { global_use_gyro = v; }, alignMatrix: () => global_align_matrix };
`;
const ctx = {};
vm.createContext(ctx);
vm.runInContext(src, ctx);
const A = ctx.api;

const places = { Delhi: [28.6139, 77.2090], Bengaluru: [12.9716, 77.5946], Leh: [34.1526, 77.5771] };
const dates = [[2026, 1, 1, 0, 0, 0], [2026, 6, 21, 18, 30, 0], [2024, 8, 12, 22, 0, 0], [2027, 3, 15, 2, 0, 0], [2030, 12, 31, 12, 0, 0]];
const bodies = [0, 1, 2, 4, 5, 6, 7, 8, 10];
const d2r = Math.PI / 180;

const reduce = [];
for (const [name, [lat, lon]] of Object.entries(places))
  for (const d of dates) {
    const jd = A.JulianDate.gregorianDateToJulianDate(...d);
    for (const body of bodies)
      reduce.push({ place: name, lat, lon, date: d, jd, body, result: A.CPReduce.reduce(body, jd, [lat * d2r, lon * d2r, 0]) });
  }

const rays = [];
const stars = [[101.287, -16.716], [279.234, 38.784], [37.954, 89.264], [83.822, -5.391], [213.915, 19.182]];
const time = Date.UTC(2026, 8, 28, 16, 0, 0);
for (const [lat, lon] of Object.values(places))
  for (const [ra, de] of stars) {
    A.setState({ lat, lon, time }); // rayFromPos reads location and time from the web app's global state
    rays.push({ ra, de, time, lat, lon, ray: A.rayFromPos(ra, de) });
  }

const rotations = [[0, 0, 0], [30, 45, 10], [200, -20, 80], [359, 89, -60]].map(([a, b, g]) =>
  ({ alpha: a, beta: b, gamma: g, matrix: A.getRotationMatrix(a, b, g) }));

// Full alignment flow: device orientation -> align on a star -> camera bearing of a target.
// The phone is pointed a few degrees off a star that is up over Delhi, as a user would before tapping Align.
// For the W3C ZXY matrix the forward axis has azimuth -alpha and altitude beta; gamma only rolls around it.
const alignment = [];
const cases = [
  { star: [279.234, 38.784], target: [283.396, 33.029], off: [8, -5], gamma: 5 },    // Vega -> M57
  { star: [297.696, 8.868], target: [282.767, -6.270], off: [-12, 6], gamma: -20 },  // Altair -> M11
  { star: [310.358, 45.280], target: [314.70, 44.33], off: [3, 10], gamma: 0 },      // Deneb -> NGC7000
];
for (const c of cases) {
  A.setState({ lat: 28.6139, lon: 77.2090, time });
  const r = A.rayFromPos(c.star[0], c.star[1]);
  const az = Math.atan2(r[0], r[1]) / d2r, alt = Math.asin(r[2]) / d2r;
  c.alpha = ((-(az + c.off[0])) % 360 + 360) % 360;
  c.beta = alt + c.off[1];
  const state = { lat: 28.6139, lon: 77.2090, time, alpha: c.alpha, alpha_user_offset: 0, alpha_gyro: c.alpha, alpha_diff: 0, beta: c.beta, gamma: c.gamma };
  A.setState(state);
  const uncorrected = A.getCameraRays();
  A.setStars([{ RA: c.star[0], DE: c.star[1] }]);
  A.selectAlign(0);
  const matrix = A.alignMatrix();
  A.useGyro(true);
  const corrected = A.getCameraRays();
  const starBearing = A.cameraBearing(c.star[0], c.star[1], corrected);
  const targetBearing = A.cameraBearing(c.target[0], c.target[1], corrected);
  alignment.push({ ...c, lat: state.lat, lon: state.lon, time, uncorrected, matrix, corrected, starBearing, targetBearing });
}

process.stdout.write(JSON.stringify({ source: 'web/astrofixxer.html', reduce, rays, rotations, alignment }, null, 1));
