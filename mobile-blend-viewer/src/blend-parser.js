// blend-parser.js - read-only .blend reader for previewing Blender scenes.
//
// Pure JS, no DOM and no three.js dependency, so it runs in browsers and Node.
// Reads the file's own SDNA (struct catalogue) instead of hard-coding offsets,
// which is what keeps it working across Blender versions.
//
// Scope (preview, not a full importer):
//   - File formats: legacy header (Blender 2.5x-4.x, 32/64-bit, LE/BE) and the
//     Blender 5.0 "large" header; gzip (pre-3.0) and zstd (3.0+) compression.
//   - Mesh objects: base mesh (modifiers are NOT evaluated), world transforms,
//     parenting, per-face material slots with the material's viewport colour.
//   - Mesh layouts: MFace (<2.63), MVert/MPoly/MLoop (2.63-3.x) and generic
//     attributes (position / .corner_vert / face offsets, 3.5+).
//   - Embedded file-browser thumbnail and an ID-block inventory.

const TEXT = new TextDecoder('latin1');

const ID_CODES = {
  SC: 'Scenes', OB: 'Objects', ME: 'Meshes', MA: 'Materials', TE: 'Textures',
  IM: 'Images', CA: 'Cameras', LA: 'Lights', WO: 'Worlds', CU: 'Curves',
  CV: 'Curves (hair)', PT: 'Point clouds', VO: 'Volumes', AR: 'Armatures',
  AC: 'Actions', NT: 'Node groups', GR: 'Collections', LI: 'Linked libraries',
  GD: 'Grease Pencil (legacy)', GP: 'Grease Pencil', MB: 'Metaballs',
  LT: 'Lattices', SO: 'Sounds', VF: 'Fonts', BR: 'Brushes', KE: 'Shape keys',
};

// Object.type values from DNA_object_types.h.
const OB_TYPES = {
  0: 'Empty', 1: 'Mesh', 2: 'Curve', 3: 'Surface', 4: 'Text', 5: 'Metaball',
  10: 'Light', 11: 'Camera', 12: 'Speaker', 13: 'Light probe', 22: 'Lattice',
  25: 'Armature', 26: 'Grease Pencil', 27: 'Curves', 28: 'Point cloud',
  29: 'Volume', 30: 'Grease Pencil',
};

const TYPE_READERS = {
  char: ['getUint8', 1], uchar: ['getUint8', 1], int8_t: ['getInt8', 1], uint8_t: ['getUint8', 1],
  short: ['getInt16', 2], ushort: ['getUint16', 2], int16_t: ['getInt16', 2], uint16_t: ['getUint16', 2],
  int: ['getInt32', 4], uint: ['getUint32', 4], int32_t: ['getInt32', 4], uint32_t: ['getUint32', 4],
  float: ['getFloat32', 4], double: ['getFloat64', 8],
  int64_t: ['getBigInt64', 8], uint64_t: ['getBigUint64', 8],
};

export class BlendError extends Error {}

// ---------------------------------------------------------------------------
// Decompression

async function gunzip(u8) {
  if (typeof DecompressionStream === 'undefined') {
    throw new BlendError('This browser cannot decompress gzip .blend files.');
  }
  const stream = new Blob([u8]).stream().pipeThrough(new DecompressionStream('gzip'));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

// Blender writes zstd in the "seekable" format: independent frames followed by
// a skippable frame holding a seek table. Decoding frame by frame avoids relying
// on the decoder's handling of skippable frames.
function zstdSeekTable(u8) {
  const n = u8.length;
  if (n < 17) return null;
  const dv = new DataView(u8.buffer, u8.byteOffset, n);
  if (dv.getUint32(n - 4, true) !== 0x8f92eab1) return null;
  const numFrames = dv.getUint32(n - 9, true);
  const entry = (u8[n - 5] & 0x80) ? 12 : 8;
  const tableStart = n - 9 - numFrames * entry;
  if (tableStart < 0) return null;
  const frames = [];
  let off = 0;
  for (let i = 0; i < numFrames; i++) {
    const c = dv.getUint32(tableStart + i * entry, true);
    const d = dv.getUint32(tableStart + i * entry + 4, true);
    frames.push({ off, c, d });
    off += c;
  }
  return frames;
}

function unzstd(u8, zstdDecompress) {
  if (!zstdDecompress) throw new BlendError('zstd decoder not available.');
  const frames = zstdSeekTable(u8);
  if (!frames) return zstdDecompress(u8);
  const total = frames.reduce((s, f) => s + f.d, 0);
  const out = new Uint8Array(total);
  let pos = 0;
  for (const f of frames) {
    const part = zstdDecompress(u8.subarray(f.off, f.off + f.c));
    out.set(part, pos);
    pos += part.length;
  }
  return pos === total ? out : out.subarray(0, pos);
}

export async function decompress(input, { zstdDecompress } = {}) {
  const u8 = input instanceof Uint8Array ? input : new Uint8Array(input);
  if (u8[0] === 0x1f && u8[1] === 0x8b) return { bytes: await gunzip(u8), compression: 'gzip' };
  if (u8[0] === 0x28 && u8[1] === 0xb5 && u8[2] === 0x2f && u8[3] === 0xfd) {
    return { bytes: unzstd(u8, zstdDecompress), compression: 'zstd' };
  }
  return { bytes: u8, compression: null };
}

// ---------------------------------------------------------------------------
// Low-level file structure

function ascii(u8, a, b) {
  return TEXT.decode(u8.subarray(a, b));
}

function readHeader(u8) {
  if (ascii(u8, 0, 7) !== 'BLENDER') {
    throw new BlendError('Not a .blend file (missing BLENDER header).');
  }
  const c = String.fromCharCode(u8[7]);
  if (c === '_' || c === '-') {
    const v = ascii(u8, 9, 12);
    return {
      size: 12, ptrSize: c === '-' ? 8 : 4, little: u8[8] === 0x76 /* v */,
      large: false, version: `${v[0]}.${Number(v.slice(1))}`, versionCode: Number(v),
    };
  }
  // Blender 5.0+: "BLENDER" + header size (2 digits) + "-" + format (2) + endian + version (4).
  const headerSize = Number(ascii(u8, 7, 9));
  const format = Number(ascii(u8, 10, 12));
  if (!Number.isFinite(headerSize) || format !== 1) {
    throw new BlendError(`Unsupported .blend header format "${ascii(u8, 0, 17)}".`);
  }
  const v = ascii(u8, 13, 17);
  return {
    size: headerSize, ptrSize: 8, little: u8[12] === 0x76, large: true,
    version: `${Number(v.slice(0, 2))}.${Number(v.slice(2))}`, versionCode: Number(v),
  };
}

function ptrKey(dv, off, ptrSize, little) {
  if (ptrSize === 4) {
    const lo = dv.getUint32(off, little);
    return lo === 0 ? null : lo.toString(16);
  }
  const a = dv.getUint32(off, little);
  const b = dv.getUint32(off + 4, little);
  const lo = little ? a : b;
  const hi = little ? b : a;
  if (lo === 0 && hi === 0) return null;
  return hi.toString(16) + lo.toString(16).padStart(8, '0');
}

function readBlocks(u8, dv, h) {
  const blocks = [];
  let p = h.size;
  const L = h.little;
  while (p + 8 <= u8.length) {
    const code = ascii(u8, p, p + 4).replace(/\0+$/, '');
    let len, old, sdna, nr, hsize;
    if (h.large) {
      sdna = dv.getInt32(p + 4, L);
      old = ptrKey(dv, p + 8, 8, L);
      len = Number(dv.getBigInt64(p + 16, L));
      nr = Number(dv.getBigInt64(p + 24, L));
      hsize = 32;
    } else {
      len = dv.getInt32(p + 4, L);
      old = ptrKey(dv, p + 8, h.ptrSize, L);
      sdna = dv.getInt32(p + 8 + h.ptrSize, L);
      nr = dv.getInt32(p + 12 + h.ptrSize, L);
      hsize = 16 + h.ptrSize;
    }
    if (code === 'ENDB') break;
    const off = p + hsize;
    if (len < 0 || off + len > u8.length) throw new BlendError('File is truncated or corrupt.');
    blocks.push({ code, off, len, old, sdna, nr });
    p = off + len;
  }
  return blocks;
}

function parseSDNA(u8, dv, block, h) {
  const L = h.little;
  const start = block.off;
  let p = start;
  const tag = () => { const t = ascii(u8, p, p + 4); p += 4; return t; };
  const align = () => { p = start + ((p - start + 3) & ~3); };
  const cstrings = (n) => {
    const out = new Array(n);
    for (let i = 0; i < n; i++) {
      let e = p;
      while (u8[e] !== 0) e++;
      out[i] = ascii(u8, p, e);
      p = e + 1;
    }
    return out;
  };
  if (tag() !== 'SDNA' || tag() !== 'NAME') throw new BlendError('Invalid SDNA block.');
  const nNames = dv.getInt32(p, L); p += 4;
  const names = cstrings(nNames);
  align();
  if (tag() !== 'TYPE') throw new BlendError('Invalid SDNA TYPE section.');
  const nTypes = dv.getInt32(p, L); p += 4;
  const types = cstrings(nTypes);
  align();
  if (tag() !== 'TLEN') throw new BlendError('Invalid SDNA TLEN section.');
  const tlen = new Array(nTypes);
  for (let i = 0; i < nTypes; i++) { tlen[i] = dv.getInt16(p, L); p += 2; }
  align();
  if (tag() !== 'STRC') throw new BlendError('Invalid SDNA STRC section.');
  const nStructs = dv.getInt32(p, L); p += 4;
  const structs = [];
  const structByName = new Map();
  for (let s = 0; s < nStructs; s++) {
    const typeIdx = dv.getInt16(p, L);
    const nFields = dv.getInt16(p + 2, L);
    p += 4;
    const fields = new Map();
    let off = 0;
    for (let f = 0; f < nFields; f++) {
      const ft = dv.getInt16(p, L);
      const fn = names[dv.getInt16(p + 2, L)];
      p += 4;
      const isPtr = fn.includes('*');
      const dims = [...fn.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1]));
      const count = fn.startsWith('(*') ? 1 : dims.reduce((a, b) => a * b, 1);
      const size = (isPtr ? h.ptrSize : tlen[ft]) * count;
      const base = fn.replace(/^\(?\*+/, '').replace(/\).*$/, '').replace(/\[.*$/, '');
      fields.set(base, { type: types[ft], typeIdx: ft, off, size, isPtr, count, dims });
      off += size;
    }
    const st = { name: types[typeIdx], size: tlen[typeIdx], fields };
    structs.push(st);
    structByName.set(st.name, st);
  }
  return { structs, structByName };
}

// ---------------------------------------------------------------------------
// Struct access

class View {
  constructor(file, struct, off) {
    this.file = file;
    this.struct = struct;
    this.off = off;
  }

  has(name) { return this.struct.fields.has(name); }

  field(name) {
    const f = this.struct.fields.get(name);
    if (!f) throw new BlendError(`${this.struct.name}.${name} not in this file's SDNA.`);
    return f;
  }

  num(name, i = 0) {
    const f = this.field(name);
    const r = TYPE_READERS[f.type];
    if (!r || f.isPtr) throw new BlendError(`${this.struct.name}.${name} is not numeric.`);
    const v = this.file.dv[r[0]](this.off + f.off + i * r[1], this.file.little);
    return typeof v === 'bigint' ? Number(v) : v;
  }

  numOr(name, fallback, i = 0) { return this.has(name) ? this.num(name, i) : fallback; }

  nums(name) {
    const f = this.field(name);
    const out = new Array(f.count);
    for (let i = 0; i < f.count; i++) out[i] = this.num(name, i);
    return out;
  }

  str(name) {
    const f = this.field(name);
    const u8 = this.file.u8;
    const a = this.off + f.off;
    let e = a;
    while (e < a + f.size && u8[e] !== 0) e++;
    return new TextDecoder('utf-8').decode(u8.subarray(a, e));
  }

  ptr(name) {
    const f = this.field(name);
    if (!f.isPtr) throw new BlendError(`${this.struct.name}.${name} is not a pointer.`);
    return ptrKey(this.file.dv, this.off + f.off, this.file.ptrSize, this.file.little);
  }

  sub(name) {
    const f = this.field(name);
    const st = this.file.sdna.structByName.get(f.type);
    if (!st) throw new BlendError(`${f.type} is not a struct.`);
    return new View(this.file, st, this.off + f.off);
  }
}

class BlendFile {
  constructor(u8, header) {
    this.u8 = u8;
    this.dv = new DataView(u8.buffer, u8.byteOffset, u8.byteLength);
    this.header = header;
    this.little = header.little;
    this.ptrSize = header.ptrSize;
    this.blocks = readBlocks(u8, this.dv, header);
    const dna = this.blocks.find((b) => b.code === 'DNA1');
    if (!dna) throw new BlendError('File has no SDNA block.');
    this.sdna = parseSDNA(u8, this.dv, dna, header);
    // Pointers to ID blocks resolve file-wide; pointers to DATA blocks resolve
    // within the ID block they follow (Blender 5.0 reuses DATA addresses across
    // IDs, so a single file-wide map returns the wrong block).
    this.idByPtr = new Map();
    this.dataByPtr = new Map();
    let owner = null;
    for (const b of this.blocks) {
      if (b.code !== 'DATA') {
        owner = b;
        if (b.old) this.idByPtr.set(b.old, b);
        continue;
      }
      b.owner = owner;
      if (!b.old) continue;
      let m = this.dataByPtr.get(owner);
      if (!m) this.dataByPtr.set(owner, (m = new Map()));
      if (!m.has(b.old)) m.set(b.old, b);
    }
  }

  // `scope` is the ID block whose data is being read.
  block(key, scope = null) {
    if (!key) return null;
    const m = scope && this.dataByPtr.get(scope);
    return (m && m.get(key)) || this.idByPtr.get(key) || null;
  }

  structOf(block) { return this.sdna.structs[block.sdna]; }

  view(block, structName, index = 0) {
    const st = structName ? this.sdna.structByName.get(structName) : this.structOf(block);
    if (!st) return null;
    return new View(this, st, block.off + index * st.size);
  }

  // Pointer target as an array of `count` elements of a named struct.
  viewsAt(key, structName, count, scope) {
    const b = this.block(key, scope);
    const st = this.sdna.structByName.get(structName);
    if (!b || !st) return null;
    const n = Math.min(count, Math.floor(b.len / st.size));
    const out = new Array(n);
    for (let i = 0; i < n; i++) out[i] = new View(this, st, b.off + i * st.size);
    return out;
  }

  floatsAt(key, count, scope) {
    const b = this.block(key, scope);
    if (!b) return null;
    const n = Math.min(count, b.len >> 2);
    const out = new Float32Array(n);
    for (let i = 0; i < n; i++) out[i] = this.dv.getFloat32(b.off + i * 4, this.little);
    return out;
  }

  intsAt(key, count, scope) {
    const b = this.block(key, scope);
    if (!b) return null;
    const n = Math.min(count, b.len >> 2);
    const out = new Int32Array(n);
    for (let i = 0; i < n; i++) out[i] = this.dv.getInt32(b.off + i * 4, this.little);
    return out;
  }

  bytesAt(key, count, scope) {
    const b = this.block(key, scope);
    if (!b) return null;
    return this.u8.slice(b.off, b.off + Math.min(count, b.len));
  }

  ptrsAt(key, count, scope) {
    const b = this.block(key, scope);
    if (!b) return [];
    const n = Math.min(count, Math.floor(b.len / this.ptrSize));
    const out = [];
    for (let i = 0; i < n; i++) out.push(ptrKey(this.dv, b.off + i * this.ptrSize, this.ptrSize, this.little));
    return out;
  }

  idName(view) {
    if (!view || !view.has('id')) return '';
    return view.sub('id').str('name').slice(2);
  }
}

// ---------------------------------------------------------------------------
// Meshes

function customDataLayers(file, cd, scope) {
  if (!cd || !cd.has('layers')) return [];
  const n = cd.num('totlayer');
  const views = file.viewsAt(cd.ptr('layers'), 'CustomDataLayer', n, scope) || [];
  return views.map((v) => ({ type: v.num('type'), name: v.str('name'), data: v.ptr('data') }));
}

// Blender 5.0 moved some generic attributes into AttributeStorage.
function attributeStorageLayers(file, mesh, scope) {
  if (!mesh.has('attribute_storage')) return [];
  const as = mesh.sub('attribute_storage');
  if (!as.has('dna_attributes')) return [];
  const n = as.num('dna_attributes_num');
  const views = file.viewsAt(as.ptr('dna_attributes'), 'Attribute', n, scope) || [];
  const out = [];
  for (const a of views) {
    const nameBlock = file.block(a.ptr('name'), scope);
    const name = nameBlock ? ascii(file.u8, nameBlock.off, nameBlock.off + nameBlock.len).replace(/\0.*$/s, '') : '';
    let data = null;
    const dataBlock = file.block(a.ptr('data'), scope);
    if (dataBlock) {
      // Array attributes wrap their buffer in AttributeArray { void *data; ... }.
      const arr = file.view(dataBlock, 'AttributeArray');
      data = arr && arr.has('data') ? arr.ptr('data') : a.ptr('data');
    }
    // storage_type 1 = one value shared by every element (AttributeSingle).
    out.push({ name, domain: a.num('domain'), data, single: a.numOr('storage_type', 0) === 1 });
  }
  return out;
}

function findLayer(layers, name) {
  return layers.find((x) => x.name === name && x.data) || null;
}

// Reads a per-element attribute, expanding single-value storage to `count` elements.
function readAttr(file, l, count, scope, kind) {
  if (!l) return null;
  const read = { int: 'intsAt', float: 'floatsAt', byte: 'bytesAt' }[kind];
  if (!l.single) return file[read](l.data, count, scope);
  const one = file[read](l.data, 1, scope);
  if (!one || !one.length) return null;
  const out = new one.constructor(count);
  out.fill(one[0]);
  return out;
}

function readMesh(file, block) {
  const me = file.view(block, 'Mesh');
  const pick = (...names) => names.find((n) => me.has(n));
  const vertsNum = me.num(pick('verts_num', 'totvert'));
  const facesNum = me.has('faces_num') || me.has('totpoly') ? me.num(pick('faces_num', 'totpoly')) : 0;
  const cornersNum = me.has('corners_num') || me.has('totloop') ? me.num(pick('corners_num', 'totloop')) : 0;

  const S = block;
  const cd = (a, b) => (me.has(a) ? me.sub(a) : me.has(b) ? me.sub(b) : null);
  const vdata = customDataLayers(file, cd('vert_data', 'vdata'), S);
  const pdata = customDataLayers(file, cd('face_data', 'pdata'), S);
  const ldata = customDataLayers(file, cd('corner_data', 'ldata'), S);
  const storage = attributeStorageLayers(file, me, S);
  const layer = (list, name) => findLayer(list, name) || findLayer(storage, name);
  const attr = (list, name, count, kind) => readAttr(file, layer(list, name), count, block, kind);

  // Positions: generic "position" attribute (3.5+) or legacy MVert.co.
  let positions = null;
  positions = layer(vdata, 'position') && !layer(vdata, 'position').single ? file.floatsAt(layer(vdata, 'position').data, vertsNum * 3, S) : null;
  if (!positions && me.has('mvert')) {
    const mv = file.viewsAt(me.ptr('mvert'), 'MVert', vertsNum, S);
    if (mv) {
      positions = new Float32Array(mv.length * 3);
      mv.forEach((v, i) => { positions[i * 3] = v.num('co', 0); positions[i * 3 + 1] = v.num('co', 1); positions[i * 3 + 2] = v.num('co', 2); });
    }
  }
  if (!positions) return { error: 'vertex positions not found' };

  // Faces as (start, size) over a corner->vertex array.
  let cornerVerts = null;
  let faceStart = null;
  let faceSize = null;
  let faceMat = null;
  let faceSmooth = null; // 1 = smooth-shaded face

  const offKey = me.has('face_offset_indices') ? me.ptr('face_offset_indices')
    : me.has('poly_offset_indices') ? me.ptr('poly_offset_indices') : null;
  const cvLayer = layer(ldata, '.corner_vert');
  const offs = offKey ? file.intsAt(offKey, facesNum + 1, S) : null;
  if (offs && offs.length === facesNum + 1 && cvLayer) {
    cornerVerts = attr(ldata, '.corner_vert', cornersNum, 'int');
    faceStart = new Int32Array(facesNum);
    faceSize = new Int32Array(facesNum);
    for (let f = 0; f < facesNum; f++) { faceStart[f] = offs[f]; faceSize[f] = offs[f + 1] - offs[f]; }
  } else if (me.has('mpoly') && me.ptr('mpoly')) {
    const polys = file.viewsAt(me.ptr('mpoly'), 'MPoly', facesNum, S) || [];
    faceStart = new Int32Array(polys.length);
    faceSize = new Int32Array(polys.length);
    faceMat = new Int32Array(polys.length);
    faceSmooth = new Uint8Array(polys.length);
    polys.forEach((p, f) => {
      faceStart[f] = p.num('loopstart'); faceSize[f] = p.num('totloop'); faceMat[f] = p.numOr('mat_nr', 0);
      faceSmooth[f] = p.numOr('flag', 0) & 1; // ME_SMOOTH
    });
    if (cvLayer) cornerVerts = attr(ldata, '.corner_vert', cornersNum, 'int');
    else {
      const loops = file.viewsAt(me.ptr('mloop'), 'MLoop', cornersNum, S) || [];
      cornerVerts = Int32Array.from(loops, (l) => l.num('v'));
    }
  } else if (me.has('mface') && me.ptr('mface')) {
    // Pre-2.63 tessellated faces: tris and quads only.
    const totface = me.num('totface');
    const mf = file.viewsAt(me.ptr('mface'), 'MFace', totface, S) || [];
    cornerVerts = new Int32Array(mf.length * 4);
    faceStart = new Int32Array(mf.length);
    faceSize = new Int32Array(mf.length);
    faceMat = new Int32Array(mf.length);
    faceSmooth = new Uint8Array(mf.length);
    let c = 0;
    mf.forEach((f, i) => {
      const vs = [f.num('v1'), f.num('v2'), f.num('v3'), f.num('v4')];
      const n = vs[3] ? 4 : 3;
      faceStart[i] = c; faceSize[i] = n; faceMat[i] = f.numOr('mat_nr', 0);
      faceSmooth[i] = f.numOr('flag', 0) & 1;
      for (let k = 0; k < n; k++) cornerVerts[c++] = vs[k];
    });
  }

  // Newer files store sharpness as a "sharp_face" bool attribute; when it is
  // absent, every face is smooth.
  if (faceStart && !faceSmooth) {
    const sharp = attr(pdata, 'sharp_face', faceStart.length, 'byte');
    faceSmooth = new Uint8Array(faceStart.length);
    for (let f = 0; f < faceStart.length; f++) faceSmooth[f] = sharp && sharp[f] ? 0 : 1;
  }
  let smoothFaces = 0;
  if (faceSmooth) for (let f = 0; f < faceSmooth.length; f++) smoothFaces += faceSmooth[f];

  const matIdx = faceStart ? attr(pdata, 'material_index', faceStart.length, 'int') : null;
  if (matIdx) faceMat = matIdx;

  // Material slots on the mesh.
  const totcol = me.numOr('totcol', 0);
  const materials = file.ptrsAt(me.has('mat') ? me.ptr('mat') : null, totcol, S).map((k) => readMaterial(file, k));

  // Fan-triangulate each face, bucketed by material slot.
  const buckets = new Map();
  let triCount = 0;
  if (faceStart && cornerVerts) {
    for (let f = 0; f < faceStart.length; f++) {
      const n = faceSize[f];
      if (n < 3) continue;
      const m = faceMat ? Math.max(0, faceMat[f]) : 0;
      let arr = buckets.get(m);
      if (!arr) buckets.set(m, (arr = []));
      const s = faceStart[f];
      const a = cornerVerts[s];
      for (let k = 1; k < n - 1; k++) {
        arr.push(a, cornerVerts[s + k], cornerVerts[s + k + 1]);
        triCount++;
      }
    }
  }
  const indices = new Uint32Array(triCount * 3);
  const groups = [];
  let pos = 0;
  for (const m of [...buckets.keys()].sort((x, y) => x - y)) {
    const arr = buckets.get(m);
    indices.set(arr, pos);
    groups.push({ start: pos, count: arr.length, materialIndex: m });
    pos += arr.length;
  }
  // Drop indices that point past the vertex array (defensive against odd files).
  const nv = positions.length / 3;
  for (let i = 0; i < indices.length; i++) if (indices[i] >= nv) indices[i] = 0;

  return {
    name: file.idName(me),
    positions, indices, groups, materials,
    vertCount: nv, faceCount: faceStart ? faceStart.length : 0, triCount,
    // Share of faces Blender shades smooth (0..1); the viewer shades the whole mesh by majority.
    smoothRatio: faceStart && faceStart.length ? smoothFaces / faceStart.length : 0,
  };
}

function readMaterial(file, key) {
  const b = file.block(key);
  if (!b || b.code !== 'MA') return null;
  const ma = file.view(b, 'Material');
  return {
    name: file.idName(ma),
    color: [ma.numOr('r', 0.8), ma.numOr('g', 0.8), ma.numOr('b', 0.8)],
    alpha: ma.numOr('a', 1),
    metallic: ma.numOr('metallic', 0),
    roughness: ma.numOr('roughness', 0.5),
  };
}

// ---------------------------------------------------------------------------
// Transforms (column-major 4x4, same layout as Blender's float[4][4] and three.js)

function mul4(a, b) {
  const o = new Array(16);
  for (let c = 0; c < 4; c++) {
    for (let r = 0; r < 4; r++) {
      let s = 0;
      for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
      o[c * 4 + r] = s;
    }
  }
  return o;
}

function rotAxis(axis, t) {
  const c = Math.cos(t), s = Math.sin(t);
  // 3x3 row-major
  if (axis === 'X') return [1, 0, 0, 0, c, -s, 0, s, c];
  if (axis === 'Y') return [c, 0, s, 0, 1, 0, -s, 0, c];
  return [c, -s, 0, s, c, 0, 0, 0, 1];
}

function mul3(a, b) {
  const o = new Array(9);
  for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) {
    o[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
  }
  return o;
}

function quatTo3(w, x, y, z) {
  const n = Math.hypot(w, x, y, z) || 1;
  w /= n; x /= n; y /= n; z /= n;
  return [
    1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
    2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
    2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y),
  ];
}

const EULER_ORDERS = { 1: 'XYZ', 2: 'XZY', 3: 'YXZ', 4: 'YZX', 5: 'ZXY', 6: 'ZYX' };

function localMatrix(ob) {
  const loc = ob.nums('loc');
  const dloc = ob.has('dloc') ? ob.nums('dloc') : [0, 0, 0];
  const scale = ob.has('scale') ? ob.nums('scale') : ob.has('size') ? ob.nums('size') : [1, 1, 1];
  const dscale = ob.has('dscale') ? ob.nums('dscale') : [1, 1, 1];
  const mode = ob.numOr('rotmode', 1);
  let R;
  if (mode === 0) {
    const q = ob.nums('quat');
    R = quatTo3(q[0], q[1], q[2], q[3]);
  } else if (mode === -1) {
    const ax = ob.nums('rotAxis');
    const ang = ob.num('rotAngle');
    const l = Math.hypot(...ax) || 1;
    const h = ang / 2;
    R = quatTo3(Math.cos(h), (ax[0] / l) * Math.sin(h), (ax[1] / l) * Math.sin(h), (ax[2] / l) * Math.sin(h));
  } else {
    const order = EULER_ORDERS[mode] || 'XYZ';
    const rot = ob.nums('rot');
    const angle = { X: rot[0], Y: rot[1], Z: rot[2] };
    // Blender applies the first letter first: XYZ => Rz * Ry * Rx.
    R = mul3(rotAxis(order[2], angle[order[2]]), mul3(rotAxis(order[1], angle[order[1]]), rotAxis(order[0], angle[order[0]])));
  }
  const s = [scale[0] * dscale[0], scale[1] * dscale[1], scale[2] * dscale[2]];
  return [
    R[0] * s[0], R[3] * s[0], R[6] * s[0], 0,
    R[1] * s[1], R[4] * s[1], R[7] * s[1], 0,
    R[2] * s[2], R[5] * s[2], R[8] * s[2], 0,
    loc[0] + dloc[0], loc[1] + dloc[1], loc[2] + dloc[2], 1,
  ];
}

function storedWorldMatrix(ob) {
  for (const name of ['object_to_world', 'obmat']) {
    if (ob.has(name)) {
      const m = ob.nums(name);
      if (m.some((v) => v !== 0) && m.every(Number.isFinite)) return m;
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Thumbnail ("TEST" block: int width, int height, RGBA rows bottom-up)

function readThumbnail(file) {
  const b = file.blocks.find((x) => x.code === 'TEST');
  if (!b || b.len < 8) return null;
  const w = file.dv.getInt32(b.off, file.little);
  const h = file.dv.getInt32(b.off + 4, file.little);
  if (w <= 0 || h <= 0 || w > 4096 || h > 4096 || 8 + w * h * 4 > b.len) return null;
  const rgba = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) {
    const src = b.off + 8 + (h - 1 - y) * w * 4;
    rgba.set(file.u8.subarray(src, src + w * 4), y * w * 4);
  }
  return { width: w, height: h, rgba };
}

// ---------------------------------------------------------------------------
// Public entry point

export async function parseBlend(input, options = {}) {
  const { bytes, compression } = await decompress(input, options);
  const header = readHeader(bytes);
  const file = new BlendFile(bytes, header);
  const warnings = [];

  const inventory = {};
  for (const b of file.blocks) {
    if (ID_CODES[b.code]) inventory[ID_CODES[b.code]] = (inventory[ID_CODES[b.code]] || 0) + 1;
  }

  const meshes = new Map();
  const meshFor = (key) => {
    if (!key) return null;
    if (meshes.has(key)) return meshes.get(key);
    const b = file.block(key);
    let mesh = null;
    if (b && b.code === 'ME') {
      try { mesh = readMesh(file, b); } catch (e) { mesh = { error: e.message }; }
    }
    meshes.set(key, mesh);
    return mesh;
  };

  const obBlocks = file.blocks.filter((b) => b.code === 'OB');
  const obViews = new Map(obBlocks.map((b) => [b.old, file.view(b, 'Object')]));
  const worldCache = new Map();
  const worldOf = (key, depth = 0) => {
    if (worldCache.has(key)) return worldCache.get(key);
    const ob = obViews.get(key);
    let m = storedWorldMatrix(ob);
    if (!m) {
      m = localMatrix(ob);
      const parent = ob.has('parent') ? ob.ptr('parent') : null;
      if (parent && obViews.has(parent) && depth < 64) {
        m = mul4(mul4(worldOf(parent, depth + 1), ob.nums('parentinv')), m);
      }
    }
    worldCache.set(key, m);
    return m;
  };

  let modifierCount = 0;
  const objects = obBlocks.map((b) => {
    const ob = obViews.get(b.old);
    const type = ob.num('type');
    const parentKey = ob.has('parent') ? ob.ptr('parent') : null;
    const mods = ob.has('modifiers') && ob.sub('modifiers').ptr('first');
    if (mods) modifierCount++;
    const o = {
      id: b.old,
      name: file.idName(ob),
      type,
      typeName: OB_TYPES[type] || `Type ${type}`,
      parent: parentKey && obViews.has(parentKey) ? file.idName(obViews.get(parentKey)) : null,
      matrix: worldOf(b.old),
      hasModifiers: Boolean(mods),
      mesh: null,
    };
    if (type === 1) {
      const mesh = meshFor(ob.ptr('data'));
      if (mesh && !mesh.error) o.mesh = mesh;
      else warnings.push(`Mesh for "${o.name}" could not be read${mesh && mesh.error ? `: ${mesh.error}` : ''}.`);
    }
    return o;
  });

  if (modifierCount) {
    warnings.push(`${modifierCount} object(s) use modifiers. The preview shows the base mesh without modifiers applied.`);
  }
  if (inventory['Linked libraries']) {
    warnings.push('This file links data from other .blend files. Linked objects are not included.');
  }
  const unsupported = objects.filter((o) => [2, 3, 4, 5, 27, 28, 29].includes(o.type));
  if (unsupported.length) {
    warnings.push(`${unsupported.length} curve, text, metaball, hair, point-cloud or volume object(s) are listed but not drawn.`);
  }

  return {
    version: header.version,
    versionCode: header.versionCode,
    pointerSize: header.ptrSize,
    endian: header.little ? 'little' : 'big',
    compression,
    fileSize: input.byteLength ?? input.length,
    dataSize: bytes.length,
    blockCount: file.blocks.length,
    thumbnail: readThumbnail(file),
    inventory,
    objects,
    warnings,
  };
}
