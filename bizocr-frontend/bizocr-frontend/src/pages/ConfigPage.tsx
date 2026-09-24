import React from 'react';

export default function ConfigPage() {
    return (
        <div className="flex flex-col w-full">
            <div className="relative w-full overflow-hidden px-margin py-space-lg flex flex-col gap-space-lg">
                {/* Background Ambient */}
                <div
                    className="pointer-events-none absolute -top-24 left-1/4 w-96 h-96 rounded-full bg-primary-container/5 blur-3xl"></div>
                <div
                    className="pointer-events-none absolute top-10 right-1/4 w-80 h-80 rounded-full bg-secondary/5 blur-3xl"></div>

                {/* Header Section */}
                <div className="relative flex flex-col lg:flex-row lg:items-center justify-between gap-space-md">
                    <div className="flex flex-col gap-space-xs max-w-3xl">
                        <div className="flex items-center gap-space-xs">
              <span
                  className="px-2 py-0.5 rounded-full bg-primary-container/15 text-primary-container font-label-sm text-label-sm tracking-wider uppercase">
                Hệ thống Quản trị Tri thức Thuế & Mẫu biểu
              </span>
                            <span className="text-outline text-body-sm">•</span>
                            <span className="font-body-sm text-body-sm text-secondary flex items-center gap-1">
                <span className="inline-block w-1.5 h-1.5 rounded-full bg-secondary animate-pulse"></span>
                Đồng bộ Thông tư 78/2021/TT-BTC
              </span>
                        </div>
                        <h1 className="font-headline-lg text-headline-lg text-primary tracking-tight">
                            Cấu hình Mẫu biểu & Định mức Thuế suất
                        </h1>
                        <p className="font-body-md text-body-md text-on-surface-variant leading-relaxed">
                            Quản lý danh mục template nhận diện OCR, quy tắc bóc tách trường thông tin và định mức thuế
                            suất GTGT áp dụng theo Nghị định hiện hành.
                        </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-space-sm pt-2 lg:pt-0">
                        <button
                            className="group flex items-center gap-space-xs px-space-md py-2.5 rounded-xl bg-surface-container-high hover:bg-surface-bright text-on-surface transition-colors shadow-sm"
                            type="button">
                            <span
                                className="material-symbols-outlined text-[18px] text-on-surface-variant group-hover:rotate-180 transition-transform duration-500">cached</span>
                            <span className="font-label-md text-label-md">Nạp lại cache</span>
                        </button>
                        <button
                            className="flex items-center gap-space-xs px-space-md py-2.5 rounded-xl bg-surface-container-high hover:bg-surface-bright text-secondary font-label-md text-label-md transition-colors shadow-sm"
                            type="button">
                            <span className="material-symbols-outlined text-[18px]">tune</span>
                            <span>Cập nhật định mức thuế</span>
                        </button>
                        <button
                            className="flex items-center gap-space-xs px-space-lg py-2.5 rounded-xl bg-gradient-to-r from-primary-container to-surface-tint text-on-primary-container font-label-md text-label-md font-bold shadow-[0_0_20px_rgba(0,242,254,0.3)] hover:brightness-110 active:scale-95 transition-all"
                            type="button">
                            <span className="material-symbols-outlined text-[18px] font-bold">add_circle</span>
                            <span>Thêm mẫu biểu mới</span>
                        </button>
                    </div>
                </div>

                {/* Live Compliance Alert Banner */}
                <div
                    className="relative overflow-hidden rounded-xl bg-surface-container-low/90 backdrop-blur-xl p-space-md shadow-md flex items-start sm:items-center gap-space-md">
                    <div
                        className="w-1.5 self-stretch rounded-full bg-secondary-fixed shadow-[0_0_12px_rgba(78,222,163,0.8)]"></div>
                    <div className="w-9 h-9 rounded-xl bg-secondary/15 flex items-center justify-center shrink-0">
                        <span className="material-symbols-outlined text-secondary text-[22px]">policy</span>
                    </div>
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-space-xs flex-1">
                        <div>
                            <span
                                className="font-label-md text-label-md text-secondary font-bold mr-2 uppercase tracking-wide">[Chính sách áp dụng 2025]</span>
                            <span className="font-body-md text-body-md text-on-surface">
                Cập nhật chính sách thuế mới nhất: Giảm 2% thuế suất GTGT theo Nghị quyết Quốc hội tiếp tục áp dụng cho nhóm hàng hóa dịch vụ đủ điều kiện đến hết 31/12/2025.
              </span>
                        </div>
                        <a className="inline-flex items-center gap-1 font-label-md text-label-md text-primary-container hover:underline shrink-0 pt-1 sm:pt-0"
                           href="#thue-suat">
                            <span>Xem chi tiết định mức</span>
                            <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                        </a>
                    </div>
                </div>

                {/* 3 Stat Cards Bento Strip */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-gutter">
                    <div
                        className="relative overflow-hidden rounded-xl bg-surface-container/70 backdrop-blur-xl p-space-lg shadow-md hover:shadow-xl transition-all flex flex-col justify-between">
                        <div className="flex items-start justify-between">
                            <div className="flex flex-col gap-1">
                                <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline">Kho mẫu lưu trữ</span>
                                <span className="font-title-md text-title-md text-on-surface font-semibold">Mẫu biểu đang kích hoạt</span>
                            </div>
                            <div
                                className="w-10 h-10 rounded-xl bg-primary-container/15 flex items-center justify-center shadow-[0_0_12px_rgba(0,242,254,0.25)]">
                                <span
                                    className="material-symbols-outlined text-primary-container text-[22px]">file_copy</span>
                            </div>
                        </div>
                        <div className="mt-space-md flex items-baseline gap-space-sm">
                            <span
                                className="font-headline-xl text-headline-xl text-primary font-bold tracking-tight">38</span>
                            <span className="font-title-md text-title-md text-primary-container font-medium">Mẫu chuẩn hóa</span>
                        </div>
                        <p className="mt-space-xs font-body-sm text-body-sm text-on-surface-variant truncate">
                            HĐĐT Thông tư 78/NĐ 123, Hợp đồng kinh tế, Phiếu chi EVN...
                        </p>
                        <div
                            className="mt-space-sm pt-space-xs flex items-center gap-space-xs text-secondary font-label-sm text-label-sm">
                            <span className="material-symbols-outlined text-[14px]">check_circle</span>
                            <span>100% Khớp phôi hóa đơn TCT</span>
                        </div>
                    </div>

                    <div
                        className="relative overflow-hidden rounded-xl bg-surface-container/70 backdrop-blur-xl p-space-lg shadow-md hover:shadow-xl transition-all flex flex-col justify-between">
                        <div className="flex items-start justify-between">
                            <div className="flex flex-col gap-1">
                                <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline">Trí tuệ nhân tạo OCR</span>
                                <span className="font-title-md text-title-md text-on-surface font-semibold">Quy tắc bóc tách AI</span>
                            </div>
                            <div
                                className="w-10 h-10 rounded-xl bg-secondary/15 flex items-center justify-center shadow-[0_0_12px_rgba(78,222,163,0.25)]">
                                <span className="material-symbols-outlined text-secondary text-[22px]">psychology</span>
                            </div>
                        </div>
                        <div className="mt-space-md flex items-baseline gap-space-sm">
                            <span
                                className="font-headline-xl text-headline-xl text-secondary font-bold tracking-tight">142</span>
                            <span className="font-title-md text-title-md text-secondary-fixed-dim font-medium">Field Rules</span>
                        </div>
                        <p className="mt-space-xs font-body-sm text-body-sm text-on-surface-variant">
                            Bảo mật tuyệt đối • Không ảo giác mô hình
                        </p>
                        <div
                            className="mt-space-sm pt-space-xs flex items-center gap-space-xs text-primary-container font-label-sm text-label-sm">
                            <span className="material-symbols-outlined text-[14px]">verified_user</span>
                            <span>Auto-Validation Level 3</span>
                        </div>
                    </div>

                    <div
                        className="relative overflow-hidden rounded-xl bg-surface-container/70 backdrop-blur-xl p-space-lg shadow-md hover:shadow-xl transition-all flex flex-col justify-between">
                        <div className="flex items-start justify-between">
                            <div className="flex flex-col gap-1">
                                <span className="font-label-sm text-label-sm uppercase tracking-wider text-outline">Luật thuế & Kế toán</span>
                                <span className="font-title-md text-title-md text-on-surface font-semibold">Định mức thuế suất chuẩn</span>
                            </div>
                            <div
                                className="w-10 h-10 rounded-xl bg-tertiary-container/20 flex items-center justify-center shadow-[0_0_12px_rgba(255,185,95,0.2)]">
                                <span
                                    className="material-symbols-outlined text-tertiary-fixed-dim text-[22px]">percent</span>
                            </div>
                        </div>
                        <div className="mt-space-md flex items-baseline gap-space-sm">
                            <span
                                className="font-headline-xl text-headline-xl text-tertiary-fixed font-bold tracking-tight">4</span>
                            <span className="font-title-md text-title-md text-tertiary-fixed-dim font-medium">Nhóm định mức</span>
                        </div>
                        <div className="mt-space-xs flex items-center gap-1.5 flex-wrap">
                            <span
                                className="px-2 py-0.5 rounded-md bg-surface-container-high text-on-surface font-label-sm text-label-sm">0%</span>
                            <span
                                className="px-2 py-0.5 rounded-md bg-surface-container-high text-on-surface font-label-sm text-label-sm">5%</span>
                            <span
                                className="px-2 py-0.5 rounded-md bg-secondary/20 text-secondary font-label-sm text-label-sm font-bold">8% (Giảm 2%)</span>
                            <span
                                className="px-2 py-0.5 rounded-md bg-surface-container-high text-on-surface font-label-sm text-label-sm">10%</span>
                        </div>
                        <div
                            className="mt-space-sm pt-space-xs flex items-center gap-space-xs text-outline font-label-sm text-label-sm">
                            <span className="material-symbols-outlined text-[14px]">account_balance</span>
                            <span>Khớp tài khoản Thông tư 200/133</span>
                        </div>
                    </div>
                </div>

                {/* Main Workspace */}
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-gutter items-start">

                    {/* LEFT COLUMN */}
                    <div className="lg:col-span-7 flex flex-col gap-space-md">
                        <div
                            className="rounded-xl bg-surface-container/70 backdrop-blur-xl p-space-md shadow-md flex flex-col gap-space-sm">
                            <div className="flex items-center justify-between flex-wrap gap-space-sm">
                                <div className="flex items-center gap-space-xs">
                                    <span
                                        className="material-symbols-outlined text-primary-container text-[20px]">dataset</span>
                                    <h2 className="font-title-lg text-title-lg text-on-surface">Danh sách Mẫu biểu văn
                                        bản & Quy tắc OCR</h2>
                                </div>
                                <span className="font-label-sm text-label-sm text-outline">Đang hiển thị 3 mẫu biểu trọng yếu</span>
                            </div>
                            <div className="flex flex-col sm:flex-row items-center gap-space-xs pt-1">
                                <div className="relative flex-1 w-full">
                                    <span
                                        className="material-symbols-outlined absolute left-3 top-2.5 text-outline text-[18px]">search</span>
                                    <input
                                        className="w-full pl-9 pr-space-md py-2 rounded-xl bg-surface-container-low font-body-sm text-body-sm text-on-surface placeholder:text-outline focus:outline-none focus:bg-surface-container-highest transition-colors"
                                        placeholder="Tìm theo tên mẫu biểu, ký hiệu mẫu, động cơ OCR..." type="text"/>
                                </div>
                                <div className="flex items-center gap-space-xs w-full sm:w-auto">
                                    <select
                                        className="px-space-md py-2 rounded-xl bg-surface-container-low font-label-md text-label-md text-on-surface-variant focus:outline-none">
                                        <option>Tất cả loại văn bản</option>
                                        <option>Hóa đơn GTGT</option>
                                        <option>Hợp đồng / Nghiệm thu</option>
                                    </select>
                                    <button
                                        className="p-2 rounded-xl bg-surface-container-low text-on-surface-variant hover:text-on-surface hover:bg-surface-container-highest transition-colors"
                                        title="Bộ lọc nâng cao">
                                        <span className="material-symbols-outlined text-[20px]">filter_list</span>
                                    </button>
                                </div>
                            </div>
                        </div>

                        {/* Template Card 1 */}
                        <div
                            className="rounded-xl bg-surface-container/80 backdrop-blur-xl p-space-lg shadow-md hover:shadow-xl transition-all flex flex-col gap-space-md group">
                            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-space-sm">
                                <div className="flex items-start gap-space-md">
                                    <div
                                        className="w-12 h-12 rounded-xl bg-primary-container/15 flex items-center justify-center shrink-0">
                                        <span
                                            className="material-symbols-outlined text-primary-container text-[26px]">receipt_long</span>
                                    </div>
                                    <div className="flex flex-col">
                                        <div className="flex items-center gap-space-xs flex-wrap">
                                            <h3 className="font-title-md text-title-md text-primary font-bold">Mẫu Hóa
                                                đơn GTGT Điện tử</h3>
                                            <span
                                                className="px-2 py-0.5 rounded-full bg-secondary/15 text-secondary font-label-sm text-label-sm font-semibold">Chuẩn TT78/2021/NĐ123</span>
                                        </div>
                                        <div
                                            className="mt-1 flex items-center gap-space-sm flex-wrap font-label-sm text-label-sm text-on-surface-variant">
                      <span className="flex items-center gap-1">
                        <span className="text-outline">Ký hiệu mẫu:</span>
                        <code className="px-1.5 py-0.5 rounded bg-surface-container-highest text-primary">1C25TKK, 1C24TYY</code>
                      </span>
                                            <span>•</span>
                                            <span className="text-secondary font-medium">Bản cập nhật v4.2</span>
                                        </div>
                                    </div>
                                </div>
                                <div className="flex items-center gap-space-sm self-end sm:self-start">
                  <span
                      className="px-2.5 py-1 rounded-full bg-secondary/15 text-secondary font-label-sm text-label-sm font-bold flex items-center gap-1.5 shadow-[0_0_8px_rgba(78,222,163,0.2)]">
                    <span className="w-2 h-2 rounded-full bg-secondary animate-pulse"></span>
                    Đang áp dụng
                  </span>
                                    <label className="relative inline-flex items-center cursor-pointer">
                                        <input defaultChecked className="sr-only peer" type="checkbox"/>
                                        <div
                                            className="w-11 h-6 bg-surface-container-highest peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-on-primary-container after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-primary-container shadow-inner"></div>
                                    </label>
                                </div>
                            </div>
                            <div
                                className="grid grid-cols-1 sm:grid-cols-2 gap-space-sm p-space-md rounded-xl bg-surface-container-low/70">
                                <div className="flex items-center gap-space-sm">
                                    <div
                                        className="w-8 h-8 rounded-lg bg-surface-container-high flex items-center justify-center shrink-0">
                                        <span
                                            className="material-symbols-outlined text-outline text-[18px]">memory</span>
                                    </div>
                                    <div className="flex flex-col">
                                        <span className="font-label-sm text-label-sm text-outline">Động cơ OCR chuyên biệt</span>
                                        <span
                                            className="font-body-sm text-body-sm text-on-surface font-semibold truncate">Multimodal LayoutLMv3</span>
                                    </div>
                                </div>
                                <div className="flex items-center gap-space-sm">
                                    <div
                                        className="w-8 h-8 rounded-lg bg-surface-container-high flex items-center justify-center shrink-0">
                                        <span
                                            className="material-symbols-outlined text-secondary text-[18px]">speed</span>
                                    </div>
                                    <div className="flex flex-col">
                                        <span className="font-label-sm text-label-sm text-outline">Tỷ lệ nhận diện tự động</span>
                                        <div className="flex items-center gap-2">
                                            <span
                                                className="font-body-md text-body-md text-secondary font-bold">99.4%</span>
                                            <div
                                                className="w-24 h-1.5 rounded-full bg-surface-container-highest overflow-hidden">
                                                <div className="h-full bg-secondary rounded-full"
                                                     style={{width: '99.4%'}}></div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        {/* Tương tự bạn có thể paste tiếp 2 Template Card còn lại (Hợp đồng, Điện nước) từ file HTML */}
                    </div>

                    {/* RIGHT COLUMN */}
                    <div className="lg:col-span-5 flex flex-col gap-space-md" id="thue-suat">
                        <div
                            className="rounded-xl bg-surface-container/80 backdrop-blur-xl p-space-lg shadow-md flex flex-col gap-space-md">
                            <div className="flex items-center justify-between border-b-0 pb-1">
                                <div className="flex items-center gap-space-xs">
                                    <span
                                        className="material-symbols-outlined text-secondary text-[22px]">account_balance_wallet</span>
                                    <h2 className="font-title-md text-title-md text-on-surface font-bold">Định mức Thuế
                                        GTGT & Hạch toán</h2>
                                </div>
                            </div>
                            <p className="font-body-sm text-body-sm text-on-surface-variant">Quy định ánh xạ tài khoản
                                ghi nợ/có và kiểm tra hợp lệ khi AI tự động định khoản.</p>

                            <div className="flex flex-col gap-space-sm">
                                <div
                                    className="p-space-md rounded-xl bg-secondary/5 shadow-[0_0_12px_rgba(78,222,163,0.08)] flex flex-col gap-space-xs">
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-2">
                                            <span
                                                className="w-8 h-8 rounded-lg bg-secondary/20 flex items-center justify-center font-headline-md text-title-md text-secondary font-bold">8%</span>
                                            <div>
                                                <div className="flex items-center gap-1.5">
                                                    <span
                                                        className="font-label-md text-label-md text-secondary font-bold">Chính sách ưu đãi giảm 2%</span>
                                                </div>
                                                <span
                                                    className="block font-body-sm text-[11px] text-secondary-fixed-dim">Gia hạn áp dụng cả năm 2025</span>
                                            </div>
                                        </div>
                                        <div className="text-right">
                                            <code
                                                className="px-2 py-1 rounded-md bg-secondary/20 text-secondary font-label-sm text-label-sm font-bold">TK
                                                1331-8%</code>
                                        </div>
                                    </div>
                                </div>
                                {/* Phần trăm thuế suất khác */}
                            </div>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    );
}