/**
 * Danh sách contract TIN CẬY của verifier — nguồn sự thật, không phải bundle.
 *
 * ## Vì sao các địa chỉ này nằm trong mã nguồn chứ không đọc từ tệp bundle
 *
 * Người cầm bundle là người có động cơ sửa nó. Nếu verifier đọc `getRoot` từ địa chỉ contract
 * ghi trong bundle thì kẻ tấn công chỉ cần:
 *
 *   1. tự dựng cây Merkle chứa credential giả của mình,
 *   2. deploy một contract trả về đúng root đó,
 *   3. ghi địa chỉ contract đó vào bundle.
 *
 * Mọi phép kiểm mật mã khác vẫn xanh — leaf khớp, chữ ký khớp, proof dẫn về root — và
 * credential giả được chấp nhận. **Đây là cách phá hệ thống rẻ nhất nếu làm sai**, và nó
 * không đòi phá vỡ bất kỳ thuật toán nào.
 *
 * Neo địa chỉ vào mã nguồn của verifier biến câu hỏi "tin ai" thành thứ **người kiểm tra
 * chọn**, không phải thứ **người bị kiểm tra khai**. Đó cũng là mô hình tin cậy đúng: nhà
 * tuyển dụng tin PolygonScan / bản deploy công khai của trường, không tin tệp ứng viên đưa.
 *
 * ## Kiểm chứng độc lập các địa chỉ này
 *
 * Cả ba đã verify mã nguồn trên **PolygonScan lẫn Sourcify**, nên bất kỳ ai cũng đọc được
 * mã đang chạy mà không cần hỏi trường:
 *
 *   https://amoy.polygonscan.com/address/0x4aC296Ad010233799bA3B91b8505269213503fAF#code
 *
 * Bản ghi triển khai đầy đủ: `contracts/deployments/amoy.json`.
 */

/** Polygon Amoy — mạng thử nghiệm chính thức của Polygon, chainId 80002. */
export const AMOY = Object.freeze({
  name: 'Polygon Amoy',
  chainId: 80002,
  anchorRegistry: '0x4ac296ad010233799ba3b91b8505269213503faf',
  issuerRegistry: '0xd323118fa310a730bc4202fadd8dfa7cea4c5637',
  statusList: '0xc8538a8741ce428c4a26f3a06678b6ca10972106',
});

/**
 * Endpoint mặc định — **công cộng, không cần API key**.
 *
 * Đây không phải chi tiết tiện lợi mà là một phần của luận điểm 2 (`PROJECT.md` §10): hồ sơ
 * vẫn xác minh được kể cả khi trường đã tắt máy chủ VÀ ngừng trả tiền cho mọi dịch vụ RPC.
 * Verifier là trang tĩnh chạy trong trình duyệt nên đằng nào cũng không giấu được API key —
 * biến ràng buộc đó thành một tính chất đáng viết vào báo cáo thay vì một hạn chế.
 *
 * Đổi được bằng biến môi trường `RPC_URL` hoặc cờ `--rpc`.
 */
export const DEFAULT_RPC_URL = 'https://polygon-amoy-bor-rpc.publicnode.com/';

/** Địa chỉ chữ thường — phép so sánh trong `bundle.mjs` không phụ thuộc cách viết EIP-55. */
export function trustedChainFor(chainId) {
  if (Number(chainId) === AMOY.chainId) {
    return AMOY;
  }
  throw new Error(
    `Verifier này chỉ biết chainId ${AMOY.chainId} (${AMOY.name}), bundle khai ${chainId}.`,
  );
}
