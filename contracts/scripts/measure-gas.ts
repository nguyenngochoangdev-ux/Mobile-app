/**
 * Đo gas THUẦN LOCAL — không cần Amoy, không cần POL, không cần RPC.
 *
 * Đây là phương án dự phòng mà cổng kiểm soát cuối tuần 3 (PROJECT.md §7) đã định sẵn:
 * nếu chưa deploy được thì phép đo #1 và #2 (§8) vẫn ra đủ số cho chương 11. Chạy:
 *
 *     npm run gas
 *
 * In ra sẵn dạng bảng Markdown để dán thẳng vào `docs/measurements.md`.
 *
 * GIỚI HẠN PHẢI GHI VÀO BÁO CÁO, ĐỪNG GIẤU: đây là gas đo trên EVM mô phỏng của Hardhat.
 * Gas là đại lượng của EVM nên con số khớp với Amoy, nhưng CHI PHÍ bằng POL thì không —
 * nó còn phụ thuộc giá gas lúc gửi. Mọi quy đổi ra tiền dưới đây là ước lượng ở một mức
 * giá giả định, không phải số đo.
 */
import { network } from "hardhat";

/** Giá gas giả định để quy đổi. Amoy thường ~25–30 gwei; lấy 30 cho ước lượng thận trọng. */
const GAS_PRICE_GWEI = 30n;

const ATTEND = "0x415454454e440000"; // docs/canonicalization.md §2

function polCost(gas: bigint): string {
  const wei = gas * GAS_PRICE_GWEI * 10n ** 9n;
  return (Number(wei) / 1e18).toFixed(6);
}

async function main() {
  const { ethers } = await network.create();
  const [admin] = await ethers.getSigners();
  const out: string[] = [];
  const log = (s = "") => {
    out.push(s);
    console.log(s);
  };

  // ---------------------------------------------------------------- deploy
  log("### Gas triển khai");
  log();
  log("| Contract | Gas | POL @ 30 gwei |");
  log("|---|---:|---:|");

  const deployed: Record<string, any> = {};
  for (const name of ["AnchorRegistry", "IssuerRegistry", "StatusList", "StatusListMapping"]) {
    const c = await ethers.deployContract(name, [admin!.address]);
    const receipt = await c.deploymentTransaction()!.wait();
    deployed[name] = c;
    const note = name === "StatusListMapping" ? " *(đối chứng, không deploy thật)*" : "";
    log(`| \`${name}\`${note} | ${receipt!.gasUsed.toLocaleString("en-US")} | ${polCost(receipt!.gasUsed)} |`);
  }

  // ------------------------------------------------- phép đo #1: neo Merkle
  const anchor = deployed.AnchorRegistry;
  const root = "0x" + "ab".repeat(32);

  // Lô đầu tiên của một miền đắt hơn: nó khởi tạo luôn ô đếm `_batchCount` (0 → khác 0).
  // Tách riêng, vì trong vận hành thật chỉ có 5 lần như thế trong cả đời hệ thống.
  const first = await (await anchor.anchor(ATTEND, 1n, root, 10)).wait();
  const steady = await (await anchor.anchor(ATTEND, 2n, root, 10)).wait();

  log();
  log("### Phép đo #1 — chi phí neo theo kích thước lô");
  log();
  log(`Gas neo lô đầu tiên của một miền: **${first!.gasUsed.toLocaleString("en-US")}** (khởi tạo bộ đếm)`);
  log(`Gas neo ở trạng thái ổn định: **${steady!.gasUsed.toLocaleString("en-US")}**`);
  log();
  log("Chi phí neo **không phụ thuộc số bản ghi trong lô** — cây Merkle dựng off-chain, on-chain");
  log("chỉ nhận đúng 32 byte root. Đây là kết quả chính của phép đo:");
  log();
  log("| N bản ghi/lô | Gas cả lô | Gas/bản ghi | POL/bản ghi @ 30 gwei |");
  log("|---:|---:|---:|---:|");
  for (const n of [10n, 100n, 1000n, 5000n]) {
    const perRecord = Number(steady!.gasUsed) / Number(n);
    log(
      `| ${n.toLocaleString("en-US")} | ${steady!.gasUsed.toLocaleString("en-US")} | ` +
        `${perRecord.toFixed(1)} | ${(Number(polCost(steady!.gasUsed)) / Number(n)).toFixed(8)} |`,
    );
  }

  // ------------------------------------ phép đo #2: bitmap vs mapping
  log();
  log("### Phép đo #2 — thu hồi: bitmap vs mapping-per-credential");
  log();
  log("Hai contract cùng `IStatusList`, cùng `AccessControl`, cùng sự kiện — khác đúng cách");
  log("lưu trữ (xem bộ test dùng chung trong `test/StatusList.test.ts`).");
  log();
  log("| Kịch bản | Bitmap (gas) | Mapping (gas) | Chênh |");
  log("|---|---:|---:|---:|");

  type Scenario = { label: string; indices: bigint[] };
  const scenarios: Scenario[] = [
    { label: "Thu hồi 1 credential", indices: [1n] },
    // Gom cụm: 64 chỉ số liên tiếp nằm gọn trong 1 word 256-bit — trường hợp TỐT NHẤT của bitmap.
    { label: "64 chỉ số **gom cụm** (cùng 1 word)", indices: Array.from({ length: 64 }, (_, i) => BigInt(i)) },
    // Rải đều: mỗi chỉ số một word khác nhau — trường hợp XẤU NHẤT, và là trường hợp THẬT,
    // vì PROJECT.md §2.3 bắt cấp status_list_index ngẫu nhiên để không lộ thứ tự cấp phát.
    {
      label: "64 chỉ số **rải đều** (mỗi chỉ số 1 word)",
      indices: Array.from({ length: 64 }, (_, i) => BigInt(i) * 256n),
    },
  ];

  for (const s of scenarios) {
    // Deploy mới mỗi kịch bản: slot đã ghi rồi thì lần ghi sau rẻ hơn, đo lại trên contract
    // cũ sẽ ra số sai.
    const bm = await ethers.deployContract("StatusList", [admin!.address]);
    const mp = await ethers.deployContract("StatusListMapping", [admin!.address]);

    const bmGas = (await (await bm.setRevokedBatch(s.indices, true)).wait())!.gasUsed;
    const mpGas = (await (await mp.setRevokedBatch(s.indices, true)).wait())!.gasUsed;
    const ratio = Number(mpGas) / Number(bmGas);

    log(
      `| ${s.label} | ${bmGas.toLocaleString("en-US")} | ${mpGas.toLocaleString("en-US")} | ` +
        `bitmap rẻ hơn ${ratio.toFixed(2)}× |`,
    );
  }

  log();
  log("**Đọc bảng này cho đúng.** Kết luận KHÔNG phải \"bitmap rẻ hơn\", mà là: lợi thế của");
  log("bitmap phụ thuộc hoàn toàn vào việc các chỉ số bị thu hồi có nằm cùng word hay không.");
  log("Đề tài đã cố ý chọn cấp `status_list_index` **ngẫu nhiên** để sự kiện `StatusChanged`");
  log("không lộ thứ tự cấp phát (PROJECT.md §2.3) — tức là cố ý chọn đúng trường hợp xấu nhất");
  log("của bitmap. Đây là một đánh đổi có thật giữa quyền riêng tư và chi phí, và dòng");
  log("\"rải đều\" mới là con số phản ánh vận hành thật.");
  log();
  log("### Đọc bitmap");
  log();
  log("`getWord(w)` trả 256 trạng thái trong **một** `eth_call`; mapping cần **256** lần gọi");
  log("`isRevoked`. Với verifier tĩnh chạy trên RPC công cộng, đây mới là chênh lệch quan");
  log("trọng nhất — và nó không xuất hiện trong bảng gas vì đọc không tốn gas.");

  console.log("\n---\nDán khối trên vào docs/measurements.md (mục ch.11.4).");
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
