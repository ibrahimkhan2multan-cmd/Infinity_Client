// Infinity's Client - Bedrock QoL behavior pack (HUD via the supported Script API).
// Information display only: nothing here changes combat, movement or world state.
import * as mc from "@minecraft/server";

const { world, system } = mc;

// The launcher rewrites everything between the BEGIN/END markers when it builds the pack.
// INFINITY_CONFIG_BEGIN
const CONFIG = {
  brand: true, coords: true, facing: false, dimension: true, nether: false,
  target: true, reach: true, entity: true, health: false, held: false,
  armor: false, speed: false, time: false, combo: true,
  accent: "d", multiline: false, interval: 2, name: "Infinity's Client"
};
// INFINITY_CONFIG_END

const TOGGLE_KEYS = [
  "brand", "coords", "facing", "dimension", "nether", "target", "reach",
  "entity", "health", "held", "armor", "speed", "time", "combo"
];

const COMBO_WINDOW_TICKS = 40; // 2 seconds without a hit ends the combo
const HIT_FLASH_TICKS = 8;     // how long the HIT indicator stays visible
const VIEW_DISTANCE = 8;

// Runtime copy so /scriptevent toggles do not touch the packaged defaults.
const runtime = { ...CONFIG, enabled: true };
runtime.name = String(CONFIG.name ?? "Infinity's Client");
const state = new Map();

function getState(id) {
  let s = state.get(id);
  if (!s) {
    s = { combo: 0, best: 0, lastHit: -100000, flashUntil: -1, lastDamage: null };
    state.set(id, s);
  }
  return s;
}

function strip(id) {
  return String(id ?? "").replace("minecraft:", "");
}

function dimensionLabel(id) {
  if (id === "minecraft:overworld") return "Overworld";
  if (id === "minecraft:nether") return "Nether";
  if (id === "minecraft:the_end") return "The End";
  return strip(id);
}

function entityLabel(entity) {
  if (entity.nameTag && entity.nameTag.length > 0) return entity.nameTag;
  return strip(entity.typeId);
}

function distance(a, b) {
  const dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}

function durabilityOf(item) {
  const d = item?.getComponent("minecraft:durability");
  if (!d) return null;
  return { left: d.maxDurability - d.damage, max: d.maxDurability };
}

// ---- Events ---------------------------------------------------------------

world.afterEvents.entityHitEntity.subscribe((event) => {
  const player = event.damagingEntity;
  if (!player || player.typeId !== "minecraft:player") return;
  const now = system.currentTick;
  const s = getState(player.id);
  s.combo = s.combo > 0 && now - s.lastHit <= COMBO_WINDOW_TICKS ? s.combo + 1 : 1;
  if (s.combo > s.best) s.best = s.combo;
  s.lastHit = now;
  s.flashUntil = now + HIT_FLASH_TICKS;
  s.lastDamage = null; // filled in by entityHurt if the hit dealt damage
});

world.afterEvents.entityHurt.subscribe((event) => {
  const attacker = event.damageSource?.damagingEntity;
  if (attacker && attacker.typeId === "minecraft:player") {
    getState(attacker.id).lastDamage = event.damage;
  }
  const victim = event.hurtEntity;
  if (victim && victim.typeId === "minecraft:player") {
    const s = state.get(victim.id);
    if (s) s.combo = 0; // taking damage ends your combo
  }
});

world.afterEvents.playerLeave.subscribe((event) => {
  state.delete(event.playerId);
});

// ---- HUD segments (each returns a string or null) ---------------------------

const accent = () => `§${runtime.accent}`;

function raycastBlock(player) {
  const hit = player.getBlockFromViewDirection({
    maxDistance: VIEW_DISTANCE,
    includeLiquidBlocks: false,
    includePassableBlocks: false
  });
  return hit && hit.block ? hit : null;
}

function firstEntityInView(player) {
  const hits = player.getEntitiesFromViewDirection({ maxDistance: VIEW_DISTANCE });
  if (!hits) return null;
  return hits.find((h) => h.entity && h.entity.id !== player.id) ?? null;
}

const SEGMENTS = [
  ["brand", () => `${accent()}${runtime.name}`],

  ["coords", (p) => {
    const l = p.location;
    return `§fXYZ ${Math.floor(l.x)} ${Math.floor(l.y)} ${Math.floor(l.z)}`;
  }],

  ["facing", (p) => {
    const yaw = p.getRotation().y;
    const dirs = ["S", "SW", "W", "NW", "N", "NE", "E", "SE"]; // yaw 0 = south, 90 = west
    const idx = Math.round((((yaw % 360) + 360) % 360) / 45) % 8;
    return `§f${dirs[idx]} §7${Math.round(yaw)}°`;
  }],

  ["dimension", (p) => `§7${dimensionLabel(p.dimension.id)}`],

  ["nether", (p) => {
    const l = p.location;
    if (p.dimension.id === "minecraft:overworld") {
      return `§cNether≈ ${Math.floor(l.x / 8)} ${Math.floor(l.z / 8)}`;
    }
    if (p.dimension.id === "minecraft:nether") {
      return `§aOverworld≈ ${Math.floor(l.x * 8)} ${Math.floor(l.z * 8)}`;
    }
    return null;
  }],

  ["target", (p) => {
    const hit = raycastBlock(p);
    if (!hit) return null;
    const b = hit.block.location;
    return `§bLooking at: §f${strip(hit.block.typeId)} (${b.x}, ${b.y}, ${b.z})`;
  }],

  ["reach", (p) => {
    const hit = raycastBlock(p);
    if (!hit) return null;
    const point = hit.faceLocation ?? {
      x: hit.block.location.x + 0.5, y: hit.block.location.y + 0.5, z: hit.block.location.z + 0.5
    };
    return `§eReach≈ ${distance(p.getHeadLocation(), point).toFixed(1)}m`;
  }],

  ["entity", (p) => {
    const hit = firstEntityInView(p);
    if (!hit) return null;
    let text = `§6Target: §f${entityLabel(hit.entity)}`;
    const h = hit.entity.getComponent("minecraft:health");
    if (h) text += ` §c♥${Math.ceil(h.currentValue)}`;
    if (typeof hit.distance === "number") text += ` §7${hit.distance.toFixed(1)}m`;
    return text;
  }],

  ["health", (p) => {
    const h = p.getComponent("minecraft:health");
    if (!h) return null;
    return `§c♥ ${Math.ceil(h.currentValue)}/${Math.ceil(h.effectiveMax)}`;
  }],

  ["held", (p) => {
    const inv = p.getComponent("minecraft:inventory");
    const item = inv?.container?.getItem(p.selectedSlotIndex);
    if (!item) return null;
    let text = `§fHeld: ${strip(item.typeId)}`;
    if (item.amount > 1) text += ` x${item.amount}`;
    const d = durabilityOf(item);
    if (d) text += ` §7${d.left}/${d.max}`;
    return text;
  }],

  ["armor", (p) => {
    const eq = p.getComponent("minecraft:equippable");
    if (!eq) return null;
    const slots = [["H", "Head"], ["C", "Chest"], ["L", "Legs"], ["B", "Feet"]];
    const parts = slots.map(([short, name]) => {
      const item = eq.getEquipment(mc.EquipmentSlot?.[name] ?? name);
      if (!item) return `${short}:--`;
      const d = durabilityOf(item);
      return d ? `${short}:${Math.round((d.left / d.max) * 100)}%` : `${short}:ok`;
    });
    return `§fArmor ${parts.join(" ")}`;
  }],

  ["speed", (p) => {
    const v = p.getVelocity();
    return `§fSpeed ${(Math.sqrt(v.x * v.x + v.z * v.z) * 20).toFixed(1)} b/s`;
  }],

  ["time", () => {
    const t = world.getTimeOfDay(); // 0 = 06:00
    const hours = Math.floor(t / 1000 + 6) % 24;
    const minutes = Math.floor(((t % 1000) / 1000) * 60);
    let text = `§f${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
    try { text += ` §7Day ${world.getDay()}`; } catch (_e) { /* optional */ }
    return text;
  }],

  ["combo", (p) => {
    const s = state.get(p.id);
    if (!s) return null;
    const now = system.currentTick;
    const active = s.combo > 0 && now - s.lastHit <= COMBO_WINDOW_TICKS;
    if (!active) return null;
    let text = `§cCombo: ${s.combo}`;
    if (s.best > s.combo) text += ` §7(best ${s.best})`;
    if (now <= s.flashUntil) {
      text += ` §4✦ HIT`;
      if (typeof s.lastDamage === "number") text += ` -${s.lastDamage.toFixed(1)}`;
    }
    return text;
  }]
];

function buildLine(player) {
  const parts = [];
  for (const [key, build] of SEGMENTS) {
    if (!runtime[key]) continue;
    try {
      const text = build(player);
      if (text) parts.push(text);
    } catch (_error) {
      // A segment that fails on this game version is skipped; the rest keep working.
    }
  }
  return parts.join(runtime.multiline ? "\n" : " §8| ");
}

// ---- Main loop --------------------------------------------------------------

let clearedWhileDisabled = false;

function tick() {
  for (const player of world.getAllPlayers()) {
    try {
      if (!runtime.enabled) {
        if (!clearedWhileDisabled) player.onScreenDisplay.setActionBar("");
        continue;
      }
      player.onScreenDisplay.setActionBar(buildLine(player));
    } catch (_error) { /* player may be mid-teleport or leaving */ }
  }
  clearedWhileDisabled = !runtime.enabled;
}

const interval = Math.min(20, Math.max(1, Math.floor(Number(CONFIG.interval)) || 2));
system.runInterval(tick, interval);

// ---- Optional in-game toggles: /scriptevent infinity:toggle coords ----------
// Requires cheats/commands to be enabled in the world. Changes last until the world reloads.

try {
  system.afterEvents.scriptEventReceive.subscribe((event) => {
    const reply = (msg) => { try { event.sourceEntity?.sendMessage?.(msg); } catch (_e) { /* ignore */ } };
    const arg = String(event.message ?? "").trim().toLowerCase();
    if (event.id === "infinity:hud") {
      if (arg === "on" || arg === "off") {
        runtime.enabled = arg === "on";
        reply(`${accent()}${runtime.name} §fHUD ${arg}`);
      } else {
        reply(`${accent()}${runtime.name} §fUse: /scriptevent infinity:hud on|off`);
      }
    } else if (event.id === "infinity:toggle") {
      if (TOGGLE_KEYS.includes(arg)) {
        runtime[arg] = !runtime[arg];
        reply(`${accent()}${runtime.name} §f${arg}: ${runtime[arg] ? "on" : "off"}`);
      } else {
        reply(`${accent()}${runtime.name} §fUse: /scriptevent infinity:toggle <${TOGGLE_KEYS.join("|")}>`);
      }
    }
  });
} catch (_error) {
  // scriptEventReceive unavailable: launcher-configured HUD still works.
}
