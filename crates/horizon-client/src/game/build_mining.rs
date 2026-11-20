use std::sync::Arc;

#[derive(Default)]
pub struct BuildMining;

impl BuildMining {
    pub fn new() -> Self { Self }
    pub fn prepare(&mut self, _w: f32, _h: f32) {}
    pub fn update(&mut self, _now: std::time::Instant) {}
    pub fn take_sounds(&mut self) -> Vec<super::setup::fishing::Sound> { Vec::new() }
}
