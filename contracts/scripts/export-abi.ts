/**
 * Xuất ABI + bytecode ra dạng web3j codegen đọc được.
 *
 *     npm run abi
 *
 * web3j nhận hai file rời (`.abi` và `.bin`), không nhận artifact JSON của Hardhat. Script
 * này tách chúng ra `contracts/build/` (dẫn xuất, đã gitignore).
 *
 * Sinh wrapper Java từ đó:
 *
 *     web3j generate solidity \
 *       -a contracts/build/AnchorRegistry.abi \
 *       -b contracts/build/AnchorRegistry.bin \
 *       -o backend/src/main/java \
 *       -p vn.ptit.drl.anchor.contracts.generated
 *
 * Gói `...anchor.contracts.generated` đã nằm trong .gitignore — wrapper là mã sinh, không
 * commit. Đặt nó DƯỚI module `anchor` là có chủ ý: ranh giới cứng của PROJECT.md §5 nói
 * `anchor` không import gì từ nghiệp vụ, chứ không cấm chiều ngược lại.
 */
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = `${HERE}/../build`;

/** Chỉ ba contract thật. `IStatusList` là interface, `StatusListMapping` chỉ để đo gas. */
const CONTRACTS = [
  ["AnchorRegistry", "contracts/AnchorRegistry.sol"],
  ["IssuerRegistry", "contracts/IssuerRegistry.sol"],
  ["StatusList", "contracts/StatusList.sol"],
] as const;

mkdirSync(OUT_DIR, { recursive: true });

for (const [name, source] of CONTRACTS) {
  const artifactPath = `${HERE}/../artifacts/${source}/${name}.json`;
  let artifact: { abi: unknown[]; bytecode: string };

  try {
    artifact = JSON.parse(readFileSync(artifactPath, "utf8"));
  } catch {
    throw new Error(`Chưa có artifact cho ${name}. Chạy \`npm run build\` trước.`);
  }

  writeFileSync(`${OUT_DIR}/${name}.abi`, JSON.stringify(artifact.abi), "utf8");
  // web3j muốn hex TRẦN, không có tiền tố 0x.
  writeFileSync(`${OUT_DIR}/${name}.bin`, artifact.bytecode.replace(/^0x/, ""), "utf8");

  console.log(`${name.padEnd(15)} -> build/${name}.abi + build/${name}.bin`);
}

console.log(`\nXong. ${CONTRACTS.length} contract trong contracts/build/.`);
