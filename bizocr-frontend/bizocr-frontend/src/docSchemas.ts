import type {Fields} from './lib'

// Form CỐ ĐỊNH theo loại chứng từ. Phải khớp với DocumentFieldSchema.java ở backend
// (cả hai được sinh từ cùng một bảng dữ liệu; sửa form thì sửa ở cả hai nơi).
// Loại chứng từ không có trong SCHEMAS (loại tùy biến) thì hiển thị đúng những key có trong dữ liệu.

export type FieldGroup = 'person' | 'company'
export type FieldDef = { key: string; label: string; match: string[]; group?: FieldGroup }

const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, '')

const def = (key: string, label: string, ...aliases: string[]): FieldDef => ({
    key,
    label,
    match: [key, ...aliases].map(norm),
})

const defWithGroup = (key: string, label: string, group: FieldGroup, ...aliases: string[]): FieldDef => ({
    key,
    label,
    group,
    match: [key, ...aliases].map(norm),
})

const SCHEMAS: Record<string, FieldDef[]> = {
    NAMECARD: [
        // Khối cá nhân: contact_name, contact_title, phone (số di động), email (email cá nhân)
        defWithGroup('contact_name', 'Họ và tên', 'person', 'name', 'fullName', 'personInCharge'),
        defWithGroup('contact_title', 'Chức danh', 'person', 'title', 'position', 'jobTitle'),
        defWithGroup('phone', 'Số ĐT cá nhân', 'person', 'personalPhone', 'mobile', 'cell', 'sdtCaNhan', 'sdt'),
        defWithGroup('email', 'Email cá nhân', 'person', 'personalEmail', 'mail'),

        // Khối công ty: company_name, tax_code, company_phone, fax, company_email, address, website, industry
        defWithGroup('company_name', 'Tên công ty', 'company', 'company', 'issuerName', 'organization'),
        defWithGroup('tax_code', 'Mã số thuế', 'company', 'mst', 'maSoThue'),
        defWithGroup('company_phone', 'Số ĐT công ty', 'company', 'companyPhone', 'officePhone', 'companyTel', 'tel', 'telephone', 'sdtCongTy'),
        defWithGroup('fax', 'Fax', 'company', 'faxNumber', 'soFax'),
        defWithGroup('company_email', 'Email công ty', 'company', 'officeEmail', 'corpEmail', 'emailCongTy'),
        defWithGroup('address', 'Địa chỉ', 'company'),
        defWithGroup('website', 'Website', 'company', 'web', 'url'),
        defWithGroup('industry', 'Ngành nghề', 'company', 'nganhNghe'),
    ],
    VAT_INVOICE: [
        def('invoice_symbol', 'Ký hiệu hóa đơn', 'symbol', 'serial', 'invoiceSerial', 'kyHieu'),
        def('invoice_number', 'Số hóa đơn', 'documentNumber', 'number', 'soHoaDon'),
        def('issue_date', 'Ngày lập', 'date', 'invoiceDate', 'ngayLap'),
        def('seller_name', 'Đơn vị bán', 'issuerName', 'seller', 'supplier'),
        def('tax_code', 'MST bên bán', 'sellerTaxCode', 'mst', 'maSoThue'),
        def('seller_address', 'Địa chỉ bên bán', 'address'),
        def('buyer_name', 'Đơn vị mua', 'customerName', 'buyer', 'customer'),
        def('buyer_tax_code', 'MST bên mua', 'customerTaxCode'),
        def('subtotal', 'Tiền hàng (chưa thuế)', 'amountBeforeTax', 'totalBeforeTax', 'tienHang'),
        def('vat_rate', 'Thuế suất', 'taxRate'),
        def('vat_amount', 'Tiền thuế GTGT', 'vat', 'taxAmount', 'tienThue'),
        def('total_amount', 'Tổng thanh toán', 'total', 'grandTotal', 'tongTien'),
    ],
    SERVICE_PAYMENT_VOUCHER: [
        def('document_number', 'Số phiếu / hóa đơn', 'invoiceNumber', 'receiptNumber', 'number'),
        def('issue_date', 'Ngày lập', 'date', 'invoiceDate'),
        def('seller_name', 'Đơn vị cung cấp', 'issuerName', 'merchant', 'storeName', 'seller'),
        def('tax_code', 'Mã số thuế', 'sellerTaxCode', 'mst', 'maSoThue'),
        def('address', 'Địa chỉ', 'sellerAddress'),
        def('customer_name', 'Khách hàng', 'buyerName', 'buyer'),
        def('payment_method', 'Hình thức thanh toán'),
        def('total_amount', 'Tổng thanh toán', 'total', 'grandTotal', 'amount'),
    ],
    ELECTRICITY_BILL: [
        def('invoice_number', 'Số hóa đơn', 'documentNumber', 'number'),
        def('issue_date', 'Ngày lập', 'date', 'invoiceDate'),
        def('seller_name', 'Đơn vị điện lực', 'issuerName', 'seller', 'supplier'),
        def('tax_code', 'Mã số thuế', 'sellerTaxCode', 'mst'),
        def('customer_name', 'Khách hàng', 'buyerName', 'customer'),
        def('customer_code', 'Mã khách hàng', 'maKhachHang', 'customerId'),
        def('address', 'Địa chỉ sử dụng điện', 'customerAddress'),
        def('billing_period', 'Kỳ hóa đơn', 'period'),
        def('consumption', 'Điện tiêu thụ (kWh)', 'consumptionKwh', 'kwh', 'usage'),
        def('total_amount', 'Tổng thanh toán', 'total', 'grandTotal', 'amount'),
    ],
    WATER_BILL: [
        def('invoice_number', 'Số hóa đơn', 'documentNumber', 'number'),
        def('issue_date', 'Ngày lập', 'date', 'invoiceDate'),
        def('seller_name', 'Đơn vị cấp nước', 'issuerName', 'seller', 'supplier'),
        def('tax_code', 'Mã số thuế', 'sellerTaxCode', 'mst'),
        def('customer_name', 'Khách hàng', 'buyerName', 'customer'),
        def('customer_code', 'Mã khách hàng', 'maKhachHang', 'customerId', 'danhBo'),
        def('address', 'Địa chỉ sử dụng nước', 'customerAddress'),
        def('billing_period', 'Kỳ hóa đơn', 'period'),
        def('consumption', 'Nước tiêu thụ (m3)', 'consumptionM3', 'm3', 'usage'),
        def('total_amount', 'Tổng thanh toán', 'total', 'grandTotal', 'amount'),
    ],
    CONTRACT: [
        def('contract_number', 'Số hợp đồng', 'documentNumber', 'number'),
        def('contract_title', 'Tên hợp đồng', 'documentTitle', 'title'),
        def('issue_date', 'Ngày ký', 'signDate', 'date'),
        def('party_a', 'Bên A', 'partyAName'),
        def('party_a_tax_code', 'MST bên A', 'taxCode'),
        def('party_b', 'Bên B', 'partyBName'),
        def('party_b_tax_code', 'MST bên B'),
        def('total_amount', 'Giá trị hợp đồng', 'contractValue', 'value', 'amount', 'total'),
        def('effective_date', 'Ngày hiệu lực', 'startDate'),
        def('expiry_date', 'Ngày hết hạn', 'endDate'),
    ],
    BANK_STATEMENT: [
        def('document_number', 'Số chứng từ', 'number', 'transactionId'),
        def('issue_date', 'Ngày chứng từ', 'date', 'transactionDate'),
        def('bank_name', 'Ngân hàng', 'bank'),
        def('payer_name', 'Người chuyển', 'payer', 'senderName', 'remitter'),
        def('payer_account', 'Số TK người chuyển', 'senderAccount', 'accountNumber'),
        def('beneficiary_name', 'Người thụ hưởng', 'beneficiary', 'receiverName', 'payee'),
        def('beneficiary_account', 'Số TK thụ hưởng', 'receiverAccount'),
        def('beneficiary_bank', 'Ngân hàng thụ hưởng', 'receiverBank'),
        def('total_amount', 'Số tiền', 'amount', 'total'),
        def('content', 'Nội dung', 'note', 'description', 'memo'),
    ],
    ID_CARD: [
        def('id_number', 'Số CCCD/CMND', 'cccd', 'cmnd', 'documentNumber', 'soCanCuoc'),
        def('full_name', 'Họ và tên', 'name', 'fullName'),
        def('date_of_birth', 'Ngày sinh', 'dob', 'birthDate'),
        def('gender', 'Giới tính', 'sex'),
        def('nationality', 'Quốc tịch'),
        def('place_of_origin', 'Quê quán', 'hometown'),
        def('place_of_residence', 'Nơi thường trú', 'residence', 'address'),
        def('issue_date', 'Ngày cấp', 'date'),
        def('expiry_date', 'Có giá trị đến', 'validUntil'),
    ],
    OTHER: [
        def('document_title', 'Tên loại chứng từ'),
        def('document_number', 'Số hiệu chứng từ', 'number'),
        def('issue_date', 'Ngày tháng năm', 'date'),
        def('issuer_name', 'Đơn vị phát hành', 'company', 'companyName'),
        def('tax_code', 'Mã số thuế', 'mst'),
        def('contact_name', 'Người liên quan', 'personInCharge', 'name', 'fullName'),
        def('contact_title', 'Chức danh', 'title', 'position'),
        def('phone', 'Số điện thoại', 'phoneNumber', 'tel'),
        def('email', 'Email'),
        def('address', 'Địa chỉ'),
        def('total_amount', 'Số tiền', 'total', 'amount'),
        def('note', 'Ghi chú', 'content', 'description'),
    ],
}

const TYPE_ALIASES: Record<string, string> = {
    NAME_CARD: 'NAMECARD',
    DANH_THIEP: 'NAMECARD',
    BUSINESS_CARD: 'NAMECARD',
    INVOICE: 'VAT_INVOICE',
    HOA_DON: 'VAT_INVOICE',
    HOA_DON_GTGT: 'VAT_INVOICE',
    RECEIPT: 'SERVICE_PAYMENT_VOUCHER',
    INVOICE_SUPERMARKET: 'SERVICE_PAYMENT_VOUCHER',
    RETAIL_INVOICE: 'SERVICE_PAYMENT_VOUCHER',
    INVOICE_ELECTRICITY: 'ELECTRICITY_BILL',
    INVOICE_WATER: 'WATER_BILL',
    CONTRACT_LABOR: 'CONTRACT',
    IDCARD: 'ID_CARD',
    CCCD: 'ID_CARD',
    CMND: 'ID_CARD',
    UNKNOWN: 'OTHER',
    CHUA_PHAN_LOAI: 'OTHER',
}

export function canonicalType(type?: string | null): string {
    const u = (type ?? '').toUpperCase().trim().replace(/[\s-]+/g, '_')
    if (!u) return 'OTHER'
    if (SCHEMAS[u]) return u
    return TYPE_ALIASES[u] ?? u
}

export const schemaOf = (type?: string | null): FieldDef[] | undefined => SCHEMAS[canonicalType(type)]

// Key nội bộ của pipeline, không phải dữ liệu chứng từ
export const isInternalKey = (k: string) =>
    k.startsWith('_') || k.startsWith('validator_') || norm(k) === 'reviewstatus'

export const labelOf = (type: string | null | undefined, key: string) =>
    schemaOf(type)?.find((d) => d.key === key)?.label ?? key

export const groupOf = (type: string | null | undefined, key: string): FieldGroup | undefined =>
    schemaOf(type)?.find((d) => d.key === key)?.group

const isEmpty = (v: unknown) => v == null || v === ''

/**
 * Tách dữ liệu đã lưu thành:
 *  - shown: các trường HIỂN THỊ trên form (loại có schema -> đúng bộ key cố định, thiếu = '')
 *  - hidden: key nội bộ, không hiện nhưng phải giữ nguyên khi lưu (tránh làm nhiễu nhật ký sửa)
 */
export function splitBySchema(type: string | null | undefined, raw: Fields): { shown: Fields; hidden: Fields } {
    const entries = Object.entries(raw)
    const hidden = Object.fromEntries(entries.filter(([k]) => isInternalKey(k))) as Fields
    const visible = entries.filter(([k]) => !isInternalKey(k))

    const schema = schemaOf(type)
    if (!schema) {
        return {shown: Object.fromEntries(visible) as Fields, hidden}
    }

    const byNorm = new Map<string, unknown>()
    for (const [k, v] of visible) {
        const n = norm(k)
        if (isEmpty(byNorm.get(n))) byNorm.set(n, v)
    }

    const shown = Object.fromEntries(
        schema.map((d) => {
            for (const m of d.match) {
                const v = byNorm.get(m)
                if (!isEmpty(v)) return [d.key, v]
            }
            return [d.key, '']
        }),
    ) as Fields

    return {shown, hidden}
}