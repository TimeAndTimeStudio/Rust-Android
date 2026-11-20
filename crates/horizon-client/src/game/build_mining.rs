//! Dummy replacement for removed mining minigame
use std::time::Instant;
#[derive(Default)]
pub struct BuildMining;
impl BuildMining {
    pub fn new() -> Self { Self }
    pub fn update(&mut self, _now: Instant) {}
    pub fn take_sounds(&mut self) -> Vec<super::setup::fishing::Sound> { Vec::new() }
}
