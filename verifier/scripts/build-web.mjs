/**
 * Dựng trang verifier tĩnh vào `verifier/dist/`.
 *
 *     npm run build:web
 *
 * ## Vì sao KHÔNG có bundler
 *
 * `PROJECT.md` §4 ràng buộc cứng: verifier chỉ được có `ethers` + `merkletreejs`, **không
 * thêm dependency nào, kể cả devDependency**. Vite/esbuild/rollup đều vi phạm.
 *
 * Ràng buộc đó nghe khắt khe nhưng phục vụ đúng một mục đích: verifier là thứ **nhà tuyển
 * dụng chạy mà không có ai bảo đảm cho họ**. Mỗi gói thêm vào là một thứ họ phải tin. Trang
 * này vì thế là HTML + ES module chạy thẳng trong trình duyệt, và "build" chỉ là **chép tệp**.
 *
 * Cách thay bundler: `<script type="importmap">` trong `index.html` trỏ `ethers` tới
 * `./vendor/ethers.js` — chính bản ESM mà gói `ethers` đã ship sẵn. Không biên dịch, không
 * biến đổi, không sinh mã. Thứ chạy trên trình duyệt là **đúng những tệp nằm trong repo**,
 * và ai cũng đối chiếu được từng dòng.
 *
 * ## Kết quả
 *
 * `dist/` là một thư mục tĩnh thuần: mở bằng bất kỳ máy chủ tĩnh nào, hoặc đẩy thẳng lên
 * GitHub Pages / Vercel. Không cần Node ở phía chạy.
 */
import { cpSync, mkdirSync, rmSync, existsSync, readdirSync, statSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const GOC = resolve(HERE, '..');
const DIST = join(GOC, 'dist');

/**
 * Bản ESM của ethers, ship sẵn trong gói.
 *
 * Dùng `ethers.js` chứ không phải `ethers.min.js`: bản không rút gọn đọc được, và verifier là
 * chỗ mà **đọc được quan trọng hơn nhẹ**. Chênh lệch kích thước không đáng kể so với việc một
 * người muốn kiểm tra có thể mở tệp ra xem.
 */
const ETHERS = join(GOC, 'node_modules', 'ethers', 'dist', 'ethers.js');

if (!existsSync(ETHERS)) {
  console.error(`Khong thay ${ETHERS}\nChay \`npm install\` trong thu muc verifier truoc.`);
  process.exit(1);
}

// Xóa NỘI DUNG chứ không xóa chính thư mục.
//
// Trên Windows (và nhất là khi repo nằm trong OneDrive), `rm -rf dist` báo `EPERM` bất cứ khi
// nào có tiến trình khác đang mở thư mục — một tab trình duyệt đang xem trang, hay chính máy
// chủ tĩnh dùng để chạy thử. Bắt người dùng đóng trình duyệt mỗi lần build là một quy trình
// không ai theo được.
if (existsSync(DIST)) {
  for (const ten of readdirSync(DIST)) {
    try {
      rmSync(join(DIST, ten), { recursive: true, force: true });
    } catch {
      // Tệp đang bị giữ sẽ được ghi đè ngay dưới đây.
    }
  }
}
mkdirSync(join(DIST, 'vendor'), { recursive: true });
mkdirSync(join(DIST, 'src'), { recursive: true });

const { readFileSync, writeFileSync } = await import('node:fs');

/**
 * ⚠️ Đổi đuôi `.mjs` → `.js` khi chép sang `dist/`, và viết lại đường dẫn import.
 *
 * **Không phải chuyện thẩm mỹ.** Trình duyệt TỪ CHỐI chạy một ES module nếu máy chủ trả về
 * `Content-Type` không phải JavaScript — và nhiều máy chủ tĩnh không biết đuôi `.mjs`. Trên
 * chính máy này, `python -m http.server` trả `text/plain` cho `.mjs`, và trang trắng xóa mà
 * **console không báo gì cả**: script không chạy, không có lỗi nào để in.
 *
 * Đuôi `.js` được ánh xạ ở mọi nơi. Với một trang mà cả điểm bán hàng là "chạy được ở bất kỳ
 * đâu, kể cả sau khi trường ngừng hoạt động", phụ thuộc vào việc máy chủ có biết `.mjs` hay
 * không là một rủi ro không đáng nhận.
 *
 * Mã nguồn giữ `.mjs` — Node và bộ test không đổi gì.
 */
// Neo vào `from '…'` / `import '…'` thay vì dò đường dẫn tự do. Bản đầu dùng
// `(\.\/[\w.-]+)\.mjs(['"])` và ĐỂ LỌT `'../src/bundle.mjs'` — vì `[\w.-]` không khớp dấu
// `/` ở giữa. Trang vẫn trắng xóa, và console vẫn không báo gì.
const doiDuoi = (s) => s.replace(/(\b(?:from|import)\s+['"][^'"]+)\.mjs(['"])/g, '$1.js$2');

for (const ten of readdirSync(join(GOC, 'src'))) {
  if (!ten.endsWith('.mjs')) continue;
  writeFileSync(
    join(DIST, 'src', ten.replace(/\.mjs$/, '.js')),
    doiDuoi(readFileSync(join(GOC, 'src', ten), 'utf8')),
    'utf8');
}

// Trang + mã giao diện.
cpSync(join(GOC, 'web'), DIST, { recursive: true });
writeFileSync(join(DIST, 'app.js'),
  doiDuoi(readFileSync(join(DIST, 'app.js'), 'utf8')), 'utf8');

// ethers ESM.
cpSync(ETHERS, join(DIST, 'vendor', 'ethers.js'));

// ------------------------------------------------------------------ kiểm tra

/**
 * `merkle.mjs` import `merkletreejs` (CJS, cần `Buffer`) nên **không chạy được trong trình
 * duyệt**. Nó vẫn được chép vào `dist/src` cho đủ bộ, nhưng trang web tuyệt đối không được
 * import nó — đường xác minh dùng `merkle-verify.mjs`.
 *
 * Kiểm ở đây thay vì tin: một lần `import` nhầm sẽ làm trang trắng xóa khi mở, và lỗi
 * `Buffer is not defined` trong console không nói được nguyên nhân thật.
 */
const nguon = [join(DIST, 'app.js'), join(DIST, 'src', 'bundle.js'),
  join(DIST, 'src', 'chain.js'), join(DIST, 'src', 'cred.js'),
  join(DIST, 'src', 'leaf.js'), join(DIST, 'src', 'jcs.js'),
  join(DIST, 'src', 'trusted-chain.js'), join(DIST, 'src', 'merkle-verify.js')];



/**
 * Bỏ chú thích trước khi dò.
 *
 * Bản đầu dò thẳng trên mã nguồn và báo lỗi ở `leaf.mjs` — vì **chú thích giải thích tại sao
 * đã bỏ `Buffer`** có chứa chữ `Buffer`. Một phép kiểm bắt nhầm chính lời giải thích của nó
 * là phép kiểm sẽ bị ai đó tắt đi.
 *
 * Cắt thô bằng regex là đủ: mấy tệp này không có chuỗi nào chứa `//` hay `/*`.
 */
function boChuThich(src) {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/.*$/gm, '$1');
}

for (const f of nguon) {
  const noiDung = boChuThich(readFileSync(f, 'utf8'));
  if (/from\s+['"]merkletreejs['"]/.test(noiDung)) {
    console.error(`LOI: ${f} import merkletreejs — khong chay duoc trong trinh duyet.`);
    process.exit(1);
  }
  if (/\bBuffer\b/.test(noiDung)) {
    console.error(`LOI: ${f} dung Buffer — global cua Node, khong co trong trinh duyet.`);
    process.exit(1);
  }
  if (/from\s+['"]\.\.?\/.*merkle\.mjs['"]/.test(noiDung)) {
    console.error(`LOI: ${f} import merkle.mjs — dung merkle-verify.mjs.`);
    process.exit(1);
  }
}

function kichThuoc(dir) {
  let tong = 0;
  for (const ten of readdirSync(dir)) {
    const p = join(dir, ten);
    const st = statSync(p);
    tong += st.isDirectory() ? kichThuoc(p) : st.size;
  }
  return tong;
}

console.log(`Da dung trang verifier tinh -> ${DIST}`);
console.log(`  kich thuoc  ${(kichThuoc(DIST) / 1024).toFixed(0)} KB`);
console.log(`  phu thuoc   ethers (ESM, chep nguyen van) — khong co gi khac`);
console.log('');
console.log('Chay thu:');
console.log('  cd verifier/dist && python -m http.server 8090');
console.log('  rồi mở http://localhost:8090');
