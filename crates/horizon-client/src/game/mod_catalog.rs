use super::Game;
use horizon_client::hud::HudBatch;

#[derive(Default)]
pub struct Catalog;

impl Catalog {
    pub fn new() -> Self { Self }
}

impl Game<'_> {
    pub fn poll_catalog(&mut self) {}
    pub fn draw_catalog(&mut self, _batch: &mut HudBatch) {}
    pub fn catalog_touch(&mut self, _t: &winit::event::Touch) -> bool { false }
    pub fn catalog_back(&mut self) -> bool { false }
    pub fn catalog_typing(&self) -> bool { false }
}
