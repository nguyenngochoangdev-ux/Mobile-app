import { useQuery } from '@tanstack/react-query'
import { ScoringService } from '../../api/generated'
import type { ScoreResponse } from '../../api/generated'
import { NGUON_NHAN, nhanXepLoai, useRuleset } from '../../lib/ruleset'
import type { TieuChi } from '../../lib/ruleset'

/**
 * Điểm rèn luyện của sinh viên.
 *
 * Trang này cố ý **không chỉ hiện con số**. Nó hiện cả `evidenceHash` và `rulesetHash`, và nói
 * rõ tiêu chí nào chấm từ dữ liệu điểm danh còn tiêu chí nào là điểm mặc định.
 *
 * Lý do: một bảng điểm 5 tiêu chí trông như thể cả 5 đều được tính từ dữ liệu — điều không
 * đúng với hệ thống hiện tại. Giấu chuyện đó đi là để sinh viên tin một con số mà chính hệ
 * thống không đo được. Xem `docs/measurements.md` §11.3.
 *
 * **Tên tiêu chí, trần điểm và ngưỡng xếp loại đọc từ bộ quy tắc**, không gán cứng. Bản trước
 * viết thẳng `TRAN = { c1: 20, c2: 25, … }` vào đây; con số đó trùng bộ quy tắc một cách tình
 * cờ, và bộ quy tắc mới là thứ được băm rồi neo. Xem `PROJECT.md` §5.1 và `lib/ruleset.ts`.
 */
export default function ScorePage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['scoring', 'me'],
    queryFn: () => ScoringService.myScores(),
  })

  if (isLoading) return <p className="text-slate-400">Đang tải…</p>
  if (error) return <p className="text-rose-400">{(error as Error).message}</p>

  const items = data ?? []

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Điểm rèn luyện</h2>

      {items.length === 0 && (
        <p className="text-slate-400 text-sm">
          Chưa có kết quả chấm điểm nào. Điểm được tổng kết vào cuối mỗi học kỳ.
        </p>
      )}

      {items.map((s: ScoreResponse) => (
        <TheDiem key={s.id} s={s} />
      ))}
    </div>
  )
}

function TheDiem({ s }: { s: ScoreResponse }) {
  const xl = s.classification ? nhanXepLoai(s.classification) : undefined
  const ruleset = useRuleset(s.semester)
  const doc = ruleset.data?.doc
  const meta = ruleset.data?.meta

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-4 space-y-4">
      <div className="flex items-center gap-3">
        <div>
          <p className="text-sm text-slate-400">Học kỳ {s.semester}</p>
          <p className="text-3xl font-semibold text-slate-100">
            {s.total}
            {/* Thang điểm cũng từ bộ quy tắc — Thông tư có thể đổi, và đã từng đổi. */}
            <span className="text-base text-slate-500"> / {doc?.thang ?? '—'}</span>
          </p>
        </div>
        {xl && (
          <span className={`ml-auto rounded-md border px-2.5 py-1 text-sm font-medium ${xl.lop}`}>
            {xl.ten}
          </span>
        )}
      </div>

      {/*
        Chưa tải được bộ quy tắc thì KHÔNG vẽ bảng tiêu chí. Vẽ bằng trần đoán mò còn tệ hơn
        không vẽ: sinh viên không phân biệt được thanh tiến trình thật với thanh bịa.
      */}
      {doc ? (
        <ul className="space-y-1.5">
          {doc.tieuChi.map((t) => (
            <DongTieuChi key={t.ma} t={t} diem={diemCuaTieuChi(s, t.ma)} />
          ))}
        </ul>
      ) : (
        <p className="text-xs text-slate-500">
          {ruleset.isError
            ? 'Không tải được bộ quy tắc, nên không hiện được chi tiết từng tiêu chí.'
            : 'Đang tải bộ quy tắc…'}
        </p>
      )}

      {/*
        Phần dưới đây là thứ phân biệt trang này với một bảng điểm thông thường: nó nói ra
        điểm này dựa trên cái gì, và bao nhiêu phần trong đó KHÔNG phải kết quả đo.
      */}
      <div className="rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-3 space-y-2">
        <p className="text-xs font-medium text-slate-300">Điểm này kiểm lại được</p>

        <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-xs text-slate-400">
          <dt>Bộ quy tắc</dt>
          <dd className="text-slate-300">{s.rulesetVersion}</dd>
          <dt>Ruleset hash</dt>
          <dd className="font-mono text-slate-300 break-all">{s.rulesetHash}</dd>
          <dt>Evidence hash</dt>
          <dd className="font-mono text-slate-300 break-all">{s.evidenceHash}</dd>
          <dt>Chấm lúc</dt>
          <dd className="text-slate-300">
            {s.scoredAt ? new Date(s.scoredAt).toLocaleString('vi-VN') : '—'}
          </dd>
          <dt>Trạng thái neo</dt>
          <dd className={s.daNeo ? 'text-emerald-400' : 'text-amber-400'}>
            {s.daNeo ? 'đã neo lên blockchain' : 'chưa neo — job chạy 02:00 hằng đêm'}
          </dd>
        </dl>

        <p className="text-xs text-slate-500 leading-relaxed">
          <span className="text-slate-400">Evidence hash</span> cam kết vào đúng những bản ghi
          điểm danh đã dùng để tính điểm này.{' '}
          <span className="text-slate-400">Ruleset hash</span> cam kết vào đúng bộ quy tắc đã
          áp dụng. Có cả hai, bất kỳ ai cũng tính lại được điểm này mà không cần tin máy chủ
          của trường.
        </p>

        {/*
          Cảnh báo bộ quy tắc tự khai về chính nó. `rulesetVersion` của BẢN GHI ĐIỂM có thể
          khác bản đang áp dụng cho học kỳ, nên nói rõ khi lệch — nếu không, con số 50/40 ở
          đây lại mô tả một bộ quy tắc khác với bộ đã chấm ra điểm này.
        */}
        {meta && (
          <p className="text-xs text-amber-500/90 leading-relaxed border-l-2 border-amber-700/60 pl-2">
            <strong>{meta.diemTuDuLieu}/{doc?.thang ?? 100}</strong> điểm được chấm từ dữ liệu
            điểm danh; <strong>{meta.diemMacDinh}/{doc?.thang ?? 100}</strong> là điểm mặc định
            do hệ thống chưa có nguồn dữ liệu. Bộ quy tắc khai rõ từng tiêu chí — xem cột bên
            phải mỗi dòng.
            {meta.version !== s.rulesetVersion && (
              <>
                {' '}
                <strong className="text-rose-300">
                  Lưu ý: điểm này chấm bằng {s.rulesetVersion}, còn bộ quy tắc đang áp dụng cho
                  học kỳ là {meta.version}.
                </strong>
              </>
            )}
          </p>
        )}
      </div>
    </div>
  )
}

/**
 * Điểm của một tiêu chí, tra theo mã.
 *
 * Bảng `scores` có năm cột cố định `c1`…`c5` (ràng buộc `ck_score_range` từ V1 chốt theo Thông
 * tư 16/2015), nên tra bằng mã viết thường là đúng — nhưng nếu bộ quy tắc khai một mã ngoài
 * năm mã đó thì trả `undefined` chứ không đoán, và dòng đó hiện dấu gạch.
 */
function diemCuaTieuChi(s: ScoreResponse, ma: string): number | undefined {
  const key = ma.toLowerCase() as 'c1' | 'c2' | 'c3' | 'c4' | 'c5'
  return s[key] ?? undefined
}

function DongTieuChi({ t, diem }: { t: TieuChi; diem: number | undefined }) {
  const coDiem = diem !== undefined
  const tyLe = coDiem && t.toiDa > 0 ? (diem / t.toiDa) * 100 : 0

  return (
    <li className="flex items-center gap-3 text-sm">
      <span className="text-slate-400 flex-1 min-w-0 truncate" title={t.lyDo}>
        {t.ma} · {t.ten}
      </span>

      {/* Nguồn dữ liệu của tiêu chí — trường trung thực nhất của bộ quy tắc. */}
      <span
        className={`text-[10px] shrink-0 rounded px-1.5 py-0.5 border ${
          t.nguon === 'TU_DONG'
            ? 'border-emerald-900 bg-emerald-950 text-emerald-400'
            : t.nguon === 'HON_HOP'
              ? 'border-amber-900 bg-amber-950 text-amber-400'
              : 'border-slate-700 bg-slate-800 text-slate-400'
        }`}
      >
        {NGUON_NHAN[t.nguon]}
      </span>

      <span className="h-1.5 w-20 rounded bg-slate-800 overflow-hidden shrink-0">
        <span className="block h-full bg-sky-500" style={{ width: `${tyLe}%` }} />
      </span>
      <span className="tabular-nums text-slate-300 w-14 text-right shrink-0">
        {coDiem ? diem : '—'}/{t.toiDa}
      </span>
    </li>
  )
}
