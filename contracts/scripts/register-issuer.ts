/**
 * Đăng ký một địa chỉ ví làm bên cấp phát trong `IssuerRegistry` trên Amoy.
 *
 *     npm run register-issuer:amoy
 *
 * Địa chỉ và tên lấy từ biến môi trường `ISSUER_ADDRESS` / `ISSUER_NAME`; bỏ trống
 * `ISSUER_ADDRESS` thì dùng chính ví đang ký.
 *
 * ⚠️ GIAO DỊCH GHI LÊN CHUỖI CÔNG KHAI, KHÔNG HOÀN TÁC ĐƯỢC.
 *
 * `registerIssuer` cố ý không có hàm xóa — thu hồi quyền của một đơn vị chỉ tắt cờ `active`,
 * chứ không làm biến mất dấu vết đơn vị đó từng có quyền. Nếu xóa được thì mọi credential đã
 * cấp trở nên không truy nguyên được. Nghĩa là: đăng ký nhầm một địa chỉ thì địa chỉ đó nằm
 * lại vĩnh viễn trong `listIssuers()`, chỉ tắt được chứ không gỡ.
 *
 * Vì sao đây là bước bắt buộc chứ không phải thủ tục: verifier phục hồi địa chỉ ví từ chữ ký
 * rồi hỏi contract này xem địa chỉ đó có quyền không (`docs/canonicalization.md` §12.2). Chưa
 * đăng ký thì mọi credential do ví đó ký đều bị verifier báo "không có quyền cấp" — trông y
 * hệt credential giả.
 */
import hre, { network } from "hardhat";

const AMOY_CHAIN_ID = 80002n;

async function main() {
  const networkName = (hre as any).globalOptions?.network;
  if (networkName === undefined || networkName === "default") {
    throw new Error(
      "Chưa chọn mạng. Chạy `npm run register-issuer:amoy`, đừng chạy thẳng script này —\n" +
        "không có --network thì nó ghi lên chuỗi mô phỏng trong bộ nhớ rồi vứt đi.",
    );
  }

  const { ethers } = await network.create();
  const provider = ethers.provider;

  const chainId = (await provider.getNetwork()).chainId;
  if (chainId !== AMOY_CHAIN_ID) {
    throw new Error(`Sai chuỗi: chainId = ${chainId}, cần ${AMOY_CHAIN_ID} (Polygon Amoy).`);
  }

  const [signer] = await ethers.getSigners();
  if (signer === undefined) {
    throw new Error("Không có signer — `DEPLOYER_PRIVATE_KEY` rỗng trong .env?");
  }

  const registryAddress = process.env.ISSUER_REGISTRY_ADDRESS;
  if (!registryAddress) {
    throw new Error("Thiếu ISSUER_REGISTRY_ADDRESS trong .env.");
  }

  const issuer = process.env.ISSUER_ADDRESS || signer.address;
  const name = process.env.ISSUER_NAME || "Doan Thanh nien";

  const registry = await ethers.getContractAt("IssuerRegistry", registryAddress, signer);

  console.log(`Mạng      : ${networkName} (chainId ${chainId})`);
  console.log(`Contract  : ${registryAddress}`);
  console.log(`Ví ký     : ${signer.address}`);
  console.log(`Đăng ký   : ${issuer}`);
  console.log(`Tên       : ${name}`);
  console.log();

  // Ba phép kiểm TRƯỚC khi gửi. Giao dịch revert vẫn tốn gas, và quan trọng hơn là thông báo
  // lỗi của contract (`AlreadyRegistered`, `AccessControlUnauthorizedAccount`) đọc rất khó
  // qua explorer nếu không biết trước mình đang tìm gì.
  const registrarRole = await registry.REGISTRAR_ROLE();
  if (!(await registry.hasRole(registrarRole, signer.address))) {
    throw new Error(
      `Ví ${signer.address} không có REGISTRAR_ROLE trên contract này.\n` +
        "Chỉ ví admin lúc deploy mới có, và nó cấp được cho ví khác bằng grantRole().",
    );
  }

  const truoc = await registry.getIssuer(issuer);
  if (truoc.registered) {
    console.log(`Địa chỉ này ĐÃ đăng ký rồi (active = ${truoc.active}, tên "${truoc.name}").`);
    console.log("Không gửi giao dịch. `registerIssuer` revert với AlreadyRegistered nếu gọi lại.");
    if (!truoc.active) {
      console.log("\n⚠️  Đang ở trạng thái TẮT — verifier sẽ báo 'không có quyền cấp'.");
      console.log("    Bật lại bằng setIssuerActive(address, true).");
    }
    return;
  }

  const balance = Number(ethers.formatEther(await provider.getBalance(signer.address)));
  console.log(`Số dư     : ${balance.toFixed(4)} POL`);
  if (balance < 0.01) {
    throw new Error("Số dư dưới 0,01 POL — lấy thêm từ faucet Amoy.");
  }

  console.log("\n⚠️  KHÔNG HOÀN TÁC ĐƯỢC. Địa chỉ đăng ký nhầm chỉ tắt được, không gỡ được.");
  console.log("Gửi giao dịch...\n");

  const tx = await registry.registerIssuer(issuer, name);
  console.log(`tx        : ${tx.hash}`);
  const receipt = await tx.wait();

  console.log(`block     : ${receipt!.blockNumber}`);
  console.log(`gas       : ${receipt!.gasUsed.toString()}`);
  console.log(`explorer  : https://amoy.polygonscan.com/tx/${tx.hash}`);

  const sau = await registry.isActiveIssuer(issuer);
  console.log(`\nisActiveIssuer(${issuer}) = ${sau}`);
  if (!sau) {
    throw new Error("Đăng ký xong nhưng isActiveIssuer trả về false — có gì đó rất sai.");
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
