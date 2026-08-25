# BizOCR Enterprise - Hệ Thống Phân Loại & Trích Xuất Chứng Từ Động

BizOCR là hệ thống xử lý tài liệu thông minh dựa trên kiến trúc **Microservices** và **Event-driven (Reactive)**. Hệ thống được thiết kế để tự động hóa quy trình số hóa cho ~200 loại chứng từ khác nhau, áp dụng chiến lược *"Phân loại trước, Trích xuất sau"* kết hợp giữa thuật toán Template Matching và Generative AI.

---

## 🏗️ Kiến Trúc Công Nghệ (Tech Stack)

Hệ thống được chia thành 3 node chính giao tiếp qua mạng nội bộ (Docker Network):

1. **Lõi Điều Phối (Core Orchestrator):**
   * **Framework:** Java 21, Spring Boot 3.3.x (WebFlux).
   * **Nhiệm vụ:** Quản lý I/O không đồng bộ (Non-blocking), điều phối luồng tải cao, gọi API ngoại vi và giao tiếp với cơ sở dữ liệu.
   * **Thư viện chính:** Spring WebFlux, Spring Data R2DBC, Spring AI (OpenAI), Resilience4J.

2. **Dịch vụ Xử lý Ảnh (Vision / OCR Engine):**
   * **Framework:** Python 3.10+, FastAPI.
   * **Nhiệm vụ:** Nhận file ảnh và bóc tách tọa độ hộp chữ (Bounding Boxes) + Text thô.
   * **Engine:** RapidOCR, VietOCR, OpenCV.

3. **Cơ Sở Dữ Liệu (Database):**
   * **Engine:** MariaDB 10.11+.
   * **Nhiệm vụ:** Lưu trữ metadata, trạng thái hồ sơ và dữ liệu chứng từ động dưới định dạng `JSON`.

---

## 📂 Cấu Trúc Thư Mục (Java Core)

```text
com.company.bizocr
 ├── config/            # Cấu hình R2DBC, WebClient, Rate Limiter
 ├── controller/        # REST APIs (Endpoints nhận file)
 ├── model/             # Entity ánh xạ DB và DTO (Data Transfer Objects)
 ├── repository/        # R2DBC Repositories tương tác MariaDB
 ├── pipeline/          # Reactive Orchestrator điều phối luồng xử lý chính
 ├── ocr/               # Dịch vụ gọi Python FastAPI
 ├── classify/          # Logic phân loại (Template Matching & AI Fallback)
 └── extract/           # Bóc tách JSON động sử dụng LLM
