import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { ScoringService } from '../../api/generated'
import type { KetQuaLuot } from '../../api/generated'
import { nhanXepLoai, useRuleset, xepLoaiGiamDan } from '../../lib/ruleset'
import type { RulesetDoc } from '../../lib/ruleset'
import { messageOf } from '../LoginPage'

/**
 * Chạy chấm điểm rèn luyện toàn khóa cho một học kỳ.
 *
 * Trước trang này, chấm điểm chỉ gọi được bằng `curl`. Đó là lỗ hổng của luồng demo: bước biến
 * dữ liệu điểm danh thành điểm số — phần "tự động hóa" trong tên đề tài — lại là bước duy nhất
 * không bấm được bằng tay người.
 *
 * Hai thứ trang này cố ý nói ra trước khi cho bấm:
 *
 * 1. **Chấm lại tạo lượt MỚI, không sửa lượt cũ.** Điểm đã chấm là phát biểu sắp được neo;
 *    sửa nó làm mọi Merkle proof fail vĩnh viễn.
 * 2. **Bao nhiêu phần của thang 100 là đo được.** Lấy thẳng từ bộ quy tắc, không viết cứng.
 *
 * Con số `milis` trong kết quả là **phép đo #3 của chương 11**, không phải thông tin trang trí.
 *
 * **Không có giá trị mặc định gán cứng.** Bản trước điền sẵn `2026-1` và `2026-1.v1`; hai chuỗi
 * đó hết hạn lặng lẽ ngay khi sang kỳ sau, và cán bộ sẽ chấm nhầm kỳ mà không thấy gì bất
 * thường. Học kỳ do cán bộ gõ, còn phiên bản bộ quy tắc **đọc từ bộ quy tắc đang áp dụng cho
 * kỳ đó**. Xem `PROJECT.md` §5.1.
 */
export default function StaffScoring() {
  const [semester, setSemester] = useState('')
  const [versionGoTay, setVersionGoTay] = useState<string | null>(null)
  const [daXacNhan, setDaXacNhan] = useState(false)

  const ruleset = useRuleset(semester.trim() || undefined)
  const doc = ruleset.data?.doc
  const meta = ruleset.data?.meta

  // Phiên bản dùng để chấm: lấy từ bộ quy tắc của học kỳ, trừ khi cán bộ cố ý gõ khác.
  //
  // Vẫn giữ ô nhập vì `chamHocKy` nạp bộ quy tắc từ `classpath:rulesets/<version>.json`, còn
  // `getRuleset` đọc từ CSDL. Trên một CSDL mới tinh chưa chấm lần nào thì chưa có bản ghi nào
  // để đọc — bỏ hẳn ô nhập là tự khóa mình khỏi lần chấm đầu tiên.
  const rulesetVersion = versionGoTay ?? meta?.version ?? ''

  const cham = useMutation({
    mutationFn: () =>
      ScoringService.runScoring({ semester: semester.trim(), rulesetVersion }),
    onSettled: () => setDaXacNhan(false),
  })

  const kq = cham.data
  const chamDuoc = !!semester.trim() && !!rulesetVersion

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Chấm điểm rèn luyện</h2>

      <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-4 space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <label className="space-y-1">
            <span className="text-xs text-slate-400">Học kỳ</span>
            {/* Placeholder gợi ý ĐỊNH DẠNG, không phải một học kỳ cụ thể. Backend chốt bằng
                regex `^\d{4}-[12]$` — ràng buộc của giao thức, không phải dữ liệu. */}
            <input
              value={semester}
              onChange={(e) => {
                setSemester(e.target.value)
                setDaXacNhan(false)
              }}
              placeholder="YYYY-1 hoặc YYYY-2"
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm"
            />
          </label>
          <label className="space-y-1">
            <span className="text-xs text-slate-400">
              Bộ quy tắc
              {versionGoTay === null && meta && (
                <span className="text-slate-500"> · lấy từ học kỳ</span>
              )}
            </span>
            <input
              value={rulesetVersion}
              onChange={(e) => {
                setVersionGoTay(e.target.value)
                setDaXacNhan(false)
              }}
              placeholder={semester.trim() ? 'chưa có bộ quy tắc cho kỳ này' : 'nhập học kỳ trước'}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm"
            />
          </label>
        </div>

        {versionGoTay !== null && meta && versionGoTay !== meta.version && (
          <p className="text-xs text-rose-400/90 leading-relaxed">
            Đang chấm bằng <strong>{versionGoTay}</strong>, khác bộ quy tắc đang áp dụng cho học
            kỳ ({meta.version}). Điểm chấm ra sẽ mang `ruleset_hash` của bản bạn gõ.
          </p>
        )}

        {meta && doc && (
          <div className="rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-3 space-y-2">
            <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-xs text-slate-400">
              <dt>Đang áp dụng</dt>
              <dd className="text-slate-300">{meta.version}</dd>
              <dt>Căn cứ</dt>
              <dd className="text-slate-300">{doc.canCu}</dd>
              <dt>Ruleset hash</dt>
              <dd className="font-mono text-slate-300 break-all">{meta.rulesetHash}</dd>
              <dt>Trạng thái neo</dt>
              <dd className={meta.daNeo ? 'text-emerald-400' : 'text-amber-400'}>
                {meta.daNeo ? 'đã neo lên blockchain' : 'chưa neo'}
              </dd>
            </dl>
            <p className="text-xs text-amber-500/90 leading-relaxed border-l-2 border-amber-700/60 pl-2">
              <strong>
                {meta.diemTuDuLieu}/{doc.thang}
              </strong>{' '}
              điểm chấm từ dữ liệu điểm danh;{' '}
              <strong>
                {meta.diemMacDinh}/{doc.thang}
              </strong>{' '}
              là điểm mặc định vì hệ thống chưa có nguồn dữ liệu. Sinh viên nhìn thấy đúng dòng
              này trên trang điểm của họ.
            </p>
          </div>
        )}

        {ruleset.isError && (
          <p className="text-xs text-rose-400">{messageOf(ruleset.error)}</p>
        )}

        {/*
          Bấm hai lần, không phải một. Chấm điểm không xóa gì và không gửi giao dịch nào, nên
          nó KHÔNG thuộc loại không hoàn tác — nhưng nó tạo ra một lượt mới mà job neo sẽ nhặt
          lên, và neo thì không hoàn tác được. Chỗ chặn rẻ nhất nằm ở đây.
        */}
        {!daXacNhan ? (
          <button
            onClick={() => setDaXacNhan(true)}
            disabled={!chamDuoc}
            className="rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-40 px-4 py-2 text-sm font-medium"
          >
            {chamDuoc ? `Chấm điểm học kỳ ${semester.trim()}` : 'Nhập học kỳ và bộ quy tắc'}
          </button>
        ) : (
          <div className="rounded-lg border border-amber-800/60 bg-amber-950/30 px-3 py-3 space-y-3">
            <p className="text-sm text-amber-200">
              Chấm toàn khóa cho học kỳ <strong>{semester}</strong> bằng bộ quy tắc{' '}
              <strong>{rulesetVersion}</strong>?
            </p>
            <p className="text-xs text-amber-400/80 leading-relaxed">
              Chấm lại tạo một <strong>lượt mới</strong>, không sửa lượt cũ. Điểm cũ vẫn còn
              nguyên và vẫn xác minh được — đó là chủ ý, vì sửa điểm đã neo làm mọi proof fail
              vĩnh viễn. Lượt mới sẽ được job neo nhặt lên lúc 02:00.
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => cham.mutate()}
                disabled={cham.isPending}
                className="rounded-lg bg-amber-600 hover:bg-amber-500 disabled:opacity-40 px-4 py-2 text-sm font-medium"
              >
                {cham.isPending ? 'Đang chấm…' : 'Chấm'}
              </button>
              <button
                onClick={() => setDaXacNhan(false)}
                className="rounded-lg border border-slate-700 hover:border-slate-600 px-4 py-2 text-sm"
              >
                Hủy
              </button>
            </div>
          </div>
        )}

        {cham.isError && <p className="text-sm text-rose-400">{messageOf(cham.error)}</p>}
      </div>

      {kq && <KetQua kq={kq} doc={doc} />}
    </div>
  )
}

/**
 * Kết quả một lượt chấm.
 *
 * Thứ tự và tập xếp loại lấy từ `phanLoai` của bộ quy tắc, sắp giảm dần theo ngưỡng. Bản trước
 * viết cứng `['XUAT_SAC', 'TOT', …]` ngay tại đây — bộ quy tắc thêm một mức là bảng này im lặng
 * nuốt mất cả một nhóm sinh viên.
 */
function KetQua({ kq, doc }: { kq: KetQuaLuot; doc?: RulesetDoc }) {
  const phanBo = kq.phanBoXepLoai ?? {}
  const tong = Object.values(phanBo).reduce((a, b) => a + b, 0)

  // Chưa tải được bộ quy tắc thì vẫn phải hiện đủ số người: rơi về đúng những mã backend trả
  // về. Thứ tự khi đó không đảm bảo, nhưng thà lộn xộn còn hơn giấu mất một nhóm.
  const thuTu = doc
    ? xepLoaiGiamDan(doc).map((p) => p.ma)
    : Object.keys(phanBo)

  const nguongCua = (ma: string) => doc?.phanLoai.find((p) => p.ma === ma)

  return (
    <div className="rounded-xl border border-emerald-900/60 bg-emerald-950/20 px-4 py-4 space-y-4">
      <div className="flex items-baseline gap-3">
        <h3 className="font-medium text-emerald-200">Đã chấm xong</h3>
        <span className="text-xs text-slate-400">
          lượt #{kq.runId} · học kỳ {kq.semester} · {kq.rulesetVersion}
        </span>
      </div>

      <div className="grid grid-cols-3 gap-3">
        <ChiSo nhan="Sinh viên" giaTri={String(kq.soSinhVien ?? 0)} />
        <ChiSo nhan="Có hoạt động" giaTri={String(kq.soCoHoatDong ?? 0)} />
        {/*
          Con số này đi thẳng vào chương 11. Hiện nó ra để lần chạy nào cũng là một phép đo,
          thay vì phải bới log tìm lại.
        */}
        <ChiSo nhan="Thời gian" giaTri={`${kq.milis ?? 0} ms`} />
      </div>

      <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-xs text-slate-400">
        <dt>Điểm thấp nhất</dt>
        <dd className="text-slate-300 tabular-nums">{kq.diemThapNhat}</dd>
        <dt>Điểm cao nhất</dt>
        <dd className="text-slate-300 tabular-nums">{kq.diemCaoNhat}</dd>
        <dt>Trung bình</dt>
        <dd className="text-slate-300 tabular-nums">{kq.diemTrungBinh?.toFixed(1)}</dd>
      </dl>

      <ul className="space-y-1.5">
        {thuTu
          .filter((k) => phanBo[k])
          .map((k) => {
            const nguong = nguongCua(k)
            return (
              <li key={k} className="flex items-center gap-3 text-sm">
                <span className="text-slate-400 flex-1">
                  {nhanXepLoai(k).ten}
                  {nguong && (
                    <span className="text-slate-600 text-xs">
                      {' '}
                      {nguong.tu}–{nguong.den}
                    </span>
                  )}
                </span>
                <span className="h-1.5 w-24 rounded bg-slate-800 overflow-hidden shrink-0">
                  <span
                    className="block h-full bg-emerald-500"
                    style={{ width: `${tong ? (phanBo[k] / tong) * 100 : 0}%` }}
                  />
                </span>
                <span className="tabular-nums text-slate-300 w-12 text-right shrink-0">
                  {phanBo[k]}
                </span>
              </li>
            )
          })}
      </ul>

      <p className="text-xs text-slate-500 leading-relaxed">
        Mỗi bản ghi mang <span className="text-slate-400">evidence_hash</span> — cam kết vào đúng
        tập bản ghi điểm danh đã dùng. Sinh viên xem được nó ở trang Điểm RL, và tính lại được
        điểm của mình mà không cần tin máy chủ của trường.
      </p>
    </div>
  )
}

function ChiSo({ nhan, giaTri }: { nhan: string; giaTri: string }) {
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-2">
      <p className="text-[11px] text-slate-500">{nhan}</p>
      <p className="text-lg font-semibold text-slate-100 tabular-nums">{giaTri}</p>
    </div>
  )
}
