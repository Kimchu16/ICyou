import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

const root = path.resolve(import.meta.dirname, "..");
const FACE_UV = [0, 0, 8, 8];
const DARK_UV = [0.25, 8.25, 0.5, 8.5];
// Solid mid-grey casing pixel (#69747C) from the original screen atlas. Using
// this for structural/rear surfaces keeps the back housing grey instead of
// sampling the near-black utility pixels beside the atlas swatches.
const METAL_UV = [0, 9, 0.25, 9.25];
const PANEL_UV = [0.5, 8.25, 0.75, 8.5];
const GREEN_UV = [1.5, 8.25, 1.75, 8.5];
const RED_UV = [1.75, 8.25, 2, 8.5];
const WARNING_UV = [8, 11.5, 13, 13.5];

function uuid(seed) {
  const hex = crypto.createHash("md5").update(seed).digest("hex").split("");
  hex[12] = "4";
  hex[16] = ((Number.parseInt(hex[16], 16) & 3) | 8).toString(16);
  return `${hex.slice(0, 8).join("")}-${hex.slice(8, 12).join("")}-${hex.slice(12, 16).join("")}-${hex.slice(16, 20).join("")}-${hex.slice(20).join("")}`;
}

function faces(frontUv = DARK_UV, sideUv = DARK_UV, backUv = sideUv) {
  return {
    north: { uv: frontUv, texture: "#0" },
    east: { uv: sideUv, texture: "#0" },
    south: { uv: backUv, texture: "#0" },
    west: { uv: sideUv, texture: "#0" },
    up: { uv: sideUv, texture: "#0" },
    down: { uv: sideUv, texture: "#0" },
  };
}

function solidFaces(uv) {
  return faces(uv, uv, uv);
}

function cube(name, from, to, faceData = faces(), group = "housing") {
  return { name, from, to, faces: faceData, group };
}

function perimeter(elements, size, thickness, z1, z2, prefix, group = "frame") {
  elements.push(
    cube(`${prefix}_left_rail`, [0, 0, z1], [thickness, size, z2], solidFaces(METAL_UV), group),
    cube(`${prefix}_right_rail`, [size - thickness, 0, z1], [size, size, z2], solidFaces(METAL_UV), group),
    cube(`${prefix}_top_rail`, [thickness, size - thickness, z1], [size - thickness, size, z2], solidFaces(METAL_UV), group),
    cube(`${prefix}_bottom_rail`, [thickness, 0, z1], [size - thickness, thickness, z2], solidFaces(METAL_UV), group),
  );
}

function mediumElements() {
  const e = [];
  e.push(
    cube("continuous_2x2_display_glass", [0, 0, 12.65], [32, 32, 12.9], faces(FACE_UV), "display_face"),
    cube("display_substrate", [0, 0, 12.9], [32, 32, 13.35], faces(DARK_UV), "display_face"),
    cube("vertical_multiblock_divider", [15.88, 0, 12.55], [16.12, 32, 12.64], solidFaces(METAL_UV), "display_face"),
    cube("horizontal_multiblock_divider", [0, 15.88, 12.55], [32, 16.12, 12.64], solidFaces(METAL_UV), "display_face"),
    cube("rear_backplate", [0.55, 0.55, 13.35], [31.45, 31.45, 13.8], solidFaces(METAL_UV), "housing"),
  );
  perimeter(e, 32, 0.8, 13.4, 14.85, "medium_outer");
  e.push(
    cube("left_vertical_back_brace", [3.2, 2.1, 13.7], [4.2, 29.9, 14.5], solidFaces(METAL_UV), "frame"),
    cube("right_vertical_back_brace", [27.8, 2.1, 13.7], [28.8, 29.9, 14.5], solidFaces(METAL_UV), "frame"),
    cube("upper_cross_brace", [4.2, 26.6, 13.75], [27.8, 27.65, 14.55], solidFaces(METAL_UV), "frame"),
    cube("lower_cross_brace", [4.2, 4.35, 13.75], [27.8, 5.4, 14.55], solidFaces(METAL_UV), "frame"),
    cube("left_controller", [6.2, 8, 13.8], [15.25, 24, 15.35], solidFaces(PANEL_UV), "controllers"),
    cube("right_controller", [16.75, 8, 13.8], [25.8, 24, 15.35], solidFaces(PANEL_UV), "controllers"),
    cube("left_vent", [7.4, 10.2, 15.35], [14.05, 21.8, 15.65], faces(PANEL_UV, PANEL_UV, [8, 0, 16, 8]), "controllers"),
    cube("right_vent", [17.95, 10.2, 15.35], [24.6, 21.8, 15.65], faces(PANEL_UV, PANEL_UV, [8, 0, 16, 8]), "controllers"),
    cube("medium_identity_bridge", [13.75, 25.15, 14.4], [18.25, 28.4, 15.7], faces(PANEL_UV, PANEL_UV, WARNING_UV), "controllers"),
    cube("power_input", [1.4, 13.4, 14.65], [4.8, 18.6, 15.9], faces(DARK_UV, DARK_UV, [8, 9, 12, 11]), "connectors"),
    cube("data_input", [27.2, 17.1, 14.8], [30.65, 20.25, 15.95], faces(DARK_UV, DARK_UV, [8, 9, 12, 11]), "connectors"),
    cube("data_output", [27.2, 11.75, 14.8], [30.65, 14.9, 15.95], faces(DARK_UV, DARK_UV, [8, 9, 12, 11]), "connectors"),
    cube("medium_status_module", [12.6, 2, 15.0], [19.4, 4.1, 15.75], solidFaces(PANEL_UV), "connectors"),
    cube("medium_green_led_left", [14.05, 2.7, 15.75], [14.85, 3.45, 15.9], solidFaces(GREEN_UV), "connectors"),
    cube("medium_green_led_right", [17.15, 2.7, 15.75], [17.95, 3.45, 15.9], solidFaces(GREEN_UV), "connectors"),
  );
  return e;
}

function bigElements() {
  const e = [];
  e.push(
    cube("continuous_3x3_display_glass", [0, 0, 12.65], [48, 48, 12.9], faces(FACE_UV), "display_face"),
    cube("display_substrate", [0, 0, 12.9], [48, 48, 13.35], faces(DARK_UV), "display_face"),
  );
  for (const x of [15.86, 31.86]) {
    e.push(cube(`vertical_multiblock_divider_${x < 20 ? "left" : "right"}`, [x, 0, 12.53], [x + 0.28, 48, 12.64], solidFaces(METAL_UV), "display_face"));
  }
  for (const y of [15.86, 31.86]) {
    e.push(cube(`horizontal_multiblock_divider_${y < 20 ? "lower" : "upper"}`, [0, y, 12.53], [48, y + 0.28, 12.64], solidFaces(METAL_UV), "display_face"));
  }
  e.push(cube("reinforced_rear_backplate", [0.7, 0.7, 13.35], [47.3, 47.3, 13.85], solidFaces(METAL_UV), "housing"));
  perimeter(e, 48, 1.15, 13.4, 15.05, "big_reinforced");
  e.push(
    cube("left_back_column", [4.2, 2.8, 13.75], [5.55, 45.2, 14.7], solidFaces(METAL_UV), "frame"),
    cube("centre_back_column", [23.35, 2.8, 13.75], [24.65, 45.2, 14.7], solidFaces(METAL_UV), "frame"),
    cube("right_back_column", [42.45, 2.8, 13.75], [43.8, 45.2, 14.7], solidFaces(METAL_UV), "frame"),
    cube("upper_back_beam", [5.55, 41.9, 13.8], [42.45, 43.25, 14.75], solidFaces(METAL_UV), "frame"),
    cube("lower_back_beam", [5.55, 4.75, 13.8], [42.45, 6.1, 14.75], solidFaces(METAL_UV), "frame"),
  );
  const starts = [7.1, 19.55, 32];
  starts.forEach((x, i) => {
    e.push(
      cube(`controller_${i + 1}`, [x, 12.2, 13.85], [x + 8.9, 35.8, 15.45], solidFaces(PANEL_UV), "controllers"),
      cube(`vent_${i + 1}`, [x + 1.05, 15.1, 15.45], [x + 7.85, 32.9, 15.75], faces(PANEL_UV, PANEL_UV, [8, 0, 16, 8]), "controllers"),
    );
  });
  e.push(
    cube("big_central_service_spine", [21.7, 7.3, 14.45], [26.3, 40.7, 15.85], faces(PANEL_UV, PANEL_UV, WARNING_UV), "controllers"),
    cube("upper_identity_cap", [19.3, 39.2, 14.3], [28.7, 44.3, 15.65], faces(PANEL_UV, PANEL_UV, WARNING_UV), "controllers"),
    cube("lower_identity_cap", [19.3, 3.7, 14.3], [28.7, 8.8, 15.65], faces(PANEL_UV, PANEL_UV, WARNING_UV), "controllers"),
    cube("dual_power_input", [1.8, 20.2, 14.75], [6.3, 27.8, 16], faces(DARK_UV, DARK_UV, [8, 9, 12, 11]), "connectors"),
    cube("upper_data_bank", [41.7, 27.1, 14.75], [46.2, 33.5, 16], faces(DARK_UV, DARK_UV, [8, 9, 12, 11]), "connectors"),
    cube("lower_data_bank", [41.7, 14.5, 14.75], [46.2, 20.9, 16], faces(DARK_UV, DARK_UV, [8, 9, 12, 11]), "connectors"),
    cube("big_status_module", [17.4, 1.6, 15.1], [30.6, 4.35, 15.85], solidFaces(PANEL_UV), "connectors"),
    cube("big_green_led", [20.35, 2.45, 15.85], [21.35, 3.4, 16], solidFaces(GREEN_UV), "connectors"),
    cube("big_red_led", [23.5, 2.45, 15.85], [24.5, 3.4, 16], solidFaces(RED_UV), "connectors"),
    cube("big_green_led_2", [26.65, 2.45, 15.85], [27.65, 3.4, 16], solidFaces(GREEN_UV), "connectors"),
  );
  return e;
}

function exportedModel(name, textureName, elements) {
  const groups = [...new Set(elements.map((e) => e.group))].map((groupName) => ({
    name: groupName,
    origin: [8, 8, 14],
    children: elements.flatMap((e, index) => e.group === groupName ? [index] : []),
  }));
  return {
    format_version: "1.21.11",
    credit: "Made with Blockbench; generated from ICYou display design source",
    texture_size: [64, 64],
    textures: { "0": `icyou:block/${textureName}`, particle: `icyou:block/${textureName}` },
    elements: elements.map(({ group, ...element }) => element),
    groups,
  };
}

function itemModel(name, textureName, size, elements) {
  const scale = 16 / size;
  const miniature = elements.map((element) => ({
    ...element,
    from: [element.from[0] * scale, element.from[1] * scale, element.from[2]],
    to: [element.to[0] * scale, element.to[1] * scale, element.to[2]],
  }));
  return {
    ...exportedModel(`${name}_item`, textureName, miniature),
    ambientocclusion: false,
    gui_light: "front",
    display: {
      // Reuse the proven thin-panel transforms from the portable display.
      gui: { rotation: [0, 180, 0], translation: [0, 0, 0], scale: [0.78, 0.78, 0.78] },
      ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.4, 0.4, 0.4] },
      fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.7, 0.7, 0.7] },
      thirdperson_righthand: { rotation: [0, 90, -35], translation: [0, 1.5, -3], scale: [0.5, 0.5, 0.5] },
      thirdperson_lefthand: { rotation: [0, 90, -35], translation: [0, 1.5, -3], scale: [0.5, 0.5, 0.5] },
      firstperson_righthand: { rotation: [0, 90, -25], translation: [1.1, 3.2, 1.1], scale: [0.62, 0.62, 0.62] },
      firstperson_lefthand: { rotation: [0, 90, -25], translation: [1.1, 3.2, 1.1], scale: [0.62, 0.62, 0.62] },
    },
  };
}

function blockstate(name, count) {
  const rotations = { north: 0, east: 90, south: 180, west: 270 };
  const allParts = [
    "bottom_left", "bottom_center", "bottom_right",
    "middle_left", "center", "middle_right",
    "top_left", "top_center", "top_right",
  ];
  const variants = {};
  for (const [facing, rotation] of Object.entries(rotations)) {
    for (let y = 0; y < count; y++) {
      for (let x = 0; x < count; x++) {
        const modelName = tileName(name, count, x, y);
        const partName = modelName.slice(`${name}_`.length);
        const variant = { model: `icyou:block/${modelName}` };
        if (rotation !== 0) variant.y = rotation;
        variants[`facing=${facing},part=${partName}`] = variant;
      }
    }
    // Medium displays share the nine-value part property with big displays.
    // Commands can construct the five otherwise-unused states, so give those
    // combinations a harmless fallback model to keep resource loading clean.
    for (const partName of allParts) {
      const key = `facing=${facing},part=${partName}`;
      if (!(key in variants)) {
        const fallback = { model: `icyou:block/${name}_bottom_left` };
        if (rotation !== 0) fallback.y = rotation;
        variants[key] = fallback;
      }
    }
  }
  return { variants };
}

function tileElements(elements, tileX, tileY) {
  const minX = tileX * 16;
  const minY = tileY * 16;
  const maxX = minX + 16;
  const maxY = minY + 16;
  return elements.flatMap((element) => {
    const clippedFromX = Math.max(element.from[0], minX);
    const clippedFromY = Math.max(element.from[1], minY);
    const clippedToX = Math.min(element.to[0], maxX);
    const clippedToY = Math.min(element.to[1], maxY);
    if (clippedFromX >= clippedToX || clippedFromY >= clippedToY) return [];
    return [{
      ...element,
      from: [clippedFromX - minX, clippedFromY - minY, element.from[2]],
      to: [clippedToX - minX, clippedToY - minY, element.to[2]],
    }];
  });
}

function tileName(name, count, x, y) {
  // A north-facing model is viewed toward +Z, which mirrors model-space X.
  // Name columns by their visible front-view position so bottom_left is the
  // controller tile even though it comes from the master's highest X range.
  const columns = count === 2 ? ["right", "left"] : ["right", "center", "left"];
  const rows = count === 2 ? ["bottom", "top"] : ["bottom", "middle", "top"];
  if (count === 3 && x === 1 && y === 1) return `${name}_center`;
  return `${name}_${rows[y]}_${columns[x]}`;
}

function bbmodel(name, textureName, size, elements) {
  // North-facing Java models mirror world X in the front view. Keeping the
  // anchor block at x=0..16 therefore requires the assembled display to extend
  // into negative model X so the visible anchor is the bottom-left block.
  const anchorShiftX = 16 - size;
  const textureFile = path.join(root, "src/main/resources/assets/icyou/textures/block", `${textureName}.png`);
  const textureSource = fs.existsSync(textureFile)
    ? `data:image/png;base64,${fs.readFileSync(textureFile).toString("base64")}`
    : undefined;
  const cubes = elements.map(({ group, ...element }, index) => ({
    ...element,
    from: [element.from[0] + anchorShiftX, element.from[1], element.from[2]],
    to: [element.to[0] + anchorShiftX, element.to[1], element.to[2]],
    faces: Object.fromEntries(Object.entries(element.faces).map(([side, face]) => [
      side,
      {
        ...face,
        // Blockbench stores UVs in texture pixels; Java model exports normalize
        // these values to the 0..16 block-model UV coordinate space.
        uv: face.uv.map((coordinate) => coordinate * 4),
        texture: Number.parseInt(face.texture.replace("#", ""), 10),
      },
    ])),
    uuid: uuid(`${name}-cube-${index + 1}`),
    origin: [size / 2 + anchorShiftX, size / 2, 8],
    color: index % 8,
    type: "cube",
  }));
  const outliner = [...new Set(elements.map((e) => e.group))].map((groupName, index) => ({
    name: groupName,
    origin: [size / 2 + anchorShiftX, size / 2, 14],
    color: index,
    uuid: uuid(`${name}-group-${index + 1}`),
    children: cubes.filter((_, cubeIndex) => elements[cubeIndex].group === groupName).map((cube) => cube.uuid),
  }));
  return {
    meta: {
      format_version: "4.10",
      // The assembled source intentionally spans several blocks. Keep it as a
      // free model so Blockbench does not mistake it for one Java block model.
      model_format: "free",
      box_uv: false,
    },
    name,
    model_identifier: `icyou:${name}`,
    visible_box: [size, size, 16],
    variable_placeholders: `Master multiblock model: ${size / 16}x${size / 16} blocks`,
    resolution: { width: 64, height: 64 },
    elements: cubes,
    outliner,
    textures: [{
      path: `../src/main/resources/assets/icyou/textures/block/${textureName}.png`,
      name: `${textureName}.png`,
      folder: "block",
      namespace: "icyou",
      id: "0",
      width: 64,
      height: 64,
      uv_width: 16,
      uv_height: 16,
      particle: true,
      use_as_default: true,
      file_format: "png",
      render_mode: "default",
      visible: true,
      internal: Boolean(textureSource),
      saved: true,
      uuid: uuid(`${name}-texture-1`),
      relative_path: `block/${textureName}.png`,
      ...(textureSource ? { source: textureSource } : {}),
    }],
  };
}

const designs = [
  ["medium_screen", "medium_screen", 32, mediumElements()],
  ["big_screen", "big_screen", 48, bigElements()],
];

for (const [name, textureName, size, elements] of designs) {
  const sourcePath = path.join(root, "blockbench", `${name}.bbmodel`);
  fs.mkdirSync(path.dirname(sourcePath), { recursive: true });
  fs.writeFileSync(sourcePath, `${JSON.stringify(bbmodel(name, textureName, size, elements), null, 2)}\n`);

  // Java block-model cubes must remain local to one block. Export the editable
  // master as clipped 16x16 tiles ready for a future multiblock blockstate.
  const count = size / 16;
  for (let y = 0; y < count; y++) {
    for (let x = 0; x < count; x++) {
      const partName = tileName(name, count, x, y);
      const exportPath = path.join(root, "src/main/resources/assets/icyou/models/block", `${partName}.json`);
      const partElements = tileElements(elements, x, y);
      fs.writeFileSync(exportPath, `${JSON.stringify(exportedModel(partName, textureName, partElements), null, 2)}\n`);
    }
  }

  const blockstatePath = path.join(root, "src/main/resources/assets/icyou/blockstates", `${name}.json`);
  fs.writeFileSync(blockstatePath, `${JSON.stringify(blockstate(name, count), null, 2)}\n`);

  // Inventory and hand rendering use one complete miniature, just as a door
  // uses a dedicated item representation rather than assembling world parts.
  const itemPath = path.join(root, "src/main/resources/assets/icyou/models/item", `${name}.json`);
  fs.writeFileSync(itemPath, `${JSON.stringify(itemModel(name, textureName, size, elements), null, 2)}\n`);

  const obsoleteMasterExport = path.join(root, "src/main/resources/assets/icyou/models/block", `${name}.json`);
  if (fs.existsSync(obsoleteMasterExport)) fs.rmSync(obsoleteMasterExport);
}
