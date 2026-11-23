//! What the app itself needs around the game files. TML no longer prepares them on the phone:
//! preparing runs Mojang's own exporters, which need a Java runtime the app does not carry (or
//! download) any more. The resources `horizon-prepare` makes on a computer are imported as a
//! zip (`import.rs`, `Kind::Resources`). What stays here is the setup's skin and the packs the
//! app carries.
use std::path::{Path, PathBuf};

/// The app's own folder, where the setup's skin is unpacked.
fn home() -> PathBuf {
    crate::logging::private_home().join("preparer")
}

/// The low-budget skin the setup draws the game's menus with while their own resources are not
/// there (packaging/lowbudget, the APK's `assets/lowbudget.zip`): a resource folder, unpacked
/// into the app's own folder once for each skin the app carries.
pub fn skin() -> Option<PathBuf> {
    let bytes = crate::platform::asset("lowbudget.zip")?;
    let stamp = format!("{}-{:08x}", bytes.len(), crc32(&bytes));
    let dir = home().join("lowbudget");
    let marker = dir.join(".starling-skin");
    if std::fs::read_to_string(&marker).ok().as_deref() == Some(&stamp) {
        return Some(dir);
    }
    let _ = std::fs::remove_dir_all(&dir);
    match unpack(&bytes, &dir)
        .and_then(|()| std::fs::write(&marker, &stamp).map_err(|e| e.to_string()))
    {
        Ok(()) => Some(dir),
        Err(e) => {
            log::warn!("Horizon: the setup's skin did not unpack: {e}");
            None
        }
    }
}

/// TML Visuals Lite, the shader pack the app carries, put in `shaderpacks/` of the game
/// folder. It is never picked for the player: a new install starts with shaders Off, and the
/// pack is there in the list when the player wants it.
pub fn put_shader_pack(game_folder: &Path) {
    let _ = put_asset_pack(game_folder, "TMLVisualsLite.zip", ".tml-visuals-lite");
    // A save that still names the removed full pack starts with shaders Off instead: the
    // player is not quietly moved to another look they did not choose.
    let graphics_file = game_folder.join("horizon-graphics.txt");
    let Ok(content) = std::fs::read_to_string(&graphics_file) else {
        return;
    };
    if !content
        .lines()
        .any(|line| line.trim() == "pickedShaderPack:TMLVisuals.zip")
    {
        return;
    }
    let lines: Vec<String> = content
        .lines()
        .map(|line| {
            if line.trim() == "pickedShaderPack:TMLVisuals.zip" {
                "pickedShaderPack:".to_string()
            } else {
                line.to_string()
            }
        })
        .collect();
    if let Err(e) = std::fs::write(&graphics_file, lines.join("\n") + "\n") {
        log::warn!(
            "TML: the removed shader pack could not be cleared in {}: {e}",
            graphics_file.display()
        );
    }
}

/// One bundled shader pack: `asset` from the APK's assets, written as `name` in
/// `shaderpacks/` when it changed. Returns whether it is there.
fn put_asset_pack(game_folder: &Path, name: &str, marker_name: &str) -> bool {
    let Some(bytes) = crate::platform::asset(name) else {
        return false;
    };
    let dir = game_folder.join("shaderpacks");
    let target = dir.join(name);
    let stamp = format!("{}-{:08x}", bytes.len(), crc32(&bytes));
    let marker = crate::logging::private_home().join(marker_name);
    if target.exists() && std::fs::read_to_string(&marker).ok().as_deref() == Some(&stamp) {
        return true;
    }
    match std::fs::create_dir_all(&dir)
        .and_then(|()| std::fs::write(&target, &bytes))
        .and_then(|()| std::fs::write(&marker, &stamp))
    {
        Ok(()) => {
            log::info!("TML: {name} put in {}", dir.display());
            true
        }
        Err(e) => {
            log::warn!("TML: {name} could not be put in {}: {e}", dir.display());
            false
        }
    }
}

/// Bundled resource packs the app carries (e.g. Faithful 32x),
/// put in `resourcepacks/` of the game folder so the player can choose to activate them.
pub fn put_resource_packs(game_folder: &Path) {
    let packs = ["Faithful 32x - 26.3.zip", "TML.zip"];
    let dir = game_folder.join("resourcepacks");
    for pack_name in packs {
        let Some(bytes) = crate::platform::asset(pack_name) else {
            continue;
        };
        let stamp = format!("{}-{:08x}", bytes.len(), crc32(&bytes));
        let target = dir.join(pack_name);
        let marker =
            crate::logging::private_home().join(format!(".pack-{}", crc32(pack_name.as_bytes())));
        if target.exists() && std::fs::read_to_string(&marker).ok().as_deref() == Some(&stamp) {
            continue;
        }
        match std::fs::create_dir_all(&dir)
            .and_then(|()| std::fs::write(&target, &bytes))
            .and_then(|()| std::fs::write(&marker, &stamp))
        {
            Ok(()) => log::info!("TML: {pack_name} put in {}", dir.display()),
            Err(e) => log::warn!(
                "TML: {pack_name} could not be put in {}: {e}",
                dir.display()
            ),
        }
    }
    // Activate TML UI Pack by default in options.txt if not yet specified
    let options_file = game_folder.join("options.txt");
    if let Ok(content) = std::fs::read_to_string(&options_file) {
        if !content.contains("TML.zip") {
            let tml_pack = "file/TML.zip";
            let mut new_lines = Vec::new();
            let mut found_packs = false;
            for line in content.lines() {
                if line.starts_with("resourcePacks:") {
                    found_packs = true;
                    if let Some(list_part) = line.strip_prefix("resourcePacks:") {
                        let trimmed = list_part.trim();
                        if trimmed == "[]" {
                            new_lines.push(format!("resourcePacks:[\"vanilla\",\"{tml_pack}\"]"));
                        } else if trimmed.ends_with(']') {
                            let without_closing = &trimmed[..trimmed.len() - 1];
                            new_lines.push(format!("resourcePacks:{without_closing},\"{tml_pack}\"]"));
                        } else {
                            new_lines.push(line.to_string());
                        }
                    } else {
                        new_lines.push(line.to_string());
                    }
                } else {
                    new_lines.push(line.to_string());
                }
            }
            if !found_packs {
                new_lines.push(format!("resourcePacks:[\"vanilla\",\"{tml_pack}\"]"));
            }
            let _ = std::fs::write(&options_file, new_lines.join("\n") + "\n");
        }
    }
}

/// CRC-32 (IEEE), to tell one skin from the next.
fn crc32(bytes: &[u8]) -> u32 {
    let mut crc = !0u32;
    for &b in bytes {
        crc ^= u32::from(b);
        for _ in 0..8 {
            crc = if crc & 1 != 0 {
                (crc >> 1) ^ 0xedb8_8320
            } else {
                crc >> 1
            };
        }
    }
    !crc
}

/// A zip the app carries over `dir`, its entries kept inside it.
fn unpack(bytes: &[u8], dir: &Path) -> Result<(), String> {
    let mut zip = zip::ZipArchive::new(std::io::Cursor::new(bytes)).map_err(|e| e.to_string())?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| e.to_string())?;
        let Some(name) = entry.enclosed_name() else {
            return Err(format!(
                "The app's archive names {}",
                entry
                    .name()
                    .map_or_else(|e| e.to_string(), |n| n.into_owned())
            ));
        };
        let path = dir.join(name);
        if entry.is_dir() {
            continue;
        }
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| format!("{}: {e}", parent.display()))?;
        }
        let mut file =
            std::fs::File::create(&path).map_err(|e| format!("{}: {e}", path.display()))?;
        std::io::copy(&mut entry, &mut file).map_err(|e| format!("{}: {e}", path.display()))?;
    }
    Ok(())
}