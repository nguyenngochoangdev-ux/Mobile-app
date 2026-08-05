// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

import {IStatusList} from "../IStatusList.sol";

/**
 * @title StatusListMapping
 * @notice ĐỐI CHỨNG CHO PHÉP ĐO — KHÔNG BAO GIỜ DEPLOY LÊN AMOY.
 *
 * @dev Cách hiện thực "ngây thơ": mỗi credential một ô lưu trữ riêng. Tồn tại duy nhất để
 *      phép đo #2 (PROJECT.md §8) có mẫu so sánh; không có gì trong hệ thống gọi tới nó.
 *
 *      Giữ NGUYÊN VẸN mọi thứ ngoài cách lưu trữ — cùng `IStatusList`, cùng `AccessControl`,
 *      cùng `onlyRole`, cùng sự kiện, cùng lối tắt "không đổi thì không ghi". Nếu bỏ
 *      AccessControl ở bản này cho gọn thì chênh lệch gas đo được sẽ lẫn cả chi phí kiểm
 *      tra quyền, và con số đưa vào báo cáo là con số sai. Đây là điểm phương pháp luận nên
 *      nói rõ ở ch.11.4.
 */
contract StatusListMapping is AccessControl, IStatusList {
    bytes32 public constant STATUS_ROLE = keccak256("STATUS_ROLE");

    mapping(uint256 index => bool revoked) private _revoked;

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
        return _revoked[index];
    }

    function _set(uint256 index, bool revoked) private {
        if (_revoked[index] != revoked) {
            _revoked[index] = revoked;
            emit StatusChanged(index, revoked);
        }
    }
}
