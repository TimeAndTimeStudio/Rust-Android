# แผน: tml-client — รองรับ LOD ที่สร้างไว้ล่วงหน้าจากเซิร์ฟเวอร์ + Distant Horizons (TML Edition)

> สถานะ: **แผนอย่างเป็นทางการ (สรุปทิศทางตามมติเรียบร้อย)**
> เป้าหมายโค้ด: `/home/timeandtime/Androida/tml-client` (Rust workspace, fork Horizon/Starling, MC Java 26.3, Android)
> คู่กับแผนฝั่งเซิร์ฟเวอร์: [PLAN_LOD_PREGEN_SERVER.md](PLAN_LOD_PREGEN_SERVER.md)

---

## 1. ข้อสรุปทิศทางการออกแบบฝั่งไคลเอนต์ (Client Design Decisions)

1. **Brand Name:**
   - ใช้ Brand **`tml`** ในการส่ง handshake `minecraft:brand` ไปยังเซิร์ฟเวอร์
2. **Channel v2 `tml:lod`:**
   - ลงทะเบียนรับ-ส่ง Packet ผ่านช่องทาง **`tml:lod`**
   - รองรับโหมด **Delta Sync / Progressive Push**: รับเฉพาะชังก์หรือระดับ LOD ที่ไคลเอนต์ยังไม่มี หรือส่วนที่เซิร์ฟเวอร์เพิ่งสร้างเพิ่มเติม
3. **การจัดการแคชในเครื่องผู้ใช้ (Disk Cache):**
   - บันทึก LOD ที่ได้รับลง Storage บนมือถือ/คอมพิวเตอร์อย่างถาวร
   - เมื่อเข้าเกมใหม่ ไคลเอนต์จะดึงแคชเดิมขึ้นมาแสดงผลทันทีโดยไม่ต้องดาวน์โหลดซ้ำ และส่ง Revision ให้เซิร์ฟเวอร์ตรวจสอบเฉพาะส่วนอัปเดต
4. **Distant Horizons TML Fork:**
   - โคลน Distant Horizons (`https://gitlab.com/distant-horizons-team/distant-horizons`) มาทำต่อ (Fork)
   - ปรับแต่งและเชื่อมต่อ Engine ให้เป็นสถาปัตยกรรมแบบ **TML** เพื่อให้เข้ากันได้ 100% กับทั้ง Fabric Server และ tml-client (Rust/Android)

---

## 2. ลำดับขั้นตอนการพัฒนา (Roadmap)

### Phase 1: การโคลนและดัดแปลง Distant Horizons (TML Fork)
- โคลน Git repository ของ Distant Horizons เข้ามาไว้ในชุดพัฒนา
- แยก Core Data Structure (SQLite / FullDataSource / QuadTree LOD format)
- ปรับจูน Network Protocol ให้สื่อสารผ่านช่องทาง `tml:lod` และรองรับสตรีมมิ่งบนมือถือ

### Phase 2: tml-client Storage & Disk Cache
- เพิ่มโมดูล Disk Cache ใน `crates/horizon-client-render/src/lod/cache.rs`
- จัดเก็บข้อมูลแยกตาม Server Address + World ID + Dimension
- ระบบตรวจสอบ Revision เพื่อขอรับเฉพาะส่วนที่ขาดจากเซิร์ฟเวอร์

### Phase 3: Network Handler (`tml:lod`)
- ดักจับ Packet จาก Channel `tml:lod`
- รองรับการถอดรหัส Zstandard (zstd) เพื่อประสิทธิภาพสูงสุดบน Android
- รองรับคำสั่ง Invalidation เมื่อผู้เล่นหรือเซิร์ฟเวอร์มีการปรับเปลี่ยนบล็อกในโลกจริง

---

## 3. สรุปสถานะ
- [x] อัปเดตแผน Brand เป็น `tml`
- [x] อัปเดตการรองรับ Protocol v2 `tml:lod`
- [x] กำหนดแนวทางโคลน Distant Horizons มาทำต่อในแบบ TML
- [ ] รออนุมัติก่อนเริ่มพัฒนาโค้ด (ตามคำสั่ง: "อย่าเพิ่งทำ")
