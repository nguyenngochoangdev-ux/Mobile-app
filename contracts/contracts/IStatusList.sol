// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

/**
 * @title IStatusList
 * @notice API chung của hai cách hiện thực danh sách thu hồi. Tồn tại để phép đo #2
 *         (PROJECT.md §8 — gas bitmap vs mapping-per-credential) so sánh được công bằng:
 *         cùng chữ ký hàm, cùng kiểm tra quyền, cùng sự kiện. Khác nhau đúng một thứ là
 *         cách lưu trữ.
 */
interface IStatusList {
    event StatusChanged(uint256 indexed index, bool revoked);

    function setRevoked(uint256 index, bool revoked) external;

    function setRevokedBatch(uint256[] calldata indices, bool revoked) external;

    function isRevoked(uint256 index) external view returns (bool);
}
