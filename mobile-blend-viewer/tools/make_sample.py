# Regenerates the embedded sample scene. Run with the Blender `bpy` module (pip install bpy):
#   python tools/make_sample.py -- sample-scene.blend --compress
# then base64 it into src/sample-blend.js and update tools/test-parser.mjs if the scene changes.
import bpy, sys, math
out = sys.argv[sys.argv.index('--')+1]
compress = '--compress' in sys.argv
bpy.ops.wm.read_factory_settings(use_empty=True)
def mat(name, rgb, metal=0.0, rough=0.5):
    m = bpy.data.materials.new(name); m.diffuse_color = (*rgb, 1); m.metallic = metal; m.roughness = rough; return m
bpy.ops.mesh.primitive_monkey_add(location=(0,0,1)); s = bpy.context.object; s.name = "Suzanne"
s.data.materials.append(mat("Clay", (0.85,0.55,0.35)))
bpy.ops.object.shade_smooth()
bpy.ops.mesh.primitive_cube_add(size=1, location=(2.2,0,0.5), rotation=(0,0,math.radians(30))); c=bpy.context.object; c.name="Crate"; c.scale=(1,1,1.4)
c.data.materials.append(mat("Teal", (0.1,0.55,0.6))); c.data.materials.append(mat("Brass",(0.9,0.7,0.2),1,0.3))
for i,p in enumerate(c.data.polygons): p.material_index = i % 2
bpy.ops.mesh.primitive_torus_add(location=(-2.3,0,0.4)); t=bpy.context.object; t.name="Ring"; t.rotation_mode='QUATERNION'; t.rotation_quaternion=(0.924,0.383,0,0)
t.data.materials.append(mat("Coral",(0.95,0.35,0.35)))
bpy.ops.mesh.primitive_cylinder_add(vertices=12, radius=0.25, depth=0.6, location=(0,0,0.9)); ch=bpy.context.object; ch.name="Antenna"; ch.parent=s; ch.matrix_parent_inverse = s.matrix_world.inverted()
bpy.ops.mesh.primitive_plane_add(size=8); bpy.context.object.name="Floor"
bpy.ops.object.camera_add(location=(7,-7,5), rotation=(math.radians(64), 0, math.radians(45))); cam=bpy.context.object; cam.data.lens=40
bpy.context.scene.camera = cam
bpy.ops.object.light_add(type='SUN', location=(3,3,6), rotation=(math.radians(35), math.radians(10), math.radians(35))); bpy.context.object.data.energy = 3
bpy.ops.object.light_add(type='POINT', location=(-3,-2.5,2.5)); fill=bpy.context.object; fill.name="Fill"; fill.data.energy=300; fill.data.color=(1.0,0.75,0.55)
bpy.context.scene.render.resolution_x=1600; bpy.context.scene.render.resolution_y=1000; bpy.context.scene.render.resolution_percentage=100
if bpy.context.scene.world is None: bpy.context.scene.world = bpy.data.worlds.new("World")
bpy.context.scene.world.color=(0.06,0.065,0.075)
# ngon test
bpy.ops.mesh.primitive_circle_add(vertices=7, fill_type='NGON', location=(0,2.5,0.01)); bpy.context.object.name="Heptagon"
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=out, compress=compress)
