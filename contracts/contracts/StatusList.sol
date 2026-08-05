// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {IStatusList} from "./IStatusList.sol";

/**
 * @title StatusList
 * @notice Danh sách thu hồi credential dạng bitmap, theo tinh thần W3C Status List: 256
 *         trạng thái nhồi vào một ô lưu trữ 256 bit.
 *
 * @dev CHỖ NÀY CÓ MỘT MÂU THUẪN CÓ THẬT GIỮA HAI QUYẾT ĐỊNH THIẾT KẾ — đừng bỏ qua khi
 *      viết báo cáo, nó là phần phân tích đáng giá nhất của contract này.
 *
 *      Bitmap thắng mapping-per-credential khi nhiều chỉ số bị thu hồi nằm CÙNG một word:
 *      lần ghi đầu vào word trả giá slot 0 → khác 0 (đắt), các lần sau chỉ sửa slot đã khác
 *      0 (rẻ hơn nhiều). Với mapping, mỗi lần thu hồi đều là một slot mới → luôn trả giá
 *      đắt.
 *
 *      Nhưng PROJECT.md §2.3 bắt cấp `status_list_index` NGẪU NHIÊN từ pool còn trống, để
 *      sự kiện `StatusChanged(index)` không lộ thứ tự cấp phát và không tương quan được với
 *      danh sách sinh viên. Cấp ngẫu nhiên nghĩa là các chỉ số bị thu hồi RẢI ĐỀU khắp
 *      không gian — đúng trường hợp xấu nhất của bitmap.
 *
 *      Nói cách khác: quyền riêng tư mua bằng gas. Con số cụ thể của cái đánh đổi này do
 *      `scripts/measure-gas.ts` đo (cả trường hợp gom cụm lẫn rải đều) và đưa vào ch.11.4.
 *      Kết luận đúng KHÔNG phải "bitmap rẻ hơn", mà là "bitmap rẻ hơn bao nhiêu thì phụ
 *      thuộc cách cấp chỉ số, và ta đã cố ý chọn cách đắt hơn".
 *
 *      Đọc thì bitmap thắng vô điều kiện: `getWord` trả 256 trạng thái trong MỘT `eth_call`.
 *      Đây mới là thứ verifier tĩnh cần (PROJECT.md §2.2).
 */
contract StatusList is AccessControl, IStatusList {
    bytes32 public constant STATUS_ROLE = keccak256("STATUS_ROLE");

    /// @dev word = index >> 8, bit = index & 0xff.
    mapping(uint256 word => uint256 bits) private _bits;

    error EmptyBatch();

    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(STATUS_ROLE, admin);
    }

    function setRevoked(uint256 index, bool revoked) external onlyRole(STATUS_ROLE) {
        _set(index, revoked);
    }

    function setRevokedBatch(
        uint256[] calldata indices,
        bool revoked
    ) external onlyRole(STATUS_ROLE) {
        uint256 n = indices.length;
        if (n == 0) revert EmptyBatch();
        for (uint256 i = 0; i < n; ++i) {
            _set(indices[i], revoked);
        }
    }

    function isRevoked(uint256 index) external view returns (bool) {
        return _bits[index >> 8] & (1 << (index & 0xff)) != 0;
    }

    /**
     * @notice 256 trạng thái liên tiếp trong một lần đọc — bit thứ k ứng với chỉ số
     *         `wordIndex * 256 + k`. Đây là lý do chính chọn bitmap: verifier tĩnh tải một
     *         word là kiểm tra được cả nhóm mà không cần thêm vòng gọi mạng nào.
     */
    function getWord(uint256 wordIndex) external view returns (uint256) {
        return _bits[wordIndex];
    }

    /// @dev Không ghi nếu trạng thái không đổi: tiết kiệm gas và không sinh sự kiện giả.
    ///      Hệ quả cho phép đo: đo lại trên chỉ số đã thu hồi sẽ ra gas rất thấp — script
    ///      đo phải dùng các chỉ số phân biệt.
    function _set(uint256 index, bool revoked) private {
        uint256 w = index >> 8;
        uint256 mask = 1 << (index & 0xff);
        uint256 current = _bits[w];
        uint256 next = revoked ? current | mask : current & ~mask;
        if (next != current) {
            _bits[w] = next;
            emit StatusChanged(index, revoked);
        }
    }
}
