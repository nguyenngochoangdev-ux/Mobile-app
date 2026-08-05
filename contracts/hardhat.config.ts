import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

import hardhatToolboxMochaEthers from "@nomicfoundation/hardhat-toolbox-mocha-ethers";
import { configVariable, defineConfig } from "hardhat/config";

// Bí mật nằm ở `.env` GỐC REPO, không phải trong contracts/ — một file duy nhất dùng chung
// với backend, để địa chỉ contract sau khi deploy không phải chép sang hai chỗ.
// `process.loadEnvFile` là API có sẵn của Node 21.7+, nên không cần thêm `dotenv`.
const rootEnv = fileURLToPath(new URL("../.env", import.meta.url));
if (existsSync(rootEnv)) {
  process.loadEnvFile(rootEnv);
}

export default defineConfig({
  plugins: [hardhatToolboxMochaEthers],

  solidity: {
    // Ghim CỨNG cả version lẫn evmVersion. Lý do không phải khẩu vị:
    //  - solc mới nhất (0.8.36 lúc viết) mặc định sinh mã cho hard fork mới hơn thứ Amoy
    //    chạy. Opcode lạ không báo lỗi lúc biên dịch — nó chết lúc deploy, với thông báo
    //    vô dụng. `cancun` đã sống trên Polygon PoS từ PIP-31 nên chắc chắn chạy, và cũng
    //    đúng bằng mặc định của 0.8.28 (ghim ở đây là để tài liệu hóa, không đổi hành vi).
    //  - Verify trên PolygonScan bắt buộc khớp CHÍNH XÁC version + settings. Ghim thì
    //    verify lại sau vài tuần vẫn ra cùng bytecode.
    version: "0.8.28",
    settings: {
      optimizer: { enabled: true, runs: 200 },
      evmVersion: "cancun",
    },
  },

  networks: {
    amoy: {
      type: "http",
      chainType: "generic",
      url: configVariable("AMOY_RPC_URL"),
      accounts: [configVariable("DEPLOYER_PRIVATE_KEY")],
      chainId: 80002,
    },
  },

  // Etherscan API V2: MỘT key cho 60+ chain, phân biệt bằng chainid. V1 đã tắt 15/08/2025,
  // nên "key riêng của PolygonScan" trong mọi hướng dẫn cũ là thông tin chết.
  // Amoy (80002) đã có sẵn trong chain descriptors của Hardhat 3 → không cần customChains.
  verify: {
    etherscan: {
      apiKey: configVariable("ETHERSCAN_API_KEY"),
    },
  },
});
