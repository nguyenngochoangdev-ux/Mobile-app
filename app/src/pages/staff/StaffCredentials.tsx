import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { CredentialService, OrganizationsService, StudentsService } from '../../api/generated'
import type { CredentialResponse } from '../../api/generated'
import { messageOf } from '../LoginPage'

/**
 * Cấp và thu hồi chứng nhận — bản giao diện của `scripts\credential-now.ps1`.
 *
 * **Vì sao hai con số không phải ô nhập trống.** `activityCount` và `totalPoints` đi thẳng vào
 * payload được ký rồi neo. Neo xong thì không sửa được: muốn đổi phải thu hồi rồi cấp lại. Một
 * con số gõ nhầm ở đây không phải lỗi nhập liệu bình thường — nó là một phát biểu sai, mang chữ
 * ký của trường, tồn tại vĩnh viễn trên chuỗi. Nên backend đếm từ bảng `attendances` và điền
 * sẵn; cán bộ sửa được, nhưng phải cố ý sửa và nhìn thấy mình đang lệch khỏi dữ liệu.
 * Xem `PROJECT.md` §5.1.
 *
 * Trang cũng in tỉ lệ **xác minh bằng máy**. Cấp chứng nhận cho một sinh viên có 8 hoạt động mà
 * 7 trong đó do cán bộ gõ tay là một việc khác hẳn về mặt bằng chứng, và cán bộ nên biết điều
 * đó *trước* khi ký, chứ không phải khi bị hỏi ở buổi bảo vệ.
 */
export default function StaffCredentials() {
  const [mssv, setMssv] = useState('')
  const [semester, setSemester] = useState('')
  const [daTra, setDaTra] = useState<{ mssv: string; semester: string } | null>(null)

  const sinhVien = useQuery({
    queryKey: ['student', 'mssv', daTra?.mssv],
    queryFn: () => StudentsService.getByMssv(daTra!.mssv),
    enabled: !!daTra,
    retry: false,
  })

  const traDuoc = !!mssv.trim() && !!semester.trim()

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold">Cấp chứng nhận</h2>

      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (traDuoc) {
            setDaTra({ mssv: mssv.trim().toUpperCase(), semester: semester.trim() })
          }
        }}
        className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-4 space-y-3"
      >
        <div className="grid grid-cols-2 gap-3">
          <label className="space-y-1">
            <span className="text-xs text-slate-400">MSSV</span>
            <input
              value={mssv}
              onChange={(e) => setMssv(e.target.value)}
              placeholder="Mã số sinh viên"
              autoCapitalize="characters"
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm font-mono"
            />
          </label>
          <label className="space-y-1">
            <span className="text-xs text-slate-400">Học kỳ</span>
            {/* Gợi ý ĐỊNH DẠNG, không phải một học kỳ cụ thể — backend chốt bằng regex
                `^\d{4}-[12]$`. Đó là ràng buộc giao thức, không phải dữ liệu. */}
            <input
              value={semester}
              onChange={(e) => setSemester(e.target.value)}
              placeholder="YYYY-1 hoặc YYYY-2"
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm"
            />
          </label>
        </div>
        <button
          type="submit"
          disabled={!traDuoc}
          className="rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-40 px-4 py-2 text-sm font-medium"
        >
          Tra cứu
        </button>
      </form>

      {sinhVien.isError && <p className="text-sm text-rose-400">{messageOf(sinhVien.error)}</p>}

      {sinhVien.data?.id != null && daTra && (
        <ThongTinSinhVien
          studentId={sinhVien.data.id}
          hoTen={sinhVien.data.fullName ?? ''}
          lop={sinhVien.data.classCode ?? ''}
          mssv={daTra.mssv}
          semester={daTra.semester}
        />
      )}
    </div>
  )
}

function ThongTinSinhVien({
  studentId,
  hoTen,
  lop,
  mssv,
  semester,
}: {
  studentId: number
  hoTen: string
  lop: string
  mssv: string
  semester: string
}) {
  const queryClient = useQueryClient()

  const goiY = useQuery({
    queryKey: ['credentials', 'goi-y', studentId, semester],
    queryFn: () => CredentialService.issueSuggestion(studentId, semester),
    retry: false,
  })

  const daCo = useQuery({
    queryKey: ['credentials', 'student', studentId],
    queryFn: () => CredentialService.credentialsOfStudent(studentId),
  })

  function lamMoi() {
    queryClient.invalidateQueries({ queryKey: ['credentials', 'student', studentId] })
    queryClient.invalidateQueries({ queryKey: ['credentials', 'goi-y', studentId] })
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3">
        <p className="font-medium text-slate-100">{hoTen}</p>
        <p className="text-xs text-slate-500 font-mono">
          {mssv}
          {lop ? ` · ${lop}` : ''}
        </p>
      </div>

      {goiY.isError && <p className="text-sm text-rose-400">{messageOf(goiY.error)}</p>}

      {goiY.data && (
        <FormCap
          studentId={studentId}
          semester={semester}
          goiY={goiY.data}
          onXong={lamMoi}
        />
      )}

      {(daCo.data?.length ?? 0) > 0 && (
        <div className="space-y-2">
          <h3 className="text-sm font-medium text-slate-300">Chứng nhận đã cấp</h3>
          {daCo.data!.map((c) => (
            <TheCredential key={c.id} c={c} onDoi={lamMoi} />
          ))}
        </div>
      )}
    </div>
  )
}

/**
 * Số liệu đề xuất. Khai lỏng vì client sinh từ OpenAPI đánh dấu mọi trường là tùy chọn —
 * `?? 0` bên dưới là để đọc được, không phải để che một giá trị thiếu.
 */
type GoiY = {
  activityCount?: number
  totalPoints?: number
  verifiedCount?: number
  daCap?: number
}

function FormCap({
  studentId,
  semester,
  goiY,
  onXong,
}: {
  studentId: number
  semester: string
  goiY: GoiY
  onXong: () => void
}) {
  const deXuatHoatDong = goiY.activityCount ?? 0
  const deXuatDiem = goiY.totalPoints ?? 0
  const daXacMinh = goiY.verifiedCount ?? 0

  const [soHoatDong, setSoHoatDong] = useState(String(deXuatHoatDong))
  const [tongDiem, setTongDiem] = useState(String(deXuatDiem))
  const [orgGoTay, setOrgGoTay] = useState('')
  const [daXacNhan, setDaXacNhan] = useState(false)

  const toChuc = useQuery({
    queryKey: ['organizations'],
    queryFn: () => OrganizationsService.list1(),
    staleTime: Infinity,
  })

  // Tổ chức mặc định: cái đầu tiên có type DOAN, giống `credential-now.ps1`. Chọn sẵn để cán bộ
  // không phải quyết một thứ họ không có cơ sở để quyết khác đi.
  const dsToChuc = toChuc.data ?? []
  const macDinh = dsToChuc.find((o) => o.type === 'DOAN')?.id ?? dsToChuc[0]?.id
  const orgDangChon = orgGoTay || (macDinh != null ? String(macDinh) : '')

  const cap = useMutation({
    mutationFn: () =>
      CredentialService.issueCredential({
        studentId,
        issuerOrgId: Number(orgDangChon),
        semester,
        activityCount: Number(soHoatDong),
        totalPoints: Number(tongDiem),
      }),
    onSuccess: () => {
      setDaXacNhan(false)
      onXong()
    },
    onError: () => setDaXacNhan(false),
  })

  if (goiY.daCap != null) {
    return (
      <p className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3 text-sm text-slate-400">
        Sinh viên này <strong className="text-slate-300">đã có chứng nhận</strong> cho học kỳ{' '}
        {semester} (#{goiY.daCap}). Muốn cấp bản mới thì thu hồi bản cũ trước — chứng nhận là
        phát biểu đã ký, không sửa được.
      </p>
    )
  }

  if (deXuatHoatDong === 0) {
    return (
      <p className="rounded-xl border border-amber-800/60 bg-amber-950/30 px-4 py-3 text-sm text-amber-200">
        Sinh viên này <strong>chưa có bản ghi điểm danh nào trong học kỳ {semester}</strong>.
        Không cấp chứng nhận rỗng — một chứng nhận 0 hoạt động vẫn được ký và neo vĩnh viễn như
        mọi chứng nhận khác.
      </p>
    )
  }

  const soNhap = Number(soHoatDong)
  const diemNhap = Number(tongDiem)
  const hopLe = Number.isInteger(soNhap) && soNhap >= 0 && Number.isInteger(diemNhap) && diemNhap >= 0
  const lechDuLieu = hopLe && (soNhap !== deXuatHoatDong || diemNhap !== deXuatDiem)

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-4 space-y-4">
      <div className="rounded-lg border border-slate-800 bg-slate-950/60 px-3 py-3 space-y-2">
        <p className="text-xs font-medium text-slate-300">
          Số liệu đọc từ điểm danh học kỳ {semester}
        </p>
        <p className="text-sm text-slate-300">
          <strong className="tabular-nums">{deXuatHoatDong}</strong> hoạt động ·{' '}
          <strong className="tabular-nums">{deXuatDiem}</strong> điểm ·{' '}
          <span className={daXacMinh === deXuatHoatDong ? 'text-emerald-400' : 'text-amber-400'}>
            {daXacMinh}/{deXuatHoatDong} xác minh bằng máy
          </span>
        </p>
        {daXacMinh < deXuatHoatDong && (
          <p className="text-xs text-amber-500/90 leading-relaxed border-l-2 border-amber-700/60 pl-2">
            {deXuatHoatDong - daXacMinh} bản ghi do cán bộ điểm danh tay, không qua QR. Máy không
            xác minh được chúng — đây là vấn đề oracle, và blockchain không giải quyết được nó.
            Chứng nhận sẽ nói sinh viên có {deXuatHoatDong} hoạt động mà không phân biệt nguồn.
          </p>
        )}
        <p className="text-xs text-slate-500 leading-relaxed">
          Chỉ đếm sự kiện thuộc đúng học kỳ này. Sự kiện chưa xác định được kỳ thì bỏ qua, không
          đoán — xem V8.
        </p>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <label className="space-y-1">
          <span className="text-xs text-slate-400">Số hoạt động</span>
          <input
            value={soHoatDong}
            onChange={(e) => {
              setSoHoatDong(e.target.value)
              setDaXacNhan(false)
            }}
            inputMode="numeric"
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm tabular-nums"
          />
        </label>
        <label className="space-y-1">
          <span className="text-xs text-slate-400">Tổng điểm hoạt động</span>
          <input
            value={tongDiem}
            onChange={(e) => {
              setTongDiem(e.target.value)
              setDaXacNhan(false)
            }}
            inputMode="numeric"
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm tabular-nums"
          />
        </label>
      </div>

      {!hopLe && (
        <p className="text-xs text-rose-400">Hai ô phải là số nguyên không âm.</p>
      )}

      {lechDuLieu && (
        <p className="text-xs text-rose-400/90 leading-relaxed">
          Số đang nhập <strong>khác số đọc từ dữ liệu</strong> ({deXuatHoatDong} hoạt động,{' '}
          {deXuatDiem} điểm). Chứng nhận sẽ mang số bạn nhập, và sau khi neo thì không sửa được.
        </p>
      )}

      <label className="space-y-1 block">
        <span className="text-xs text-slate-400">Tổ chức cấp</span>
        <select
          value={orgDangChon}
          onChange={(e) => {
            setOrgGoTay(e.target.value)
            setDaXacNhan(false)
          }}
          className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm"
        >
          {dsToChuc.map((o) => (
            <option key={o.id} value={o.id}>
              {o.name} · {o.type}
            </option>
          ))}
        </select>
      </label>

      {/*
        Bấm hai lần. Cấp chứng nhận chưa chạm chuỗi — nhưng nó KÝ bằng khóa của trường, và job
        neo sẽ nhặt nó lên lúc 02:00 mà không hỏi ai. Sau đó thì chỉ còn đường thu hồi.
      */}
      {!daXacNhan ? (
        <button
          onClick={() => setDaXacNhan(true)}
          disabled={!orgDangChon || !hopLe}
          className="rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-40 px-4 py-2 text-sm font-medium"
        >
          Cấp chứng nhận
        </button>
      ) : (
        <div className="rounded-lg border border-amber-800/60 bg-amber-950/30 px-3 py-3 space-y-3">
          <p className="text-sm text-amber-200">
            Ký chứng nhận {soNhap} hoạt động · {diemNhap} điểm cho học kỳ {semester}?
          </p>
          <p className="text-xs text-amber-400/80 leading-relaxed">
            Chữ ký dùng khóa của trường. Job neo lúc 02:00 sẽ đưa nó lên blockchain, và{' '}
            <strong>từ đó không sửa được nữa</strong> — chỉ còn đường thu hồi rồi cấp lại.
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => cap.mutate()}
              disabled={cap.isPending}
              className="rounded-lg bg-amber-600 hover:bg-amber-500 disabled:opacity-40 px-4 py-2 text-sm font-medium"
            >
              {cap.isPending ? 'Đang ký…' : 'Ký và cấp'}
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

      {cap.isError && <p className="text-sm text-rose-400">{messageOf(cap.error)}</p>}
    </div>
  )
}

function TheCredential({ c, onDoi }: { c: CredentialResponse; onDoi: () => void }) {
  const [lyDo, setLyDo] = useState('')
  const [moThuHoi, setMoThuHoi] = useState(false)
  const [loiBundle, setLoiBundle] = useState<string | null>(null)
  const [dangTai, setDangTai] = useState(false)

  const doiTrangThai = useMutation({
    mutationFn: (revoked: boolean) =>
      CredentialService.revokeCredential(c.id!, { revoked, reason: lyDo || undefined }),
    onSuccess: () => {
      setMoThuHoi(false)
      setLyDo('')
      onDoi()
    },
  })

  async function taiBundle() {
    setDangTai(true)
    setLoiBundle(null)
    try {
      const bundle = await CredentialService.credentialBundle(c.id!)

      // Tải thẳng trong trình duyệt. `JSON.stringify(…, 2)` cho dễ đọc — bundle KHÔNG bị băm
      // nguyên văn, chỉ `credential.payload` bên trong mới quan trọng từng byte, và nó là một
      // chuỗi con nên định dạng lại vỏ ngoài không đụng tới nó.
      const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${c.studentCode}-${c.semester}.json`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setLoiBundle(messageOf(e))
    } finally {
      setDangTai(false)
    }
  }

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/60 px-4 py-3 space-y-3">
      <div className="flex items-start gap-3">
        <div className="min-w-0">
          <p className="font-medium text-slate-100">
            #{c.id} · học kỳ {c.semester}
          </p>
          <p className="text-sm text-slate-400 mt-0.5">
            {c.activityCount} hoạt động · {c.totalPoints} điểm ·{' '}
            {c.issuedAt ? new Date(c.issuedAt).toLocaleDateString('vi-VN') : ''}
          </p>
        </div>
        <span
          className={`ml-auto shrink-0 rounded-md border px-2 py-1 text-xs font-medium ${
            c.revoked
              ? 'bg-rose-950 text-rose-300 border-rose-900'
              : 'bg-emerald-950 text-emerald-300 border-emerald-900'
          }`}
        >
          {c.revoked ? 'Đã thu hồi' : 'Còn hiệu lực'}
        </span>
      </div>

      <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1 text-xs text-slate-400">
        <dt>Leaf hash</dt>
        <dd className="font-mono text-slate-300 break-all">{c.leafHash}</dd>
        <dt>Status index</dt>
        <dd className="font-mono text-slate-300">{c.statusListIndex}</dd>
      </dl>

      <div className="flex flex-wrap gap-2">
        <button
          onClick={taiBundle}
          disabled={dangTai}
          className="rounded-lg border border-slate-700 hover:border-slate-600 disabled:opacity-40 px-3 py-1.5 text-sm"
        >
          {dangTai ? 'Đang tạo…' : 'Tải bundle'}
        </button>

        {c.revoked ? (
          <button
            onClick={() => doiTrangThai.mutate(false)}
            disabled={doiTrangThai.isPending}
            className="rounded-lg border border-slate-700 hover:border-slate-600 disabled:opacity-40 px-3 py-1.5 text-sm"
          >
            {doiTrangThai.isPending ? 'Đang gửi…' : 'Bỏ thu hồi'}
          </button>
        ) : (
          !moThuHoi && (
            <button
              onClick={() => setMoThuHoi(true)}
              className="rounded-lg border border-rose-900 text-rose-300 hover:border-rose-800 px-3 py-1.5 text-sm"
            >
              Thu hồi
            </button>
          )
        )}
      </div>

      {moThuHoi && (
        <div className="rounded-lg border border-rose-900/60 bg-rose-950/20 px-3 py-3 space-y-3">
          <input
            value={lyDo}
            onChange={(e) => setLyDo(e.target.value)}
            placeholder="Lý do thu hồi"
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm"
          />
          {/*
            Nói rõ thứ tự: giao dịch TRƯỚC, CSDL SAU. Cán bộ cần biết vì sao nút này chậm hơn
            hẳn mọi nút khác — nó đang đợi Amoy xác nhận, không phải treo.
          */}
          <p className="text-xs text-rose-300/80 leading-relaxed">
            Thu hồi <strong>gửi giao dịch lên blockchain trước</strong>, ghi cơ sở dữ liệu sau.
            Mất vài giây và tốn phí gas. Lật bit lại được, nhưng mỗi lần lật để lại một sự kiện
            vĩnh viễn trên chuỗi.
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => doiTrangThai.mutate(true)}
              disabled={doiTrangThai.isPending}
              className="rounded-lg bg-rose-700 hover:bg-rose-600 disabled:opacity-40 px-3 py-1.5 text-sm font-medium"
            >
              {doiTrangThai.isPending ? 'Đang gửi giao dịch…' : 'Thu hồi'}
            </button>
            <button
              onClick={() => setMoThuHoi(false)}
              className="rounded-lg border border-slate-700 hover:border-slate-600 px-3 py-1.5 text-sm"
            >
              Hủy
            </button>
          </div>
        </div>
      )}

      {doiTrangThai.isError && (
        <p className="text-xs text-rose-400">{messageOf(doiTrangThai.error)}</p>
      )}

      {loiBundle && (
        <p className="text-xs text-rose-400">
          {loiBundle.includes('chưa được neo')
            ? 'Chứng nhận này chưa neo nên chưa xuất bundle được. Job neo chạy 02:00 hằng đêm.'
            : loiBundle}
        </p>
      )}
    </div>
  )
}
