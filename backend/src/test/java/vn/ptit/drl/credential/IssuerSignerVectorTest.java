package vn.ptit.drl.credential;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Test vector chữ ký credential — NỬA JAVA.
 * Nửa JS: {@code verifier/test/cred.test.mjs}.
 *
 * <p>Chạy: {@code .\scripts\test-backend.ps1 IssuerSignerVectorTest}
 *
 * <p>Hai phía đọc CÙNG MỘT file {@code cred-signature-vectors.json}, sinh bởi
 * {@code verifier/scripts/gen-cred-sig-vectors.mjs} bằng {@code ethers}. Xanh một phía không
 * có nghĩa gì.
 *
 * <p><b>Vì sao bộ vector này tồn tại.</b> "ECDSA thì ở đâu cũng thế" là sai, và sai im lặng.
 * Bốn chỗ hai thư viện lệch nhau được — băm lại hay không, tiền tố EIP-191 hay không,
 * {@code v} là 27/28 hay 0/1, {@code s} có chuẩn hóa về nửa dưới hay không — và cả bốn đều
 * cho ra một địa chỉ phục hồi <b>hợp lệ nhưng sai</b>, chứ không ném lỗi. Verifier khi đó hỏi
 * {@code IssuerRegistry} về địa chỉ rác và nhận về "không có quyền", trông y hệt credential
 * giả.
 */
class IssuerSignerVectorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final HexFormat HEX = HexFormat.of();
  private static final JsonNode DOC = load();

  private static JsonNode load() {
    try (InputStream in =
        IssuerSignerVectorTest.class.getResourceAsStream("/cred-signature-vectors.json")) {
      if (in == null) {
        throw new IllegalStateException(
            "Không thấy cred-signature-vectors.json. Sinh lại bằng:"
                + " cd verifier && npm run gen-cred-sig-vectors");
      }
      return MAPPER.readTree(in);
    } catch (Exception e) {
      throw new IllegalStateException("Không đọc được bộ test vector chữ ký", e);
    }
  }

  private static List<JsonNode> vectors() {
    List<JsonNode> out = new ArrayList<>();
    DOC.get("vectors").forEach(out::add);
    return out;
  }

  private static byte[] hex(String s) {
    return HEX.parseHex(s.startsWith("0x") ? s.substring(2) : s);
  }

  private static IssuerSigner testSigner() {
    return new IssuerSigner(DOC.get("testPrivateKey").textValue());
  }

  // ------------------------------------------------------------------ phục hồi

  @TestFactory
  @DisplayName("Chữ ký do ethers tạo phải phục hồi ra đúng địa chỉ ở phía Java")
  List<DynamicTest> phucHoiDiaChi() {
    List<DynamicTest> tests = new ArrayList<>();
    for (JsonNode v : vectors()) {
      tests.add(dynamicTest(v.get("id").textValue(), () -> {
        JsonNode e = v.get("expected");
        String recovered =
            IssuerSigner.recoverAddress(hex(e.get("leaf").textValue()),
                hex(e.get("signature").textValue()));

        assertEquals(e.get("signerAddress").textValue(), recovered,
            "Địa chỉ phục hồi ở Java khác địa chỉ ethers đã ký. " + v.get("why").textValue());
      }));
    }
    return tests;
  }

  @TestFactory
  @DisplayName("Java ký lại cùng leaf phải ra ĐÚNG TỪNG BYTE chữ ký của ethers")
  List<DynamicTest> kyRaByteGiongHet() {
    IssuerSigner signer = testSigner();
    List<DynamicTest> tests = new ArrayList<>();

    for (JsonNode v : vectors()) {
      tests.add(dynamicTest(v.get("id").textValue(), () -> {
        JsonNode e = v.get("expected");
        byte[] mine = signer.sign(hex(e.get("leaf").textValue()));

        // ECDSA nói chung KHÔNG tất định — nó bốc một số ngẫu nhiên k mỗi lần ký. Hai phía
        // ra byte giống hệt nhau được là vì cả web3j lẫn ethers đều dùng k tất định theo
        // RFC 6979 và cùng chuẩn hóa s về nửa dưới của đường cong.
        //
        // Khẳng định này MẠNH HƠN mức cần thiết cho tính đúng đắn: chỉ cần địa chỉ phục hồi
        // khớp là đủ để hệ chạy. Giữ nó vì nó bắt được cả những lệch mà phép kiểm địa chỉ
        // bỏ qua — ví dụ một phía quên chuẩn hóa s, thứ vẫn phục hồi ra đúng địa chỉ nhưng
        // làm chữ ký lưu trong CSDL khác chữ ký sinh lại được.
        assertArrayEquals(hex(e.get("signature").textValue()), mine,
            "Chữ ký Java khác chữ ký ethers trên cùng một leaf.\n"
                + "  ethers: " + e.get("signature").textValue() + "\n"
                + "  java:   0x" + HEX.formatHex(mine));
      }));
    }
    return tests;
  }

  @Test
  @DisplayName("Địa chỉ của khóa test khớp giá trị ethers tính ra")
  void diaChiKhoaTest() {
    assertEquals(
        DOC.get("vectors").get(0).get("expected").get("signerAddress").textValue(),
        testSigner().address());
  }

  @Test
  @DisplayName("Địa chỉ trả về là CHỮ THƯỜNG — dạng checksum EIP-55 làm lệch leaf hash")
  void diaChiChuThuong() {
    String a = testSigner().address();
    assertEquals(a.toLowerCase(java.util.Locale.ROOT), a);
    assertTrue(a.matches("^0x[0-9a-f]{40}$"), a);
  }

  @Test
  @DisplayName("Hai leaf khác nhau cho hai chữ ký khác nhau — chữ ký gắn với NỘI DUNG")
  void chuKyGanVoiNoiDung() {
    List<JsonNode> vs = vectors();
    assertNotEquals(
        vs.get(0).get("expected").get("signature").textValue(),
        vs.get(1).get("expected").get("signature").textValue());
  }

  // ------------------------------------------------------------------ chống giả mạo

  @Nested
  @DisplayName("Sửa vào là hỏng — nếu nhóm này xanh khi không nên, phép kiểm là giả")
  class ChongGiaMao {

    @Test
    @DisplayName("Đổi một byte của leaf → phục hồi ra địa chỉ KHÁC")
    void doiLeaf() {
      JsonNode e = vectors().get(0).get("expected");
      byte[] leaf = hex(e.get("leaf").textValue());
      byte[] sig = hex(e.get("signature").textValue());

      leaf[0] ^= 0x01;

      // Điểm cốt lõi: phục hồi KHÔNG ném lỗi, nó trả về một địa chỉ hợp lệ nhưng sai. Đây
      // chính là lý do bên gọi BẮT BUỘC phải so địa chỉ phục hồi với issuerAddress trong
      // payload — tự thân lời gọi recoverAddress không phải là một phép kiểm.
      assertNotEquals(e.get("signerAddress").textValue(),
          IssuerSigner.recoverAddress(leaf, sig));
    }

    @Test
    @DisplayName("Đổi một byte của chữ ký → phục hồi ra địa chỉ KHÁC")
    void doiChuKy() {
      JsonNode e = vectors().get(0).get("expected");
      byte[] leaf = hex(e.get("leaf").textValue());
      byte[] sig = hex(e.get("signature").textValue());

      sig[10] ^= 0x01;

      String recovered;
      try {
        recovered = IssuerSigner.recoverAddress(leaf, sig);
      } catch (IllegalArgumentException ex) {
        return; // Cũng chấp nhận: một số r/s hỏng không phục hồi được điểm trên đường cong.
      }
      assertNotEquals(e.get("signerAddress").textValue(), recovered);
    }

    @Test
    @DisplayName("Dùng chữ ký của credential khác → phục hồi ra địa chỉ KHÁC")
    void chuKyCuaBanGhiKhac() {
      List<JsonNode> vs = vectors();
      byte[] leaf = hex(vs.get(0).get("expected").get("leaf").textValue());
      byte[] sigKhac = hex(vs.get(1).get("expected").get("signature").textValue());

      assertNotEquals(vs.get(0).get("expected").get("signerAddress").textValue(),
          IssuerSigner.recoverAddress(leaf, sigKhac));
    }

    @Test
    @DisplayName("Ký bằng khóa khác → địa chỉ khác, dù leaf giống hệt")
    void khoaKhac() {
      JsonNode e = vectors().get(0).get("expected");
      byte[] leaf = hex(e.get("leaf").textValue());

      IssuerSigner keSao = new IssuerSigner(
          "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");
      byte[] sigGia = keSao.sign(leaf);

      assertNotEquals(e.get("signerAddress").textValue(),
          IssuerSigner.recoverAddress(leaf, sigGia));
      assertEquals(keSao.address(), IssuerSigner.recoverAddress(leaf, sigGia));
    }
  }

  // ------------------------------------------------------------------ định dạng

  @Nested
  @DisplayName("Định dạng phải vỡ ồn ào")
  class DinhDang {

    private final IssuerSigner signer = testSigner();

    @Test
    @DisplayName("Chữ ký đúng 65 byte")
    void doDaiChuKy() {
      byte[] sig = signer.sign(new byte[32]);
      assertEquals(65, sig.length);
      assertTrue(sig[64] == 27 || sig[64] == 28, "v phải là 27 hoặc 28, nhận: " + sig[64]);
    }

    @Test
    @DisplayName("Leaf sai độ dài bị từ chối, không lặng lẽ đệm thêm")
    void leafSaiDoDai() {
      assertThrows(IllegalArgumentException.class, () -> signer.sign(new byte[31]));
      assertThrows(IllegalArgumentException.class, () -> signer.sign(new byte[33]));
      assertThrows(IllegalArgumentException.class, () -> signer.sign(null));
    }

    @Test
    @DisplayName("Chữ ký sai độ dài bị từ chối")
    void chuKySaiDoDai() {
      byte[] leaf = new byte[32];
      assertThrows(IllegalArgumentException.class,
          () -> IssuerSigner.recoverAddress(leaf, new byte[64]));
      assertThrows(IllegalArgumentException.class,
          () -> IssuerSigner.recoverAddress(leaf, null));
    }

    @Test
    @DisplayName("v ngoài {27,28} bị từ chối — chữ ký EIP-155 không dùng ở đây")
    void vSai() {
      byte[] leaf = new byte[32];
      byte[] sig = signer.sign(leaf);
      sig[64] = 0;
      assertThrows(IllegalArgumentException.class,
          () -> IssuerSigner.recoverAddress(leaf, sig));
    }

    @Test
    @DisplayName("Khóa rỗng bị từ chối lúc dựng, không đợi tới lúc ký")
    void khoaRong() {
      assertThrows(IllegalArgumentException.class, () -> new IssuerSigner(null));
      assertThrows(IllegalArgumentException.class, () -> new IssuerSigner("  "));
    }
  }

  @Test
  @DisplayName("Cảnh báo được khi khóa issuer trùng khóa neo, kể cả khi viết khác dạng")
  void nhanRaKhoaTrung() {
    String k = DOC.get("testPrivateKey").textValue();
    assertTrue(IssuerSigner.isSameKey(k, k));
    assertTrue(IssuerSigner.isSameKey(k, k.substring(2)), "thiếu tiền tố 0x vẫn là cùng khóa");
    assertTrue(IssuerSigner.isSameKey(k, " " + k + " "), "khoảng trắng thừa vẫn là cùng khóa");
    assertTrue(IssuerSigner.isSameKey(k.toUpperCase(java.util.Locale.ROOT).replace("0X", "0x"), k),
        "chữ hoa vẫn là cùng khóa");

    assertTrue(!IssuerSigner.isSameKey(k,
        "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d"));
    assertTrue(!IssuerSigner.isSameKey(null, k));
  }

  @Test
  @DisplayName("Khóa neo RỖNG không được làm sập ứng dụng — đây là cấu hình mặc định")
  void khoaNeoRongKhongLamSap() {
    String k = DOC.get("testPrivateKey").textValue();

    // ANCHOR_PRIVATE_KEY trong .env là chuỗi rỗng chừng nào chuỗi còn tắt (ANCHOR_ENABLED
    // mặc định false). Trước khi có nhánh kiểm rỗng, Numeric.toBigInt("") ném "Zero length
    // BigInteger" ngay lúc dựng bean issuerSigner, và ứng dụng KHÔNG KHỞI ĐỘNG ĐƯỢC — với
    // một thông báo lỗi không liên quan gì tới nguyên nhân thật.
    assertTrue(!IssuerSigner.isSameKey(k, ""));
    assertTrue(!IssuerSigner.isSameKey(k, "   "));
    assertTrue(!IssuerSigner.isSameKey("", k));

    // Khóa không phải hex cũng không được ném — đây là phép kiểm CẢNH BÁO, không phải phép
    // kiểm tính đúng đắn.
    assertTrue(!IssuerSigner.isSameKey(k, "khong-phai-hex"));
  }
}
