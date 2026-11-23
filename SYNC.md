# Sync with Horizon (temporary, delete after the merge lands)

Upstream commit merged: `31f83499b` (2026-11-02, horizon-rust/horizon#7585). Base `f8988d5a7`.
Android before the merge: `73fb969be`. Wasm removal, files only: `30344a60b` (does not build).

The merge commit `212b96cb7` holds its conflict markers on purpose; each group below was resolved in
a commit of its own. Part of the conflicts came resolved from the older `sync-horizon-3` worktree,
read only and carried forward with 3-way merges (the mod crates were left out).

How to work and tools: `sync4/` in the session notes folder
(`%APPDATA%/Claude/side-session-notes/local_4c49e3d5-8a78-4799-b072-15ba12d5ea8a/`): `BRIEF.md`,
`COMPILE.md`, `FOLLOWUPS.md` (what fell, in detail), `vm.sh MODE` (check, all, android, clippy,
clippy-android, fmt, test, raw CMD). On the VM: `~/sync4`, `~/sync4-target`, `~/sync4-apk.sh`.

## Steps
- [x] A. Mods: `8f18fd9d6`
- [x] B. Render and shaders: `26f8ed428`
- [x] C. Client gameplay: `2315ac2db`
- [x] D. game.rs, title, Cargo, CI: `3bbeb3a20`
- [x] E. Desktop builds (`cda4b7629`); Android builds, hooks in `horizon_client::android`, Dev APK (`7a5fb6925`)
- [x] fmt
- [x] Client opens on the VM (Xvfb, software Vulkan, new world, no mods or pack)
- [x] Tests: 5495 passed, 0 failed, 104 ignored
- [x] Clippy desktop and Android, `-D warnings`
- [ ] Owner tests the APK; merge into android

## What fell
- Wasm mods and their API: horizon-mod-sdk, starling-mod-sdk, starling-mod-cli, mods/official
  (far-view, fps-overlay, keystrokes, minimap, recipes, zoom) and the SDK examples.
- Create: own block states (`states`, `own-states.json`) and so placement orientation and belt
  funnels (`belt_funnel_for`); `fuel` on items; the support and occlusion part of `properties.shape`.
  The local .hmod data has to move to `state_properties` and `components`.
- Create New World's "Mods & Data Packs": upstream's Data Packs screen stays; a new world plays with
  the running mods; the phone's `datapacks` folder choice.
- Stack tooltip grids; Starling's remote item displays (upstream's `text_displays` instead).
- Failed shader experiments `HORIZON_PACK_SKY_LAST`, `HORIZON_PACK_VRS`, `HORIZON_PACK_HALF`.
- Item grids in tooltips (`StackTooltip`), the Config button and summary line on the Mods page.
- Android: `learn_layout` does nothing, the splitscreen pad router is a stub.

## Not checked
Nothing ran on a phone. On the VM the client ran without mods and without a shader pack: Create,
shader packs, touch, custom buttons, the setup download and native mods builds were not run after
the merge.

## Ported after the squash (2026-11-23)

The fork's history is a single squashed root (`1beb91ea75`), so `git merge upstream/main` has no
common ancestor to merge from. Stable upstream changes that matter to phones are ported by hand:

- `ab44288662` — cloud mesh rebuilds through a precomputed L2 cloud mask (+2.07x CPU).
- `2d43808325` — shader-pack sky visibility switches (`sky`, `stars`, `sun`, `moon`).
- `95c4d72ce9` — the sky gradient and sunrise fan follow the same view pose (bob/spin) as the
  world, so the sky no longer stands still under a bobbing camera.
- `e55ba16038` and later — TML's own shader cache, resolution-scale and default-pack changes
  stay on top and are not upstream.
- `15c1eec6c6` — shader-pack expressions and `#if` conditions have a nesting limit.
- `58d1bd9ce5` — a continued `block.properties` value continues past a preprocessor group.
- `a95d0770af` — numeric promotion stays local to each shader-pack operation.
- `ca9f24c304` — classic GLSL parsing/lowering depth is bounded (naga).
- `8365a5f59e` — section arena pages grow by half, not double (less video memory).
- `e349aeccea` — chunk lighting BFS reads the sky layer directly and packs its bits (-7.9% CPU).
- `f43817376a` — upload staging ring is bucketed and trims when idle (-13 to -25 MB RSS).
- `6d40db2be5` — shadowtex0/1 read through a plain sampler filter linearly as Iris does, which
  removes the striped shadows on distant ground; a pack's `shadowtexNNearest` keeps nearest.
- `d652ec2f1b` — classic packs' terrain is drawn from the camera rounded to 1/65536 block, as
  Iris does, so shadow-map texel ties no longer flip and stripe the ground.
- `b45d73de43` — weather columns outside the view frustum are not built or uploaded.
- `322774f158` — storage images may declare a compatible qualifier (`rgba16` over `RGBA16F`);
  the naga module is rewritten to the texture's real format, so IterationRP's bloom no longer
  leaves a white frame.
- `8e0b8ff5a5` (part) — `LightmapKey`: the 256-texel lightmap recompute is skipped when its
  inputs did not change. The sprite tick cache and per-frame upload skip are still to port.
- `8e0b8ff5a5` (part 2) — skip hand/crosshair global uploads when there is nothing to draw, and
  skip the sunrise fan upload when the sky is not visible.
- Java removed from the app: no runtime download, no on-phone preparation, no exporter bundle
  or `java`/`javac` launchers in the APK. The first-run screen imports the `resources` folder
  prepared on a computer (zipped) and keeps sign-in/offline; nothing is fetched from Mojang.
- Storage: dead space is freed at start — `minecraft/` (the phone no longer prepares), the old
  Java preparation folders, downloaded update APKs, staged imports, and a redundant private
  `resources` copy; old resource-pack overlays beyond the one in use are dropped. The log prints
  what the app's own files hold, folder by folder.
- Android: `--resources` follows the game folder (shared storage when allowed), so moving the
  resources out of app storage no longer hides them from the game.
- TML Visuals Lite: two cloud sheets (the vanilla height and a higher, slower one), drawn over
  terrain where they are in front of it, to the horizon instead of only overhead.
- Shadows: a `shaders/` pack with its own shadow pass hides vanilla's round entity shadows too;
  a pack whose shadow programs did not load says so when it loads.
