/**
 * Đọc Merkle root đã neo, thẳng từ chuỗi.
 *
 * Đây là TOÀN BỘ phần verifier cần từ blockchain: **một** `eth_call`, không quét sự kiện,
 * không gọi backend một dòng nào (ràng buộc cứng PROJECT.md §4).
 *
 * Vì sao chỉ một lần gọi: RPC công cộng giới hạn `eth_getLogs` ở 10.000 block mỗi lần và
 * bắt buộc có bộ lọc address (PROJECT.md §2.2) — dò sự kiện nghĩa là phân trang hàng trăm
 * lần rồi chết. `AnchorRegistry` vì thế lưu root trong storage tra được bằng
 * `(domain, batchId)`, và bundle của sinh viên mang sẵn `batchId`.
 *
 * Hệ quả cho luận điểm 2: verifier chạy được trên endpoint công cộng KHÔNG CẦN API KEY, nên
 * nó vẫn xác minh được kể cả khi trường đã ngừng trả tiền cho mọi dịch vụ.
 */
import { Contract, JsonRpcProvider } from 'ethers';

import { domainBytes8 } from './leaf.mjs';

/** Chỉ ba hàm đọc. Verifier không bao giờ ghi. */
export const ANCHOR_REGISTRY_ABI = Object.freeze([
  'function getRoot(bytes8 domain, uint64 batchId) view returns (bytes32)',
  'function batchCount(bytes8 domain) view returns (uint64)',
]);

export const ZERO_ROOT = '0x' + '00'.repeat(32);

/**
 * @param rpcUrl   endpoint JSON-RPC. Dùng được endpoint công cộng không key.
 * @param address  địa chỉ `AnchorRegistry`.
 */
export function anchorRegistry(rpcUrl, address) {
  const provider = new JsonRpcProvider(rpcUrl);
  const contract = new Contract(address, ANCHOR_REGISTRY_ABI, provider);

  return {
    provider,

    /**
     * Root của một lô, hoặc `null` nếu chưa neo.
     *
     * Trả `null` chứ không phải chuỗi 32 byte 0x00: contract chặn ghi root rỗng nên giá trị
     * 0 chỉ có một nghĩa duy nhất là "chưa neo", và biến nó thành `null` khiến bên gọi
     * không thể vô tình so sánh thành công với một root rỗng.
     */
    async getRoot(domain, batchId) {
      const root = await contract.getRoot(domainBytes8(domain), BigInt(batchId));
      return root === ZERO_ROOT ? null : root;
    },

    async batchCount(domain) {
      return Number(await contract.batchCount(domainBytes8(domain)));
    },

    async chainId() {
      return Number((await provider.getNetwork()).chainId);
    },
  };
}
