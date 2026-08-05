/**
 * Triển khai 3 contract lên Polygon Amoy.
 *
 *     npm run deploy:amoy
 *
 * Yêu cầu trong `.env` GỐC REPO: `AMOY_RPC_URL` và `DEPLOYER_PRIVATE_KEY`.
 *
 * `StatusListMapping` KHÔNG nằm trong danh sách — nó chỉ là đối chứng cho phép đo gas, chạy
 * thuần local. Đưa nó lên chuỗi là đốt POL cho một thứ không ai gọi.
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

import hre, { network } from "hardhat";

const HERE = dirname(fileURLToPath(import.meta.url));
const AMOY_CHAIN_ID = 80002n;

/** Ước lượng thô tổng gas triển khai (đo local): ~1,82 triệu. Cộng biên an toàn. */
const MIN_BALANCE_POL = 0.1;

async function main() {
  const networkName = (hre as any).globalOptions?.network;

  // Không có chốt này thì `npm run deploy` thiếu `--network` sẽ deploy lên chuỗi mô phỏng
  // trong bộ nhớ, in ra ba địa chỉ trông rất thật, rồi biến mất khi tiến trình kết thúc.
  if (networkName === undefined || networkName === "default") {
    throw new Error(
      "Chưa chọn mạng. Chạy `npm run deploy:amoy`, đừng chạy thẳng script này —\n" +
        "không có --network thì nó deploy lên chuỗi mô phỏng rồi vứt đi.",
    );
  }

  const { ethers } = await network.create();
  const provider = ethers.provider;

  const chainId = (await provider.getNetwork()).chainId;
  if (chainId !== AMOY_CHAIN_ID) {
    throw new Error(`Sai chuỗi: chainId = ${chainId}, cần ${AMOY_CHAIN_ID} (Polygon Amoy).`);
  }

  const [deployer] = await ethers.getSigners();
  if (deployer === undefined) {
    throw new Error("Không có signer — `DEPLOYER_PRIVATE_KEY` rỗng trong .env?");
  }

  const balance = await provider.getBalance(deployer.address);
  const balancePol = Number(ethers.formatEther(balance));

  console.log(`Mạng      : ${networkName} (chainId ${chainId})`);
  console.log(`Ví        : ${deployer.address}`);
  console.log(`Số dư     : ${balancePol.toFixed(4)} POL`);

  if (balancePol < MIN_BALANCE_POL) {
    throw new Error(
      `Số dư dưới ${MIN_BALANCE_POL} POL — lấy thêm từ faucet Amoy trước khi deploy.\n` +
        "PROJECT.md §2.6: lấy POL đều hằng tuần, đừng đợi đến lúc cần.",
    );
  }
  console.log();

  const admin = deployer.address;
  const results: Record<string, { address: string; txHash: string; gasUsed: string }> = {};

  for (const name of ["AnchorRegistry", "IssuerRegistry", "StatusList"]) {
    process.stdout.write(`Deploy ${name} ... `);
    const contract = await ethers.deployContract(name, [admin]);
    await contract.waitForDeployment();

    const tx = contract.deploymentTransaction()!;
    const receipt = await tx.wait();
    const address = await contract.getAddress();

    results[name] = {
      address,
      txHash: tx.hash,
      gasUsed: receipt!.gasUsed.toString(),
    };
    console.log(`${address}  (gas ${receipt!.gasUsed.toLocaleString("en-US")})`);
  }

  // ------------------------------------------------------------ lưu lại
  const record = {
    network: networkName,
    chainId: Number(chainId),
    deployer: deployer.address,
    deployedAt: new Date().toISOString().replace(/\.\d{3}Z$/, "Z"),
    contracts: results,
  };

  const outPath = `${HERE}/../deployments/${networkName}.json`;
  mkdirSync(dirname(outPath), { recursive: true });
  writeFileSync(outPath, JSON.stringify(record, null, 2) + "\n", "utf8");
  console.log(`\nĐã ghi contracts/deployments/${networkName}.json`);

  // ------------------------------------------------------ việc tiếp theo
  console.log("\n--- Dán vào .env gốc repo ---");
  console.log(`ANCHOR_REGISTRY_ADDRESS=${results.AnchorRegistry!.address}`);
  console.log(`ISSUER_REGISTRY_ADDRESS=${results.IssuerRegistry!.address}`);
  console.log(`STATUS_LIST_ADDRESS=${results.StatusList!.address}`);

  console.log("\n--- Verify trên PolygonScan (Etherscan API V2, cần ETHERSCAN_API_KEY) ---");
  for (const [name, r] of Object.entries(results)) {
    console.log(`npx hardhat verify --network ${networkName} ${r.address} ${admin}`);
    void name;
  }

  console.log("\n--- Xem trên explorer ---");
  for (const [name, r] of Object.entries(results)) {
    console.log(`${name.padEnd(15)} https://amoy.polygonscan.com/tx/${r.txHash}`);
  }

  console.log(
    "\nGhi gas triển khai vào docs/measurements.md (gọi /measurements). Đây là số đo THẬT" +
      "\ntrên Amoy, khác với số local trong bảng của scripts/measure-gas.ts — giữ cả hai.",
  );
}

main().catch((e) => {
  console.error(`\n${e instanceof Error ? e.message : e}`);
  process.exitCode = 1;
});
