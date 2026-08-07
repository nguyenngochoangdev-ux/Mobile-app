import { useQuery } from '@tanstack/react-query'
import { ScoringService } from '../api/generated'

/**
 * Đọc bộ quy tắc chấm điểm — **nguồn sự thật duy nhất** cho trần điểm, tên tiêu chí và ngưỡng
 * xếp loại.
 *
 * Trước file này, `ScorePage` gán cứng `TRAN = { c1: 20, c2: 25, ... }` và danh sách tên tiêu
 * chí. Chúng trùng với bộ quy tắc **một cách tình cờ**, và sự trùng đó không có gì bảo vệ. Bộ
 * quy tắc được băm nguyên văn rồi neo ở miền `RULESET`; ra bản `v2` với trần khác thì trang vẫn
 * vẽ theo trần cũ, sinh viên vẫn thấy "8/20" trong khi quy tắc đã neo nói tối đa 25, và không
 * phép kiểm nào bắt được. Xem `PROJECT.md` §5.1.
 *
 * `jsonBody` là **nguyên văn byte đã băm**. Phân tích nó ở đây chỉ để đọc; không có phép băm
 * nào chạy trên kết quả phân tích, nên `JSON.parse` không đụng tới tính toàn vẹn. Cùng lối với
 * `RulesetDoc` phía Java.
 */

export type Nguon = 'TU_DONG' | 'MAC_DINH' | 'HON_HOP'

export type QuyTac = { ma: string; diem: number; dieuKien: string; moTa: string }

export type TieuChi = {
  ma: string
  ten: string
  toiDa: number
  nguon: Nguon
  diemNen: number
  lyDo: string
  quyTac: QuyTac[]
}

export type PhanLoai = { ma: string; tu: number; den: number }

export type RulesetDoc = {
  version: string
  semester: string
  canCu: string
  thang: number
  tieuChi: TieuChi[]
  phanLoai: PhanLoai[]
  hanChe: string[]
}

/**
 * Nhãn tiếng Việt và màu của mã xếp loại.
 *
 * **Vì sao cái này được phép nằm trong code**, trong khi trần điểm thì không: bộ quy tắc chỉ
 * khai `ma`, `tu`, `den` — không có tên hiển thị. Thêm trường `ten` vào tệp là đổi byte của
 * tệp, tức đổi `ruleset_hash`, tức làm mọi bản ghi điểm đã chấm không tái tạo được nữa. Nên
 * đây là bảng dịch mã sang tiếng Việt, không phải dữ liệu nghiệp vụ. **Ngưỡng điểm** thì lấy
 * từ `phanLoai`, vì đó mới là thứ nhà trường có thể muốn đổi.
 */
const NHAN: Record<string, { ten: string; lop: string }> = {
  XUAT_SAC: { ten: 'Xuất sắc', lop: 'bg-emerald-950 text-emerald-300 border-emerald-900' },
  TOT: { ten: 'Tốt', lop: 'bg-emerald-950 text-emerald-300 border-emerald-900' },
  KHA: { ten: 'Khá', lop: 'bg-sky-950 text-sky-300 border-sky-900' },
  TRUNG_BINH: { ten: 'Trung bình', lop: 'bg-amber-950 text-amber-300 border-amber-900' },
  YEU: { ten: 'Yếu', lop: 'bg-orange-950 text-orange-300 border-orange-900' },
  KEM: { ten: 'Kém', lop: 'bg-rose-950 text-rose-300 border-rose-900' },
}

/** Mã lạ thì hiện nguyên mã, đừng nuốt mất. Bộ quy tắc mới có thể thêm mã chưa dịch. */
export function nhanXepLoai(ma: string): { ten: string; lop: string } {
  return NHAN[ma] ?? { ten: ma, lop: 'bg-slate-800 text-slate-300 border-slate-700' }
}

export const NGUON_NHAN: Record<Nguon, string> = {
  TU_DONG: 'chấm từ dữ liệu',
  MAC_DINH: 'điểm mặc định',
  HON_HOP: 'một phần từ dữ liệu',
}

/**
 * Bộ quy tắc đang áp dụng cho một học kỳ.
 *
 * Trả về cả phần đã phân tích (`doc`) và phần backend tính sẵn (`meta`) — `diemTuDuLieu` /
 * `diemMacDinh` tính ở backend từ chính bộ quy tắc, nên không lệch được khỏi tệp đang dùng.
 */
export function useRuleset(semester: string | undefined) {
  return useQuery({
    queryKey: ['ruleset', semester],
    queryFn: async () => {
      const meta = await ScoringService.getRuleset(semester!)
      return { meta, doc: JSON.parse(meta.jsonBody!) as RulesetDoc }
    },
    enabled: !!semester,
    // Bộ quy tắc là tài liệu công khai và không đổi trong một học kỳ — không cần tải lại.
    staleTime: Infinity,
    // Học kỳ chưa có bộ quy tắc thì 404. Đó là câu trả lời hợp lệ, đừng thử lại.
    retry: false,
  })
}

/** Xếp loại từ cao xuống thấp, theo đúng ngưỡng bộ quy tắc khai. */
export function xepLoaiGiamDan(doc: RulesetDoc): PhanLoai[] {
  return [...doc.phanLoai].sort((a, b) => b.tu - a.tu)
}
