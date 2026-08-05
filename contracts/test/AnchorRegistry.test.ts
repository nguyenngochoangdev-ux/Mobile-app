import { expect } from "chai";
import { network } from "hardhat";

import { DOMAINS, domainBytes8, fakeRoot } from "./helpers.js";

const ATTEND = domainBytes8("ATTEND");
const SCORE = domainBytes8("SCORE");
const ZERO32 = "0x" + "00".repeat(32);

describe("AnchorRegistry", () => {
  let ethers: any;
  let registry: any;
  let admin: any;
  let anchorer: any;
  let outsider: any;

  // `create()` một lần cho cả file: chuỗi mô phỏng riêng, không dính trạng thái của file
  // test khác. Cách ly giữa các test do `beforeEach` deploy contract mới đảm nhiệm.
  before(async () => {
    ({ ethers } = await network.create());
  });

  beforeEach(async () => {
    [admin, anchorer, outsider] = await ethers.getSigners();
    registry = await ethers.deployContract("AnchorRegistry", [admin.address]);
    await registry.grantRole(await registry.ANCHOR_ROLE(), anchorer.address);
  });

  describe("mã hóa miền neo", () => {
    // Chốt CỨNG giá trị bytes8. Nếu ai đó đổi cách mã hóa ở bất kỳ tầng nào (Java, JS hay
    // Solidity) thì test này đỏ — trước khi Merkle proof kịp fail im lặng ở tuần 6.
    // Nguồn sự thật: docs/canonicalization.md §2.
    const EXPECTED: Record<string, string> = {
      ATTEND: "0x415454454e440000",
      CRED: "0x4352454400000000",
      SCORE: "0x53434f5245000000",
      AUDIT: "0x4155444954000000",
      RULESET: "0x52554c4553455400",
    };

    for (const d of DOMAINS) {
      it(`${d} khớp giá trị đã chốt trong docs/canonicalization.md`, () => {
        expect(domainBytes8(d)).to.equal(EXPECTED[d]);
      });
    }

    it("mỗi miền là một không gian lô riêng biệt", async () => {
      await registry.connect(anchorer).anchor(ATTEND, 1n, fakeRoot(0xaa), 10);
      await registry.connect(anchorer).anchor(SCORE, 1n, fakeRoot(0xbb), 20);

      expect(await registry.getRoot(ATTEND, 1n)).to.equal(fakeRoot(0xaa));
      expect(await registry.getRoot(SCORE, 1n)).to.equal(fakeRoot(0xbb));
    });
  });

  describe("neo", () => {
    it("lưu root và đọc lại được bằng (domain, batchId)", async () => {
      const root = fakeRoot(0x1234);
      await registry.connect(anchorer).anchor(ATTEND, 42n, root, 500);

      expect(await registry.getRoot(ATTEND, 42n)).to.equal(root);
      expect(await registry.batchCount(ATTEND)).to.equal(1n);
    });

    it("phát sự kiện Anchored kèm leafCount", async () => {
      await expect(registry.connect(anchorer).anchor(ATTEND, 7n, fakeRoot(0x99), 123))
        .to.emit(registry, "Anchored")
        .withArgs(ATTEND, 7n, fakeRoot(0x99), 123n);
    });

    it("lô chưa neo trả bytes32(0) — không nhập nhằng, vì root = 0 bị chặn khi ghi", async () => {
      expect(await registry.getRoot(ATTEND, 999n)).to.equal(ZERO32);
    });
  });

  describe("bất biến — chỗ luận điểm 1 sống hay chết", () => {
    it("KHÔNG ghi đè được lô đã neo", async () => {
      await registry.connect(anchorer).anchor(ATTEND, 1n, fakeRoot(0x11), 10);

      await expect(registry.connect(anchorer).anchor(ATTEND, 1n, fakeRoot(0x22), 10))
        .to.be.revertedWithCustomError(registry, "RootAlreadyAnchored")
        .withArgs(ATTEND, 1n, fakeRoot(0x11));

      expect(await registry.getRoot(ATTEND, 1n)).to.equal(fakeRoot(0x11));
    });

    it("kể cả DEFAULT_ADMIN_ROLE cũng không ghi đè được", async () => {
      // Test này đỏ = đề tài mất luận điểm "chống sửa hồi tố bởi chính người quản trị",
      // tức là mất lý do tồn tại. Đừng "sửa" nó bằng cách nới điều kiện.
      await registry.connect(anchorer).anchor(ATTEND, 1n, fakeRoot(0x11), 10);

      await expect(
        registry.connect(admin).anchor(ATTEND, 1n, fakeRoot(0x22), 10),
      ).to.be.revertedWithCustomError(registry, "RootAlreadyAnchored");
    });

    it("không tồn tại hàm sửa/xóa root nào trong ABI", async () => {
      const names = registry.interface.fragments
        .filter((f: any) => f.type === "function")
        .map((f: any) => f.name);

      for (const forbidden of ["setRoot", "updateRoot", "deleteRoot", "removeRoot"]) {
        expect(names).to.not.include(forbidden);
      }
    });
  });

  describe("kiểm tra đầu vào", () => {
    it("từ chối root rỗng", async () => {
      await expect(
        registry.connect(anchorer).anchor(ATTEND, 1n, ZERO32, 10),
      ).to.be.revertedWithCustomError(registry, "EmptyRoot");
    });

    it("từ chối lô rỗng", async () => {
      await expect(
        registry.connect(anchorer).anchor(ATTEND, 1n, fakeRoot(0x11), 0),
      ).to.be.revertedWithCustomError(registry, "EmptyBatch");
    });
  });

  describe("phân quyền", () => {
    it("người ngoài không neo được", async () => {
      await expect(
        registry.connect(outsider).anchor(ATTEND, 1n, fakeRoot(0x11), 10),
      ).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
    });

    it("admin thu hồi được ANCHOR_ROLE — khóa job neo bị lộ thì cắt được", async () => {
      await registry.revokeRole(await registry.ANCHOR_ROLE(), anchorer.address);

      await expect(
        registry.connect(anchorer).anchor(ATTEND, 1n, fakeRoot(0x11), 10),
      ).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
    });
  });
});
