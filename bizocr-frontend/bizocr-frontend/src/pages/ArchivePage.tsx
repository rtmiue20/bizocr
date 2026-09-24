import {useEffect, useState} from 'react'
import {api} from '../api'
import type {Doc, DocPage} from '../types'

export default function ArchivePage() {
    const [data, setData] = useState<DocPage | null>(null)
    const [page, setPage] = useState(0)
    const [loading, setLoading] = useState(false)

    useEffect(() => {
        let active = true
        setLoading(true)
        api.list({page, size: 10})
            .then((res) => {
                if (active) setData(res)
            })
            .catch((err) => alert(err.message))
            .finally(() => {
                if (active) setLoading(false)
            })
        return () => {
            active = false
        }
    }, [page])

    return (
        <div className="flex flex-col gap-4">
            <div className="flex items-center justify-between">
                <h1 className="font-headline-md text-headline-md text-primary">Lưu trữ & Tra cứu</h1>
            </div>

            <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
                {loading ? (
                    <p className="text-on-surface-variant">Đang tải dữ liệu...</p>
                ) : (
                    <table className="w-full text-left font-body-md">
                        <thead>
                        <tr className="border-b border-outline-variant text-on-surface-variant">
                            <th className="pb-2">ID</th>
                            <th className="pb-2">Tên file</th>
                            <th className="pb-2">Loại tài liệu</th>
                            <th className="pb-2">Trạng thái</th>
                        </tr>
                        </thead>
                        <tbody>
                        {data?.items.map((doc: Doc) => (
                            <tr key={doc.id} className="border-b border-outline-variant/50 last:border-0 hover:bg-surface-container-low">
                                <td className="py-3">{doc.id}</td>
                                <td className="py-3">{doc.fileName}</td>
                                <td className="py-3">{doc.documentType || 'Chưa phân loại'}</td>
                                <td className="py-3">
                    <span className="rounded-full bg-primary-container px-2 py-1 text-xs text-on-primary-container">
                      {doc.status}
                    </span>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                )}
            </div>

            {data && (
                <div className="flex items-center gap-4">
                    <button
                        disabled={data.page === 0} // Thay data.first bằng data.page === 0
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        className="rounded-lg bg-surface-container-high px-4 py-2 disabled:opacity-50"
                    >
                        Trang trước
                    </button>

                    {/* Đổi data.number thành data.page */}
                    <span>Trang {data.page + 1} / {data.totalPages}</span>

                    <button
                        disabled={data.page >= data.totalPages - 1} // Thay data.last bằng logic tính toán
                        onClick={() => setPage((p) => p + 1)}
                        className="rounded-lg bg-surface-container-high px-4 py-2 disabled:opacity-50"
                    >
                        Trang sau
                    </button>
                </div>
            )}
        </div>
    )
}