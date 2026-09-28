# Blend Pocket

A single-file HTML app for opening and previewing Blender `.blend` files on a phone. It needs no install, no Blender and no server-side conversion. Files are read in the browser and never uploaded.

## Use it

**On a phone, as a file (works offline):** copy `blend-pocket.html` to the phone (Downloads, Drive, USB). In the Files app, tap it and choose **Chrome** (Android) or open it in **Safari** (iOS). Everything is bundled inside, so no internet is needed. Avoid the "HTML Viewer" option on Android: it cannot show a file picker.

**Hosted:**

- **Open `index.html`** from any static host (GitHub Pages, Netlify, or any web server), tap **Open**, and pick a `.blend` from Files, iCloud Drive, Google Drive or Downloads.
- On iOS/Android, use the browser's *Add to Home Screen* to launch it like an app.
- You need to be online the first time: three.js and the zstd decoder load from the jsDelivr CDN.
- Also opens common exports: GLB/glTF (embedded), OBJ, FBX, STL, PLY.

Viewport controls: drag to orbit, pinch to zoom, two-finger drag to pan, and tap an object or an Outliner row to select it. The rail has Frame, Shading (Solid / Material / Wire), View (Persp / Front / Right / Top) and Normals (Auto / Flat / Smooth). On a desktop keyboard, Blender's numpad keys work: 1 / 3 / 7 / 5, `.`, Home and Z.

## What it reads

The parser (`src/blend-parser.js`) reads each file's own SDNA struct catalogue instead of hard-coded offsets, which is how one reader covers many Blender versions.

| Supported | Notes |
|---|---|
| Blender 2.5x – 5.x file headers | Includes the new 5.0 "large" header and 64-bit block headers |
| gzip (≤2.9x) and Zstandard (3.0+) compression | zstd is decoded frame by frame from Blender's seek table |
| Mesh objects, world transforms, parenting | Uses the saved world matrix, or recomputes it from loc/rot/scale and parents |
| All three mesh layouts | MFace (<2.63), MVert/MPoly/MLoop (2.63–3.x), generic attributes (3.5+, incl. 5.x `AttributeStorage`) |
| Per-face material slots, viewport colour, metallic/roughness | Node-tree shaders are **not** evaluated |
| Smooth/flat shading as saved | `ME_SMOOTH` flag or the `sharp_face` attribute |
| Embedded file-browser thumbnail, data-block inventory | Shown in the File tab |

**Not shown in the preview:**
- Modifiers, including subdivision and armature deformation. You see the base mesh, and the File tab says so for each file.
- Curves, text, metaballs, hair, point clouds and volumes.
- Linked library data and textures.

## Verification

The parser was checked against 176 real `.blend` files saved by Blender 2.78 through 5.3, most from the [glTF-Blender-IO test scenes](https://github.com/KhronosGroup/glTF-Blender-IO/tree/main/tests/scenes). For all 713 mesh objects, these values match what Blender 5.0 reports when it opens the same file:
- vertex and face counts
- world-space bounding box
- surface area
- per-face smooth shading

No gzip file was in that set, so gzip was tested separately with a gzipped Blender 4.2 file.

```sh
npm install
npm test                      # parser regression test on the embedded sample
npm test -- path/to/file.blend # also parse your own files
npm run build                 # rebuild index.html and blend-pocket.html from src/
```

## Layout

```
src/app.html          viewer UI (three.js), edited by hand
src/blend-parser.js   .blend reader, no DOM or three.js dependency (runs in Node too)
src/sample-blend.js   embedded sample scene (Blender 5.0, zstd)
tools/build.mjs       builds index.html (CDN) and, with --offline, blend-pocket.html (fully bundled)
tools/make_sample.py  regenerates the sample scene with the `bpy` module
blend-pocket.html     built offline single file (three.js bundled), the file to copy to a phone
index.html            built single file that loads three.js from the CDN, for hosting
```

## Known limits

- Large files (roughly 100 MB+ uncompressed) may exceed a phone browser's memory. Parsing runs on the main thread, so the UI pauses while a big file loads.
- n-gons are fan-triangulated, so strongly concave n-gons can render incorrectly.
