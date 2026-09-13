# BizOCR Enterprise - Hệ Thống Phân Loại & Trích Xuất Chứng Từ Động

BizOCR là hệ thống xử lý tài liệu thông minh dựa trên kiến trúc **Microservices** và **Event-driven (Reactive)**. Hệ thống được thiết kế để tự động hóa quy trình số hóa cho ~200 loại chứng từ khác nhau, áp dụng luồng workflow đa tầng:

$$\text{Ảnh gốc} \xrightarrow{\text{OpenCV}} \text{Ảnh sạch} \xrightarrow{\text{PaddleOCR}} \text{OCR thô} \xrightarrow{\text{qwen2.5:3b}} \text{Văn bản chuẩn} \xrightarrow{\text{Java Core}} \begin{cases} \text{Regex / Template Matching} \\ \text{AI Fallback (3.5-instruct / Qwen)} \end{cases}$$

---

## 🏗️ Kiến Trúc Công Nghệ (Tech Stack)

Hệ thống được chia thành các node chính giao tiếp qua mạng nội bộ:

1. **Dịch vụ Xử lý Ảnh & Chuẩn hóa Ngôn ngữ (Python Vision & OCR Engine):**
   * **Framework:** Python 3.10+, FastAPI.
   * **Module Thị giác (OpenCV):** Xoay thẳng ảnh tự động (Auto Deskew), khử bóng (Shadow Removal), tăng tương phản thích ứng (CLAHE), khử nhiễu và làm nét chữ -> tạo **Ảnh sạch**.
   * **Module OCR (PaddleOCR):** Nhận diện hộp chữ (Bounding Boxes) và văn bản tiếng Việt + thuật toán sắp xếp thứ tự đọc tự nhiên (Spatial Reading Order) -> xuất **OCR thô**.
   * **Module Chuẩn hóa (Qwen2.5:3B LLM):** Tích hợp Ollama để phục hồi dấu tiếng Việt, sửa lỗi chính tả OCR, bảo toàn tuyệt đối số liệu hóa đơn -> xuất **Văn bản chuẩn**.
   * **Module ML & Analytics:** TF-IDF Classifier, Self-learning Data Collector, K-Means Clustering và Anomaly Detection (Z-Score).

2. **Lõi Điều Phối (Java Core Orchestrator):**
   * **Framework:** Java 21, Spring Boot 3.3.x (WebFlux).
   * **Nhiệm vụ:** Quản lý I/O không đồng bộ (Non-blocking), điều phối luồng xử lý `PipelineOrchestrator`, liên kết thực thể công ty `CompanyResolverService`.
   * **Phân loại & Bóc tách đa tầng:**
     - Tầng 1: Thuật toán `TemplateMatcher` (Rule/Anchor-based, tốc độ <1ms) & Regex Parser.
     - Tầng 2: Machine Learning Classifier dự đoán nhãn chứng từ.
     - Tầng 3: AI Fallback (`AiClassifierFallback` & `FieldExtractorService` qua Ollama LLM) bóc tách JSON khi gặp tài liệu ngoại lệ (outlier).

3. **Cơ Sở Dữ Liệu (Database):**
   * **Engine:** MariaDB 10.11+ / R2DBC Reactive.
   * **Nhiệm vụ:** Lưu trữ metadata, trạng thái hồ sơ và dữ liệu chứng từ động dưới định dạng `JSON`.

---

## 📂 Cấu Trúc Dự Án

```text
bizocr/
 ├── python_service/      # Python FastAPI Microservice
 │    ├── preprocessing/  # OpenCV Pipeline (Deskew, CLAHE, Denoise)
 │    ├── ocr/            # PaddleOCR Engine & Reading Order Sorting
 │    ├── normalizer/     # Qwen2.5:3B Text Normalizer (Ollama)
 │    ├── ml/             # TF-IDF Classifier, Clustering & Anomaly
 │    └── api/            # FastAPI Endpoints (/ocr, /ml)
 └── bizocr/              # Java Core (Spring Boot WebFlux)
      ├── config/         # Cấu hình R2DBC, WebClient, Rate Limiter
      ├── controller/     # REST APIs (Endpoints nhận file)
      ├── model/          # Entity ánh xạ DB và DTO
      ├── repository/     # R2DBC Repositories tương tác MariaDB
      ├── pipeline/       # Reactive Orchestrator điều phối luồng
      ├── ocr/            # Dịch vụ gọi Python OCR API
      ├── classify/       # Template Matching, ML & AI Fallback
      └── extract/        # Bóc tách Regex & Ollama LLM
```
