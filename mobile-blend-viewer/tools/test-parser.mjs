// Parser regression test against the embedded sample scene (Blender 5.0, zstd).
// Expected values were read from Blender itself when the sample was generated.
//   npm install && npm test          (optionally: npm test -- path/to/other.blend)
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { decompress as zstdDecompress } from 'fzstd';
import { parseBlend } from '../src/blend-parser.js';
import { SAMPLE_BASE64 } from '../src/sample-blend.js';

const EXPECTED = {
  // name: [world x, y, z, vertices, faces, material slots]
  Suzanne: [0, 0, 1, 507, 500, 1],
  Antenna: [0, 0, 0.9, 24, 14, 0],
  Crate: [2.2, 0, 0.5, 8, 6, 2],
  Ring: [-2.3, 0, 0.4, 576, 576, 1],
  Floor: [0, 0, 0, 4, 1, 0],
  Heptagon: [0, 2.5, 0.01, 7, 1, 0],
  Camera: [7, -7, 5],
  Sun: [3, 3, 6],
};

const r = await parseBlend(Buffer.from(SAMPLE_BASE64, 'base64'), { zstdDecompress });
assert.equal(r.version, '5.0');
assert.equal(r.compression, 'zstd');
assert.equal(r.objects.length, Object.keys(EXPECTED).length);
for (const o of r.objects) {
  const e = EXPECTED[o.name];
  assert.ok(e, `unexpected object ${o.name}`);
  [12, 13, 14].forEach((k, i) => assert.ok(Math.abs(o.matrix[k] - e[i]) < 1e-4, `${o.name} world position`));
  if (e.length > 3) {
    assert.equal(o.mesh.vertCount, e[3], `${o.name} vertices`);
    assert.equal(o.mesh.faceCount, e[4], `${o.name} faces`);
    assert.equal(o.mesh.materials.length, e[5], `${o.name} material slots`);
    assert.ok(o.mesh.indices.every((i) => i < o.mesh.vertCount), `${o.name} indices in range`);
  }
}
const crate = r.objects.find((o) => o.name === 'Crate').mesh;
assert.deepEqual(crate.groups.map((g) => [g.materialIndex, g.count / 3]), [[0, 6], [1, 6]], 'Crate per-face materials');
assert.equal(r.objects.find((o) => o.name === 'Suzanne').mesh.smoothRatio, 1, 'Suzanne saved smooth');
assert.equal(crate.smoothRatio, 0, 'Crate saved flat');
assert.equal(r.objects.find((o) => o.name === 'Antenna').parent, 'Suzanne');
console.log(`sample scene: ${r.objects.length} objects OK`);

// Any extra files given on the command line must at least parse.
for (const f of process.argv.slice(2)) {
  const x = await parseBlend(fs.readFileSync(f), { zstdDecompress });
  const meshes = x.objects.filter((o) => o.mesh);
  console.log(`${f}: Blender ${x.version}, ${x.objects.length} objects, ${meshes.length} meshes`);
}
