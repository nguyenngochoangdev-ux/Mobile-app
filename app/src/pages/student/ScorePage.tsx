import { useQuery } from '@tanstack/react-query'
import { ScoringService } from '../../api/generated'
import type { ScoreResponse } from '../../api/generated'

/**
 * Điểm rèn luyện của sinh viên.
 *
 * Trang này cố ý **không chỉ hiện con số**. Nó hiện cả `evidenceHash` và `rulesetHash`, và nói
 * rõ tiêu chí nào chấm từ dữ liệu điểm danh còn tiêu chí nào là điểm mặc định.
 *
 * Lý do: một bảng điểm 5 tiêu chí trông như thể cả 5 đều được tính từ dữ liệu — điều không
 * đúng với hệ thống hiện tại. Giấu chuyện đó đi là để sinh viên tin một con số mà chính hệ
 * thống không đo được. Xem `docs/measurements.md` §11.3.
 */

const TEN_TIEU_CHI: Record<string, string> = {
  c1: 'Ý thức và kết quả học tập',
  c2: 'Ý thức chấp hành nội quy',
  c3: 'Tham gia hoạt động chính trị, xã hội',
  c4: 'Ý thức công dân trong quan hệ cộng đồng',
  c5: 'Công tác cán bộ lớp, đoàn thể',
}

const TRAN: Record<string, number> = { c1: 20, c2: 25, c3: 20, c4: 25, c5: 10 }

const XEP_LOAI: Record<string, { nhan: string; lop: string }> = {
  XUAT_SAC: { nhan: 'Xuất sắc', lop: 'bg-emerald-950 text-emerald-300 border-emerald-900' },
  TOT: { nhan: 'Tốt', lop: 'bg-emerald-950 text-emerald-300 border-emerald-900' },
  KHA: { nhan: 'Khá', lop: 'bg-sky-950 text-sky-300 border-sky-900' },
  TRUNG_BINH: { nhan: 'Trung bình', lop: 'bg-amber-950 text-amber-300 border-amber-900' },
  YEU: { nhan: 'Yếu', lop: 'bg-orange-950 text-orange-300 border-orange-900' },
  KEM: { nhan: 'Kém', lop: 'bg-rose-950 text-rose-300 border-rose-900' },
}

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
  const xl = s.classification ? XEP_LOAI[s.classification] : undefined
  const { data: ruleset } = useQuery({
    queryKey: ['ruleset', s.semester],
    queryFn: () => ScoringService.getRuleset(s.semester!),
    // Bộ quy tắc là tài liệu công khai và không đổi trong một học kỳ — không cần tải lại.
    staleTime: Infinity,
  })

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-4 space-y-4">
      <div className="flex items-center gap-3">
        <div>
          <p className="text-sm text-slate-400">Học kỳ {s.semester}</p>
          <p className="text-3xl font-semibold text-slate-100">
            {s.total}
            <span className="text-base text-slate-500"> / 100</span>
          </p>
        </div>
        {xl && (
          <span className={`ml-auto rounded-md border px-2.5 py-1 text-sm font-medium ${xl.lop}`}>
            {xl.nhan}
          </span>
        )}
      </div>

      <ul className="space-y-1.5">
        {(['c1', 'c2', 'c3', 'c4', 'c5'] as const).map((k) => {
          const diem = (s[k] ?? 0) as number
          const tran = TRAN[k]
          return (
            <li key={k} className="flex items-center gap-3 text-sm">
              <span className="text-slate-400 flex-1 min-w-0 truncate">
                {k.toUpperCase()} · {TEN_TIEU_CHI[k]}
              </span>
              <span className="h-1.5 w-20 rounded bg-slate-800 overflow-hidden shrink-0">
                <span
                  className="block h-full bg-sky-500"
                  style={{ width: `${(diem / tran) * 100}%` }}
                />
              </span>
              <span className="tabular-nums text-slate-300 w-14 text-right shrink-0">
                {diem}/{tran}
              </span>
            </li>
          )
        })}
      </ul>

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

        {ruleset && (
          <p className="text-xs text-amber-500/90 leading-relaxed border-l-2 border-amber-700/60 pl-2">
            <strong>{ruleset.diemTuDuLieu}/100</strong> điểm được chấm từ dữ liệu điểm danh;{' '}
            <strong>{ruleset.diemMacDinh}/100</strong> là điểm mặc định do hệ thống chưa có
            nguồn dữ liệu (kỷ luật, chức vụ cán bộ lớp). Bộ quy tắc khai rõ từng tiêu chí —
            xem tệp quy tắc công khai.
          </p>
        )}
      </div>
    </div>
  )
}
