/**
 * Giao diện verifier — lớp mỏng trên `src/bundle.mjs`.
 *
 * **Không có logic xác minh nào trong file này.** Mọi phép kiểm nằm ở `src/bundle.mjs`, nơi
 * đã có 37 test và một bộ fixture chung với backend. Nếu trang web tự tính lại một thứ gì đó
 * thì sẽ có hai bản hiện thực của cùng một quy tắc, và chúng sẽ trôi khỏi nhau — đúng cạm
 * bẫy mà cả tầng canonicalization sinh ra để chặn.
 *
 * File này chỉ làm ba việc: đọc tệp, gọi `verifyBundle`, vẽ kết quả.
 */
import { verifyBundle } from '../src/bundle.mjs';
import { chainReader } from '../src/chain.mjs';
import { trustedChainFor, AMOY, DEFAULT_RPC_URL } from '../src/trusted-chain.mjs';

const $ = (id) => document.getElementById(id);
const el = (tag, cls, text) => {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
};

$('rpc').value = DEFAULT_RPC_URL;
$('lienket').href = `https://amoy.polygonscan.com/address/${AMOY.anchorRegistry}#code`;

// ------------------------------------------------------------------ nhận tệp

const tha = $('tha');
const nhapTep = $('tep');

tha.addEventListener('click', () => nhapTep.click());
nhapTep.addEventListener('change', () => {
  if (nhapTep.files[0]) docTep(nhapTep.files[0]);
});

for (const ten of ['dragenter', 'dragover']) {
  tha.addEventListener(ten, (e) => {
    e.preventDefault();
    tha.classList.add('keo');
  });
}
for (const ten of ['dragleave', 'drop']) {
  tha.addEventListener(ten, (e) => {
    e.preventDefault();
    tha.classList.remove('keo');
  });
}
tha.addEventListener('drop', (e) => {
  const f = e.dataTransfer?.files?.[0];
  if (f) docTep(f);
});

async function docTep(file) {
  let bundle;
  try {
    bundle = JSON.parse(await file.text());
  } catch (err) {
    return veLoi(`Không đọc được ${file.name}: ${err.message}`);
  }
  await chay(bundle, file.name);
}

// ------------------------------------------------------------------ chạy

async function chay(bundle, tenTep) {
  const offline = $('offline').checked;

  // Danh sách tin cậy lấy từ MÃ NGUỒN của trang này, không lấy từ tệp. `chain.chainId` chỉ
  // dùng để CHỌN cấu hình nào đem ra đối chiếu — bản thân nó không được tin. Xem
  // src/trusted-chain.mjs để biết vì sao đây là chỗ quan trọng nhất.
  let trusted;
  try {
    trusted = trustedChainFor(bundle?.chain?.chainId);
  } catch (err) {
    return veLoi(err.message);
  }

  veDangChay(tenTep);

  const reader = offline ? null : chainReader($('rpc').value.trim() || DEFAULT_RPC_URL, trusted);

  let kq;
  try {
    kq = await verifyBundle(bundle, trusted, reader);
  } catch (err) {
    return veLoi(`Lỗi khi xác minh: ${err.message}`);
  }
  ve(bundle, kq, trusted, offline);
}

// ------------------------------------------------------------------ vẽ

function khung() {
  const k = $('ketqua');
  k.textContent = '';
  k.classList.remove('an');
  return k;
}

function veDangChay(tenTep) {
  const k = khung();
  const the = el('div', 'the');
  the.append(el('h2', null, 'Đang xác minh'), el('p', 'phu', tenTep));
  k.append(the);
}

function veLoi(thongDiep) {
  const k = khung();
  k.append(el('div', 'ketluan truot', `✗ ${thongDiep}`));
}

function ve(bundle, kq, trusted, offline) {
  const k = khung();
  const p = bundle.credential?.payload ?? {};

  // ---- kết luận, đặt TRÊN CÙNG -------------------------------------------
  const lop = kq.ok ? 'dat' : (kq.offline ? 'bo' : 'truot');
  const dau = kq.ok ? '✓' : (kq.offline ? '–' : '✗');
  k.append(el('div', `ketluan ${lop}`, `${dau} ${kq.summary}`));

  // ---- credential nói gì --------------------------------------------------
  const theCred = el('div', 'the');
  theCred.append(el('h2', null, 'Credential'));
  const dl = el('dl');
  const dong = (nhan, giaTri, ma) => {
    dl.append(el('dt', null, nhan));
    dl.append(el('dd', ma ? 'ma' : null, giaTri));
  };
  dong('Cấp cho', `${p.studentName ?? '?'} (${p.studentCode ?? '?'})`);
  dong('Loại', p.type ?? '?');
  dong('Học kỳ', p.claims?.semester ?? '?');
  dong('Hoạt động', `${p.claims?.activityCount ?? '?'} · ${p.claims?.totalPoints ?? '?'} điểm`);
  dong('Cấp ngày', p.issuedAt ?? '?');
  dong('Hết hạn', p.expiresAt ?? 'không hạn');
  dong('Bên cấp', p.issuerAddress ?? '?', true);
  dong('Neo tại', `${bundle.anchor?.domain}/${bundle.anchor?.batchId}`);
  if (bundle.anchor?.txHash) {
    dl.append(el('dt', null, 'Giao dịch'));
    const dd = el('dd');
    const a = el('a', 'ma', bundle.anchor.txHash);
    a.href = `https://amoy.polygonscan.com/tx/${bundle.anchor.txHash}`;
    a.target = '_blank';
    a.rel = 'noopener';
    dd.append(a);
    dl.append(dd);
  }
  dong('Mạng', `${trusted.name} (chainId ${trusted.chainId})`);
  theCred.append(dl);
  k.append(theCred);

  // ---- từng phép kiểm -----------------------------------------------------
  const theKiem = el('div', 'the');
  theKiem.append(el('h2', null, 'Sáu phép kiểm'));
  for (const c of kq.checks) {
    const hang = el('div', 'kiem');
    const lopDau = c.skipped ? 'bo' : (c.pass ? 'dat' : 'truot');
    hang.append(el('span', `dau ${lopDau}`, c.skipped ? '–' : (c.pass ? '✓' : '✗')));

    const mota = el('div', 'mota');
    mota.append(el('div', null, c.label));
    mota.append(el('div', 'chitiet', c.detail));
    hang.append(mota);
    theKiem.append(hang);
  }
  k.append(theKiem);

  if (offline) {
    k.append(el('p', 'canhbao',
      'Chế độ offline: ba phép kiểm cần blockchain đã bị bỏ qua. Kết quả này KHÔNG nói'
      + ' credential đã được neo hay còn hiệu lực.'));
  }

  const lai = el('div', 'hang');
  const nut = el('button', 'phu2', 'Xác minh tệp khác');
  nut.addEventListener('click', () => nhapTep.click());
  lai.append(nut);
  k.append(lai);
}
