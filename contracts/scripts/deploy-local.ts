/**
 * Triển khai lên chuỗi Hardhat CỤC BỘ để thử đường ghi của backend.
 *
 *   npx hardhat node                       # cửa sổ 1
 *   npm run deploy:local                   # cửa sổ 2
 *
 * VÌ SAO CẦN: giao dịch `anchor()` là KHÔNG THỂ HOÀN TÁC — contract cố ý không cho ghi đè,
 * nên mỗi `(domain, batchId)` chỉ dùng được đúng một lần trên Amoy. Thử đường ghi ở đây
 * trước là cách duy nhất để gỡ lỗi mà không đốt vĩnh viễn một batchId thật.
 *
 * Chuỗi local dùng lại được vô hạn: `npx hardhat node` khởi động lại là sạch trơn.
 *
 * Khác `deploy.ts` ở chỗ: không kiểm chainId 80002, không kiểm số dư, và deploy CẢ
 * `StatusListMapping` để thử được mọi thứ.
 */
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

import hre, { network } from "hardhat";

const HERE = dirname(fileURLToPath(import.meta.url));

async function main() {
  const networkName = (hre as any).globalOptions?.network;
  if (networkName === undefined || networkName === "default") {
    throw new Error(
      "Chạy `npm run deploy:local` (nó truyền --network localhost).\n" +
        "Nhớ mở `npx hardhat node` ở một cửa sổ khác trước.",
    );
  }

  const { ethers } = await network.create();
  const chainId = (await ethers.provider.getNetwork()).chainId;

  if (chainId === 80002n) {
    throw new Error(
      "Đây là script cho chuỗi CỤC BỘ, nhưng đang trỏ vào Amoy (80002).\n" +
        "Dùng `npm run deploy:amoy` cho Amoy.",
    );
  }

  const [deployer] = await ethers.getSigners();
  console.log(`Mạng : ${networkName} (chainId ${chainId})`);
  console.log(`Ví   : ${deployer!.address}\n`);

  const results: Record<string, string> = {};
  for (const name of ["AnchorRegistry", "IssuerRegistry", "StatusList", "StatusListMapping"]) {
    const c = await ethers.deployContract(name, [deployer!.address]);
    await c.waitForDeployment();
    results[name] = await c.getAddress();
    console.log(`${name.padEnd(18)} ${results[name]}`);
  }

  const outPath = `${HERE}/../deployments/${networkName}.json`;
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(
    outPath,
    JSON.stringify(
      { network: networkName, chainId: Number(chainId), deployer: deployer!.address, contracts: results },
      null,
      2,
    ) + "\n",
    "utf8",
  );

  console.log("\n--- Biến môi trường cho backend ---");
  console.log(`ANCHOR_ENABLED=true`);
  console.log(`AMOY_RPC_URL=http://127.0.0.1:8545`);
  console.log(`CHAIN_ID=${chainId}`);
  console.log(`ANCHOR_REGISTRY_ADDRESS=${results.AnchorRegistry}`);
  console.log(`ISSUER_REGISTRY_ADDRESS=${results.IssuerRegistry}`);
  console.log(`STATUS_LIST_ADDRESS=${results.StatusList}`);
  console.log(
    "\nKhóa của ví trên là khóa công khai ai cũng biết của Hardhat — chỉ dùng ở chuỗi local.",
  );
}

main().catch((e) => {
  console.error(`\n${e instanceof Error ? e.message : e}`);
  process.exitCode = 1;
});
