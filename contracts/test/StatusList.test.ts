import { expect } from "chai";
import { network } from "hardhat";

/**
 * Bộ test hành vi chạy hai lần: một lần cho `StatusList` (bitmap, bản thật) và một lần cho
 * `StatusListMapping` (đối chứng đo gas).
 *
 * Không phải viết cho đẹp. Phép đo #2 (PROJECT.md §8) chỉ có nghĩa nếu hai contract làm
 * ĐÚNG CÙNG một việc — so gas giữa hai thứ hành xử khác nhau là con số vô nghĩa. Bộ test
 * dùng chung này chính là bằng chứng cho tính công bằng của phép đo, và nên nói tới nó
 * trong phần phương pháp của ch.11.4.
 */
const IMPLEMENTATIONS = ["StatusList", "StatusListMapping"] as const;

for (const contractName of IMPLEMENTATIONS) {
  describe(`${contractName} — hành vi chung IStatusList`, () => {
    let ethers: any;
    let list: any;
    let admin: any;
    let operator: any;
    let outsider: any;

    before(async () => {
      ({ ethers } = await network.create());
    });

    beforeEach(async () => {
      [admin, operator, outsider] = await ethers.getSigners();
      list = await ethers.deployContract(contractName, [admin.address]);
      await list.grantRole(await list.STATUS_ROLE(), operator.address);
    });

    it("mặc định mọi chỉ số là chưa thu hồi", async () => {
      expect(await list.isRevoked(0n)).to.equal(false);
      expect(await list.isRevoked(12345n)).to.equal(false);
      expect(await list.isRevoked(2n ** 200n)).to.equal(false);
    });

    it("thu hồi rồi bỏ thu hồi được", async () => {
      await list.connect(operator).setRevoked(42n, true);
      expect(await list.isRevoked(42n)).to.equal(true);

      await list.connect(operator).setRevoked(42n, false);
      expect(await list.isRevoked(42n)).to.equal(false);
    });

    it("thu hồi một chỉ số không đụng tới chỉ số khác", async () => {
      await list.connect(operator).setRevoked(100n, true);

      expect(await list.isRevoked(99n)).to.equal(false);
      expect(await list.isRevoked(101n)).to.equal(false);
      // Hàng xóm trong cùng word 256-bit — chỗ dễ sai nhất của bitmap nếu tính lệch mask.
      expect(await list.isRevoked(0n)).to.equal(false);
      expect(await list.isRevoked(255n)).to.equal(false);
    });

    it("xử lý đúng biên word: 255 / 256 / 257", async () => {
      await list.connect(operator).setRevoked(256n, true);

      expect(await list.isRevoked(255n)).to.equal(false);
      expect(await list.isRevoked(256n)).to.equal(true);
      expect(await list.isRevoked(257n)).to.equal(false);
    });

    it("chỉ số rất lớn vẫn hoạt động", async () => {
      const huge = 2n ** 255n + 7n;
      await list.connect(operator).setRevoked(huge, true);
      expect(await list.isRevoked(huge)).to.equal(true);
    });

    it("phát StatusChanged khi trạng thái đổi", async () => {
      await expect(list.connect(operator).setRevoked(7n, true))
        .to.emit(list, "StatusChanged")
        .withArgs(7n, true);
    });

    it("KHÔNG phát sự kiện khi trạng thái không đổi", async () => {
      await list.connect(operator).setRevoked(7n, true);

      await expect(list.connect(operator).setRevoked(7n, true)).to.not.emit(
        list,
        "StatusChanged",
      );
    });

    describe("thu hồi hàng loạt", () => {
      it("thu hồi mọi chỉ số trong lô", async () => {
        const indices = [1n, 2n, 3n, 500n, 100000n];
        await list.connect(operator).setRevokedBatch(indices, true);

        for (const i of indices) {
          expect(await list.isRevoked(i)).to.equal(true);
        }
        expect(await list.isRevoked(4n)).to.equal(false);
      });

      it("bỏ thu hồi hàng loạt", async () => {
        const indices = [10n, 20n, 30n];
        await list.connect(operator).setRevokedBatch(indices, true);
        await list.connect(operator).setRevokedBatch(indices, false);

        for (const i of indices) {
          expect(await list.isRevoked(i)).to.equal(false);
        }
      });

      it("từ chối lô rỗng", async () => {
        await expect(
          list.connect(operator).setRevokedBatch([], true),
        ).to.be.revertedWithCustomError(list, "EmptyBatch");
      });

      it("chỉ số trùng trong lô không gây lỗi", async () => {
        await list.connect(operator).setRevokedBatch([5n, 5n, 5n], true);
        expect(await list.isRevoked(5n)).to.equal(true);
      });
    });

    describe("phân quyền", () => {
      it("người ngoài không thu hồi được", async () => {
        await expect(
          list.connect(outsider).setRevoked(1n, true),
        ).to.be.revertedWithCustomError(list, "AccessControlUnauthorizedAccount");
      });

      it("người ngoài không thu hồi hàng loạt được", async () => {
        await expect(
          list.connect(outsider).setRevokedBatch([1n, 2n], true),
        ).to.be.revertedWithCustomError(list, "AccessControlUnauthorizedAccount");
      });
    });
  });
}

describe("StatusList — phần riêng của bitmap", () => {
  let ethers: any;
  let list: any;
  let admin: any;

  before(async () => {
    ({ ethers } = await network.create());
  });

  beforeEach(async () => {
    [admin] = await ethers.getSigners();
    list = await ethers.deployContract("StatusList", [admin.address]);
  });

  it("getWord trả 256 trạng thái trong MỘT lần đọc — lý do chính chọn bitmap", async () => {
    // Verifier tĩnh chạy trên RPC công cộng: mỗi vòng gọi mạng là một khoản phải trả.
    // Kiểm tra 256 credential bằng 1 eth_call thay vì 256 lần.
    await list.setRevokedBatch([0n, 1n, 255n], true);

    const word0: bigint = await list.getWord(0n);
    expect(word0 & 1n).to.equal(1n);
    expect((word0 >> 1n) & 1n).to.equal(1n);
    expect((word0 >> 2n) & 1n).to.equal(0n);
    expect((word0 >> 255n) & 1n).to.equal(1n);
  });

  it("getWord phân tách đúng theo word", async () => {
    await list.setRevoked(256n, true); // bit 0 của word 1

    expect(await list.getWord(0n)).to.equal(0n);
    expect(await list.getWord(1n)).to.equal(1n);
  });

  it("bit thứ k của word w ứng với chỉ số w*256 + k", async () => {
    const index = 3n * 256n + 77n;
    await list.setRevoked(index, true);

    const word3: bigint = await list.getWord(3n);
    expect((word3 >> 77n) & 1n).to.equal(1n);
    expect(word3).to.equal(1n << 77n);
  });
});
