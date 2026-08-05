// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title AnchorRegistry
 * @notice Sổ cái Merkle root theo (miền, lô). Hiện thực hóa luận điểm 1 và 2 của đề tài
 *         (PROJECT.md §10): chứng minh dữ liệu không bị sửa hồi tố, và cho phép bên thứ ba
 *         xác minh sau khi máy chủ trường đã tắt.
 *
 * @dev Ba quyết định thiết kế cần đọc trước khi sửa file này.
 *
 * 1. TRA CỨU TRỰC TIẾP, KHÔNG QUÉT SỰ KIỆN.
 *    Verifier là trang tĩnh chạy trên endpoint RPC công cộng không key. Endpoint loại này
 *    giới hạn `eth_getLogs` ở 10.000 block mỗi lần gọi (≈5,6 giờ lịch sử trên Amoy) và bắt
 *    buộc có bộ lọc address — dò sự kiện nghĩa là phân trang hàng trăm lần và chết
 *    (PROJECT.md §2.2). Vì vậy root phải nằm trong STORAGE đọc được bằng MỘT `eth_call`, và
 *    bundle của sinh viên mang sẵn `batchId`. Sự kiện ở đây chỉ để lập chỉ mục off-chain,
 *    verifier không bao giờ cần tới.
 *
 * 2. GHI MỘT LẦN, KỂ CẢ ADMIN CŨNG KHÔNG GHI ĐÈ ĐƯỢC.
 *    Nếu người có quyền quản trị sửa lại được root đã neo thì luận điểm "chống sửa hồi tố"
 *    sụp đổ hoàn toàn — hệ thống chỉ còn là một CSDL đắt tiền. Không có hàm nào sửa hay xóa
 *    root, và contract KHÔNG upgradeable (danh sách cấm, CLAUDE.md). Sai lô thì neo lô mới
 *    và ghi nhận đính chính off-chain; lịch sử sai vẫn nằm đó, đúng như mong muốn.
 *
 * 3. ĐÚNG MỘT Ô LƯU TRỮ MỖI LÔ.
 *    Chỉ `root` được lưu (1 slot). `leafCount` và thời điểm neo nằm trong sự kiện và trong
 *    block của giao dịch — bundle mang `txHash` nên verifier lấy được bằng
 *    `eth_getTransactionByHash`. Thêm một slot nữa là +~20.000 gas CỐ ĐỊNH cho mọi lô, làm
 *    hỏng chính con số đẹp nhất của đề tài: chi phí neo không phụ thuộc số bản ghi, nên
 *    gas/bản ghi giảm theo 1/N (phép đo #1, PROJECT.md §8).
 */
contract AnchorRegistry is AccessControl {
    /// @notice Vai trò của job neo hằng đêm ở backend. Tách khỏi admin để khóa triển khai
    ///         có thể cất offline sau khi cấp quyền.
    bytes32 public constant ANCHOR_ROLE = keccak256("ANCHOR_ROLE");

    /// @dev domain là ASCII căn trái đệm 0x00 — ĐÚNG bằng 8 byte đầu của tiền ảnh leaf hash
    ///      (docs/canonicalization.md §1), nên không có chỗ nào để lệch giữa Java, JS và
    ///      Solidity.
    mapping(bytes8 domain => mapping(uint64 batchId => bytes32 root)) private _roots;

    /// @dev Số lô đã neo của mỗi miền. Thuần thống kê cho báo cáo; verifier không cần.
    mapping(bytes8 domain => uint64 count) private _batchCount;

    event Anchored(
        bytes8 indexed domain,
        uint64 indexed batchId,
        bytes32 root,
        uint32 leafCount
    );

    /// @notice Lô này đã neo rồi. Ghi đè bị cấm — xem quyết định 2 ở đầu file.
    error RootAlreadyAnchored(bytes8 domain, uint64 batchId, bytes32 existingRoot);
    /// @notice root = 0 bị cấm, vì `getRoot` dùng 0 làm dấu hiệu "chưa neo".
    error EmptyRoot();
    /// @notice Neo một lô rỗng là lỗi logic ở backend, không phải trường hợp hợp lệ.
    error EmptyBatch();

    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(ANCHOR_ROLE, admin);
    }

    /**
     * @notice Neo Merkle root của một lô.
     * @param domain    Miền neo dạng bytes8: ATTEND · CRED · SCORE · AUDIT · RULESET.
     * @param batchId   Số lô do backend cấp, duy nhất trong phạm vi một miền.
     * @param root      Merkle root của lô.
     * @param leafCount Số bản ghi trong lô — chỉ vào sự kiện, không lưu (quyết định 3).
     */
    function anchor(
        bytes8 domain,
        uint64 batchId,
        bytes32 root,
        uint32 leafCount
    ) external onlyRole(ANCHOR_ROLE) {
        if (root == bytes32(0)) revert EmptyRoot();
        if (leafCount == 0) revert EmptyBatch();

        bytes32 existing = _roots[domain][batchId];
        if (existing != bytes32(0)) {
            revert RootAlreadyAnchored(domain, batchId, existing);
        }

        _roots[domain][batchId] = root;
        unchecked {
            ++_batchCount[domain];
        }

        emit Anchored(domain, batchId, root, leafCount);
    }

    /**
     * @notice Lấy root đã neo. Đây là hàm DUY NHẤT verifier gọi — một `eth_call`, không
     *         quét lịch sử, chạy được trên endpoint công cộng không key.
     * @return Root của lô, hoặc `bytes32(0)` nếu chưa neo. Không nhập nhằng: root = 0 bị
     *         chặn ở `anchor`.
     */
    function getRoot(bytes8 domain, uint64 batchId) external view returns (bytes32) {
        return _roots[domain][batchId];
    }

    /// @notice Số lô đã neo của một miền. Cho báo cáo, không cho verifier.
    function batchCount(bytes8 domain) external view returns (uint64) {
        return _batchCount[domain];
    }
}
