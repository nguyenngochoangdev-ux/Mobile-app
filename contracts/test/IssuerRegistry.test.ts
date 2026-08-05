import { expect } from "chai";
import { network } from "hardhat";

const ZERO_ADDRESS = "0x" + "00".repeat(20);

describe("IssuerRegistry", () => {
  let ethers: any;
  let registry: any;
  let admin: any;
  let registrar: any;
  let khoaCntt: any;
  let clb: any;
  let outsider: any;

  before(async () => {
    ({ ethers } = await network.create());
  });

  beforeEach(async () => {
    [admin, registrar, khoaCntt, clb, outsider] = await ethers.getSigners();
    registry = await ethers.deployContract("IssuerRegistry", [admin.address]);
    await registry.grantRole(await registry.REGISTRAR_ROLE(), registrar.address);
  });

  describe("đăng ký", () => {
    it("bên mới đăng ký xong là đang hoạt động", async () => {
      await registry.connect(registrar).registerIssuer(khoaCntt.address, "Khoa CNTT");

      expect(await registry.isActiveIssuer(khoaCntt.address)).to.equal(true);

      const issuer = await registry.getIssuer(khoaCntt.address);
      expect(issuer.registered).to.equal(true);
      expect(issuer.active).to.equal(true);
      expect(issuer.name).to.equal("Khoa CNTT");
      expect(issuer.since).to.be.greaterThan(0n);
    });

    it("phát sự kiện IssuerRegistered", async () => {
      await expect(registry.connect(registrar).registerIssuer(clb.address, "CLB Tin hoc"))
        .to.emit(registry, "IssuerRegistered")
        .withArgs(clb.address, "CLB Tin hoc");
    });

    it("địa chỉ chưa đăng ký thì không phải bên cấp phát", async () => {
      expect(await registry.isActiveIssuer(outsider.address)).to.equal(false);
    });

    it("không đăng ký trùng", async () => {
      await registry.connect(registrar).registerIssuer(khoaCntt.address, "Khoa CNTT");

      await expect(registry.connect(registrar).registerIssuer(khoaCntt.address, "Khoa CNTT v2"))
        .to.be.revertedWithCustomError(registry, "AlreadyRegistered")
        .withArgs(khoaCntt.address);
    });

    it("từ chối địa chỉ 0 và tên rỗng", async () => {
      await expect(
        registry.connect(registrar).registerIssuer(ZERO_ADDRESS, "Ten hop le"),
      ).to.be.revertedWithCustomError(registry, "ZeroAddress");

      await expect(
        registry.connect(registrar).registerIssuer(khoaCntt.address, ""),
      ).to.be.revertedWithCustomError(registry, "EmptyName");
    });
  });

  describe("bật/tắt quyền cấp phát", () => {
    beforeEach(async () => {
      await registry.connect(registrar).registerIssuer(khoaCntt.address, "Khoa CNTT");
    });

    it("tắt rồi bật lại được", async () => {
      await expect(registry.connect(registrar).setIssuerActive(khoaCntt.address, false))
        .to.emit(registry, "IssuerActiveChanged")
        .withArgs(khoaCntt.address, false);
      expect(await registry.isActiveIssuer(khoaCntt.address)).to.equal(false);

      await registry.connect(registrar).setIssuerActive(khoaCntt.address, true);
      expect(await registry.isActiveIssuer(khoaCntt.address)).to.equal(true);
    });

    it("tắt KHÔNG xóa dấu vết là bên đó từng có quyền", async () => {
      // Nếu thu hồi quyền làm biến mất bản ghi thì mọi credential khoa này đã cấp trở nên
      // không truy nguyên được — và đó chính là thứ đề tài hứa sẽ làm được.
      await registry.connect(registrar).setIssuerActive(khoaCntt.address, false);

      const issuer = await registry.getIssuer(khoaCntt.address);
      expect(issuer.registered).to.equal(true);
      expect(issuer.name).to.equal("Khoa CNTT");
      expect(await registry.listIssuers()).to.include(khoaCntt.address);
    });

    it("không bật/tắt được bên chưa đăng ký", async () => {
      await expect(registry.connect(registrar).setIssuerActive(outsider.address, true))
        .to.be.revertedWithCustomError(registry, "NotRegistered")
        .withArgs(outsider.address);
    });
  });

  describe("liệt kê — verifier không dùng được eth_getLogs nên danh sách phải ở storage", () => {
    it("trả về mọi bên từng đăng ký, kể cả bên đã tắt", async () => {
      await registry.connect(registrar).registerIssuer(khoaCntt.address, "Khoa CNTT");
      await registry.connect(registrar).registerIssuer(clb.address, "CLB Tin hoc");
      await registry.connect(registrar).setIssuerActive(clb.address, false);

      const list = await registry.listIssuers();
      expect(list).to.deep.equal([khoaCntt.address, clb.address]);
      expect(await registry.issuerCount()).to.equal(2n);
    });

    it("danh sách rỗng lúc mới deploy", async () => {
      expect(await registry.issuerCount()).to.equal(0n);
      expect(await registry.listIssuers()).to.deep.equal([]);
    });
  });

  describe("phân quyền", () => {
    it("người ngoài không đăng ký được bên cấp phát", async () => {
      await expect(
        registry.connect(outsider).registerIssuer(outsider.address, "Toi tu phong"),
      ).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
    });

    it("người ngoài không tắt được bên cấp phát hợp lệ", async () => {
      await registry.connect(registrar).registerIssuer(khoaCntt.address, "Khoa CNTT");

      await expect(
        registry.connect(outsider).setIssuerActive(khoaCntt.address, false),
      ).to.be.revertedWithCustomError(registry, "AccessControlUnauthorizedAccount");
    });
  });
});
