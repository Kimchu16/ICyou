# Display model masters

The editable display masters and their canonical textures live beside the
other ICYou Blockbench assets in `C:\BlockBench\ICYou_Mod`:

- `MediumCameraScreen.bbmodel`
- `BigCameraScreen.bbmodel`

Both masters use and embed the original `CameraScreen_Texture.png` atlas, with
their UVs fit to the full 64x64 texture. The medium/big texture aliases are
byte-identical copies for the mod's runtime identifiers.

Rear plates and structural faces use the atlas's solid mid-grey casing swatch;
vents and connector openings retain their dark interior mappings.

The assembled geometry is rebased so the anchor is the bottom-left block in
the front view: medium spans X=-16..16 and big spans X=-32..16, with the anchor
occupying X=0..16 and Y=0..16.

The mod resource folder retains deployed copies of the two textures under the
runtime identifiers `medium_screen.png` and `big_screen.png`.

The runtime Java models are generated as one local 16x16 model per multiblock
position under `assets/icyou/models/block`:

- Medium: `medium_screen_bottom_left` through `medium_screen_top_right` (4 tiles)
- Big: bottom/middle/top rows of left/center/right tiles (9 tiles); the middle
  tile is named `big_screen_center`

The generated blockstates select these pieces using the display's `part` and
`facing` properties. Inventory, dropped-item, item-frame, and first/third-person
hand views use a separate complete miniature generated under `models/item`.
