// SPDX-License-Identifier: MIT
pragma solidity 0.8.28;

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title IssuerRegistry
 * @notice Danh bạ các bên được phép cấp phát: Đoàn trường, các khoa, CLB, doanh nghiệp đối
 *         tác. Chống lưng luận điểm 3 (PROJECT.md §10) — nhiều bên cấp phát không hoàn toàn
 *         tin nhau, không bên nào nên độc quyền sổ cái.
 *
 * @dev Phát biểu cho đúng mức, đừng thổi phồng khi bảo vệ: ở phiên bản này quyền ghi vẫn
 *      nằm ở một REGISTRAR_ROLE do trường giữ, nên nó CHƯA phải quản trị đa bên thật. Cái
 *      contract này thật sự mang lại là: danh sách bên cấp phát nằm ở nơi công khai, ai
 *      cũng đọc được, và mọi thay đổi để lại vết vĩnh viễn — chứ không nằm trong một bảng
 *      MySQL mà quản trị viên sửa xong không ai biết. Chuyển sang multisig/DAO là hướng
 *      phát triển, không phải phạm vi 8 tuần.
 *
 *      Có `listIssuers()` vì lý do y hệt AnchorRegistry: verifier không dùng được
 *      `eth_getLogs` trên RPC công cộng (PROJECT.md §2.2), nên muốn liệt kê được thì danh
 *      sách phải nằm trong storage. Đăng ký là việc hiếm (vài chục lần trong đời hệ thống)
 *      nên chi phí thêm một phần tử mảng không đáng kể.
 */
contract IssuerRegistry is AccessControl {
    bytes32 public constant REGISTRAR_ROLE = keccak256("REGISTRAR_ROLE");

    struct Issuer {
        bool registered;
        bool active;
        uint64 since; // block.timestamp lúc đăng ký lần đầu
        string name; // tên hiển thị, ví dụ "Doan Thanh nien" / "Khoa CNTT"
    }

    mapping(address issuer => Issuer) private _issuers;
    address[] private _addresses;

    event IssuerRegistered(address indexed issuer, string name);
    event IssuerActiveChanged(address indexed issuer, bool active);

    error AlreadyRegistered(address issuer);
    error NotRegistered(address issuer);
    error EmptyName();
    error ZeroAddress();

    constructor(address admin) {
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(REGISTRAR_ROLE, admin);
    }

    /// @notice Đăng ký một bên cấp phát mới. Đăng ký xong là ở trạng thái hoạt động.
    function registerIssuer(
        address issuer,
        string calldata name
    ) external onlyRole(REGISTRAR_ROLE) {
        if (issuer == address(0)) revert ZeroAddress();
        if (bytes(name).length == 0) revert EmptyName();
        if (_issuers[issuer].registered) revert AlreadyRegistered(issuer);

        _issuers[issuer] = Issuer({
            registered: true,
            active: true,
            since: uint64(block.timestamp),
            name: name
        });
        _addresses.push(issuer);

        emit IssuerRegistered(issuer, name);
    }

    /**
     * @notice Bật/tắt quyền cấp phát. Không có hàm XÓA — thu hồi quyền của một khoa không
     *         được phép làm biến mất dấu vết là khoa đó từng có quyền, nếu không thì mọi
     *         credential đã cấp trở nên không truy nguyên được.
     */
    function setIssuerActive(
        address issuer,
        bool active
    ) external onlyRole(REGISTRAR_ROLE) {
        if (!_issuers[issuer].registered) revert NotRegistered(issuer);
        _issuers[issuer].active = active;
        emit IssuerActiveChanged(issuer, active);
    }

    /// @notice Câu hỏi verifier thật sự cần trả lời: địa chỉ ký credential này có đang được
    ///         phép cấp phát không. Một `eth_call`.
    function isActiveIssuer(address issuer) external view returns (bool) {
        return _issuers[issuer].active;
    }

    function getIssuer(address issuer) external view returns (Issuer memory) {
        return _issuers[issuer];
    }

    /// @notice Toàn bộ địa chỉ từng được đăng ký, kể cả bên đã bị tắt.
    function listIssuers() external view returns (address[] memory) {
        return _addresses;
    }

    function issuerCount() external view returns (uint256) {
        return _addresses.length;
    }
}
