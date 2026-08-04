import { authHeader } from './auth'

export type Page<T> = {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/**
 * Gọi endpoint phân trang bằng fetch thay vì client sinh tự động.
 *
 * Lý do: springdoc mô tả tham số Pageable của Spring thành MỘT object query param
 * (`pageable`), nên client sinh ra gửi `?pageable=[object Object]` — sai. Spring lại
 * mong đợi ba tham số phẳng `page`, `size`, `sort`.
 *
 * Đây là hạn chế đã biết của cặp springdoc + Pageable, không phải lỗi của đề tài.
 * Client sinh tự động vẫn dùng tốt cho mọi endpoint không phân trang, nên quyết định
 * số 3 (sinh client từ OpenAPI) vẫn giữ nguyên giá trị.
 */
export async function fetchPage<T>(
  path: string,
  params: { page?: number; size?: number; sort?: string } = {},
): Promise<Page<T>> {
  const qs = new URLSearchParams()
  qs.set('page', String(params.page ?? 0))
  qs.set('size', String(params.size ?? 20))
  if (params.sort) qs.set('sort', params.sort)

  const res = await fetch(`${path}?${qs}`, {
    headers: { ...authHeader() },
  })
  if (!res.ok) {
    throw new Error(await extractMessage(res))
  }
  return res.json()
}

export async function extractMessage(res: Response): Promise<string> {
  try {
    const body = await res.json()
    return body.message ?? `Lỗi ${res.status}`
  } catch {
    return `Lỗi ${res.status}`
  }
}
