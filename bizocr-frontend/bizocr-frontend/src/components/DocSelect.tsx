import { useEffect, useState } from 'react'
import { api } from '../api'
import type { Doc } from '../types'

/** Chọn chứng từ trong 30 bản ghi mới nhất; nếu chưa chọn thì tự lấy cái đầu tiên. */
export default function DocSelect({ value, onChange }: { value: number | null; onChange: (id: number) => void }) {
  const [items, setItems] = useState<Doc[]>([])

  useEffect(() => {
    api.list({ page: 0, size: 30 })
      .then((p) => {
        setItems(p.items)
        if (value == null && p.items[0]) onChange(p.items[0].id)
      })
      .catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <select
      value={value ?? ''}
      onChange={(e) => onChange(Number(e.target.value))}
      className="max-w-xs rounded-xl bg-surface-container px-space-md py-2 font-label-md text-label-md text-on-surface focus:outline-none focus:ring-1 focus:ring-primary-container"
    >
      {value != null && !items.some((d) => d.id === value) && <option value={value}>#{value}</option>}
      {items.map((d) => <option key={d.id} value={d.id}>#{d.id} · {d.fileName}</option>)}
    </select>
  )
}
