import { hexlify, toUtf8Bytes } from "ethers";

/**
 * Năm miền neo, và MỘT bản duy nhất của công thức mã hóa bytes8.
 *
 * Giá trị hex ở đây phải khớp từng byte với `docs/canonicalization.md` §2,
 * `AnchorDomain.toBytes8()` (Java) và `DOMAINS` trong `verifier/src/leaf.mjs`. Cùng một
 * chuỗi byte vừa là 8 byte đầu của tiền ảnh leaf hash, vừa là tham số `domain` truyền vào
 * `AnchorRegistry` — nên nếu ba tầng lệch nhau ở đây thì mọi proof fail, và fail im lặng.
 * `AnchorRegistry.test.ts` có một test chốt cứng các hằng số này.
 */
export const DOMAINS = ["ATTEND", "CRED", "SCORE", "AUDIT", "RULESET"] as const;

export type Domain = (typeof DOMAINS)[number];

/** ASCII căn trái, đệm 0x00 bên phải cho đủ 8 byte — đúng kiểu `bytes8` của Solidity. */
export function domainBytes8(name: string): string {
  const ascii = toUtf8Bytes(name);
  if (ascii.length > 8) {
    throw new Error(`Tên miền dài quá 8 byte: ${name}`);
  }
  const out = new Uint8Array(8); // JS khởi tạo sẵn 0x00
  out.set(ascii);
  return hexlify(out);
}

/** Root giả lập, tất định theo hạt giống — để test đọc được, không cần Merkle thật. */
export function fakeRoot(seed: number): string {
  return "0x" + seed.toString(16).padStart(64, "0");
}
