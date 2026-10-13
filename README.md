# BizOCR Enterprise - Hệ Thống OCR & Bóc Tách Dữ Liệu Doanh Nghiệp Tự Động

**BizOCR** là giải pháp phần mềm thông minh toàn diện giúp số hóa, phân loại và trích xuất dữ liệu tự động từ tài liệu, hóa đơn, chứng từ kế toán, danh thiếp (namecard) và văn bản doanh nghiệp Việt Nam.

Hệ thống kết hợp giữa công nghệ nhận diện ký tự quang học (OCR), mô hình ngôn ngữ lớn (LLM - Ollama), hàng đợi xử lý phản ứng bất đồng bộ (Reactive Queue) và kiến trúc giao diện tương tác trực quan thời gian thực.

---

## 📌 Mục Lục
1. [Giới Thiệu Dự Án](#-giới-thiệu-dự-án)
2. [Tính Năng Chính](#-tính-năng-chính)
3. [Kiến Trúc & Luồng Xử Lý](#-kiến-trúc--luồng-xử-lý)
4. [Công Nghệ Áp Dụng (Tech Stack)](#-công-nghệ-áp-dụng-tech-stack)
5. [Cấu Trúc Thư Mục Dự Án](#-cấu-trúc-thư-mục-dự-án)
6. [Yêu Cầu Môi Trường (Prerequisites)](#-yêu-cầu-môi-trường-prerequisites)
7. [Hướng Dẫn Cài Đặt & Khởi Chạy](#-hướng-dẫn-cài-đặt--khởi-chạy)
8. [Hướng Dẫn Sử Dụng & API Endpoints](#-hướng-dẫn-sử-dụng--api-endpoints)

---

## 📖 Giới Thiệu Dự Án

Trong quy trình vận hành doanh nghiệp, việc nhập liệu thủ công từ hóa đơn, hợp đồng, chứng từ và danh thiếp tốn nhiều thời gian và dễ sai sót. **BizOCR** được xây dựng nhằm giải quyết bài toán này với các mục tiêu:
* **Tự động nhận diện:** Đọc văn bản tiếng Việt chính xác cao từ hình ảnh, file scan hoặc camera.
* **Tự động phân loại:** Nhận biết loại tài liệu (Hóa đơn GTGT, Giấy nộp tiền, Bảng kê, Danh thiếp, Hợp đồng,...).
* **Bóc tách thông tin có cấu trúc:** Trích xuất các trường thông tin cốt lõi (Mã số thuế, Tên công ty, Ngày lập, Tổng tiền, Số điện thoại, Email, Địa chỉ, Danh sách hàng hóa,...) xuất ra định dạng JSON chuẩn.
* **Xác thực & Tra cứu thông minh:** Đối soát dữ liệu bóc tách, tra cứu thông tin doanh nghiệp qua mã số thuế, kiểm tra độ tin cậy và tự động sửa lỗi.

---

## ✨ Tính Năng Chính

* **Pipeline Bóc tách Đa Tầng:**
  1. *Tầng Rule/Regex:* Bóc tách tức thì (<1ms) cho các mẫu định dạng chuẩn (Email, Phone, MST, Date,...).
  2. *Tầng Template Matching:* So khớp bố cục tài liệu theo mẫu có sẵn.
  3. *Tầng AI LLM (Ollama / Qwen2.5):* Phân tích ngữ cảnh, xử lý nhiễu OCR, phục hồi dấu và trích xuất chuẩn schema JSON khi tài liệu phức tạp hoặc không theo mẫu cố định.
* **Hàng Đợi Xử Lý Bất Đồng Bộ (Reactive Redis Queue):** Đảm bảo xử lý hàng loạt tài liệu lớn mà không nghẽn hệ thống.
* **Quản Lý & Số Hóa Danh Thiếp (Namecard & Contact Hub):** Tự động bóc tách danh thiếp, phân luồng email/chức danh, lưu trữ liên hệ và quản lý thẻ thông minh.
* **Tra Cứu Mã Số Thuế & Doanh Nghiệp:** Tích hợp tra cứu thông tin đăng ký doanh nghiệp trực tuyến để làm giàu dữ liệu (enrichment).
* **Chấm Điểm & Đánh Giá Chất Lượng:** Tự động tính toán điểm tin cậy (Confidence Score) cho từng trường dữ liệu bóc tách.
* **Xuất Dữ Liệu Báo Cáo:** Hỗ trợ xuất dữ liệu ra định dạng Excel (`.xlsx`) hoặc JSON.
* **Giao Diện Trực Quan (Web App):** Kéo thả tải ảnh, chụp trực tiếp từ camera, chỉnh sửa trực tiếp dữ liệu sau bóc tách, xem trước ảnh kèm bounding box/vùng tương tác.

---

## 🔄 Kiến Trúc & Luồng Xử Lý

```
[Ảnh / Tài liệu tải lên]
         │
         ▼
[Python OCR Engine / FastAPI] ──► (Tiền xử lý ảnh OpenCV + PaddleOCR nhận diện chữ)
         │
         ▼ (OCR Raw Text)
[Java Core Orchestrator (Spring Boot WebFlux)]
         │
         ├──► [Tầng 1: Regex & Template Extractor] (Xử lý siêu tốc)
         │
         ├──► [Tầng 2: Redis Reactive Queue] ──► [Ollama LLM (qwen2.5:3b-instruct)]
         │                                      (Trích xuất JSON ngữ cảnh & làm sạch)
         ▼
[Validator & Scoring Service] (Kiểm tra schema & tính độ tin cậy)
         │
         ├──► Tra cứu doanh nghiệp (Company Finder / Tax Search)
         │
         ▼
[Lưu trữ MariaDB (R2DBC) & Trả kết quả về Frontend React UI]
```

---

## 🛠️ Công Nghệ Áp Dụng (Tech Stack)

### 1. Backend Core (Java Microservice)
* **Java Version:** Java 21 (LTS)
* **Framework:** Spring Boot 3.3.x / 4.x (Spring WebFlux - Reactive non-blocking)
* **AI Integration:** Spring AI (Ollama Client - Model `qwen2.5:3b-instruct`)
* **Database & ORM:** MariaDB 10.11+ kết hợp Spring Data R2DBC (Reactive Database Connectivity)
* **Cache & Message Queue:** Redis Reactive (`spring-boot-starter-data-redis-reactive`)
* **Resilience:** Spring Cloud Circuit Breaker (Resilience4j Reactor)
* **Báo cáo & Tiện ích:** Apache POI (xuất Excel), Jackson, Lombok, Apache Commons Text

### 2. OCR & Vision Engine (Python Service)
* **Ngôn ngữ:** Python 3.10+
* **Framework:** FastAPI / Uvicorn
* **Computer Vision:** OpenCV (Deskew, CLAHE, khử nhiễu, cân bằng sáng)
* **OCR Model:** PaddleOCR (nhận diện tiếng Việt và tọa độ không gian)

### 3. Frontend Web Application
* **Framework:** React 18, TypeScript
* **Build Tool:** Vite
* **Styling:** Tailwind CSS, PostCSS, Autoprefixer
* **UI/UX:** Tương tác ảnh thời gian thực, quản lý trạng thái luồng xử lý (Process Image Flow, Dashboard, Contacts)

---

## 📂 Cấu Trúc Thư Mục Dự Án

```text
bizocr/
├── bizocr/                                   # BACKEND: Java Spring Boot Reactive Service
│   ├── pom.xml                               # File cấu hình Maven & Dependencies backend
│   ├── uploads/                              # Thư mục lưu trữ file/ảnh chứng từ tải lên
│   └── src/
│       ├── main/
│       │   ├── java/com/company/bizocr/
│       │   │   ├── BizocrApplication.java    # Main entry point của ứng dụng Spring Boot
│       │   │   ├── classify/                 # Module phân loại chứng từ (Template / ML / AI)
│       │   │   ├── client/                   # WebClient gọi Python OCR & Search Service
│       │   │   ├── company/                  # Tra cứu & liên kết dữ liệu thực thể công ty
│       │   │   ├── config/                   # Cấu hình R2DBC, Redis, Security, WebFlux
│       │   │   ├── controller/               # REST API Controllers (Admin, Document, Card,...)
│       │   │   ├── extract/                  # Engine bóc tách (Regex, Schemas, Sanitizers)
│       │   │   ├── model/                    # Entity R2DBC, DTOs, Enums, Response/Request models
│       │   │   ├── ocr/                      # Service đóng gói kết quả OCR
│       │   │   ├── pipeline/                 # Pipeline Orchestrator điều phối luồng xử lý
│       │   │   ├── repository/               # Reactive Repositories giao tiếp MariaDB
│       │   │   ├── score/                    # Thuật toán tính điểm tin cậy (Confidence Score)
│       │   │   ├── security/                 # Bảo mật, Token Filter & Authorization
│       │   │   ├── service/                  # Core Business Services (Ollama Queue, OCR Consumer,...)
│       │   │   ├── statemachine/             # Quản lý trạng thái vòng đời chứng từ
│       │   │   ├── storage/                  # Lưu trữ và phục vụ file ảnh
│       │   │   └── validator/                # Kiểm định tính toàn vẹn của dữ liệu sau bóc tách
│       │   └── resources/
│       │       ├── application.yaml          # Cấu hình chính (Port, DB, Redis, Ollama, Service URLs)
│       │       ├── schema.sql                # DDL khởi tạo bảng cơ sở dữ liệu MariaDB
│       │       └── static/                   # Static resources / trang điều khiển dựng sẵn
│       └── test/                             # Unit tests & Integration tests
│
├── bizocr-frontend/                          # FRONTEND: Ứng dụng React + TypeScript
│   └── bizocr-frontend/
│       ├── index.html                        # HTML template chính
│       ├── package.json                      # Cấu hình npm packages và scripts
│       ├── tsconfig.json                     # Cấu hình TypeScript
│       ├── tailwind.config.js                # Cấu hình Tailwind CSS
│       ├── vite.config.ts                    # Cấu hình Vite bundler & dev server
│       └── src/
│           ├── main.tsx                      # Entry point React app
│           ├── App.tsx                       # Component chính điều hướng giao diện
│           ├── api.ts                        # Axios/Fetch API client kết nối Backend
│           └── book/                         # Các màn hình nghiệp vụ:
│               ├── ProcessImageFlow.tsx      # Luồng tải ảnh, OCR, xác nhận bóc tách
│               ├── DashboardView.tsx         # Bảng điều khiển thống kê tổng quan
│               ├── CardsManagement.tsx       # Quản lý danh thiếp / danh bạ liên hệ
│               └── ...                       # Các component giao diện phụ trợ khác
│
└── README.md                                 # Tài liệu hướng dẫn dự án (File này)
```

---

## ⚙️ Yêu Cầu Môi Trường (Prerequisites)

Trước khi chạy dự án, cần cài đặt sẵn các thành phần sau:
* **Java:** JDK 21 trở lên (Khuyến nghị Eclipse Temurin hoặc Oracle JDK 21).
* **Node.js:** Phiên bản 18.x hoặc 20.x LTS & npm.
* **MariaDB / MySQL:** Phiên bản 10.11+ (Cần tạo sẵn database `bizocr`).
* **Redis Server:** Phiên bản 6.x / 7.x (Chạy cổng mặc định `6379`).
* **Ollama:** Cài đặt từ [ollama.com](https://ollama.com/) và tải model:
  ```bash
  ollama pull qwen2.5:3b-instruct
  ```
* **Python Service:** Dịch vụ Python OCR (FastAPI) chạy tại cổng `8000` (nếu sử dụng module OCR nâng cao).

---

## 🚀 Hướng Dẫn Cài Đặt & Khởi Chạy

### 1. Chuẩn Bị Cơ Sở Dữ Liệu & Dịch Vụ Nền
1. Khởi động **MariaDB** và tạo cơ sở dữ liệu:
   ```sql
   CREATE DATABASE bizocr CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Khởi động **Redis Server** (Port `6379`).
3. Khởi động **Ollama** (Port `11434`):
   ```bash
   ollama serve
   ```

---

### 2. Cấu Hình & Chạy Backend (Spring Boot)
1. Di chuyển vào thư mục backend:
   ```bash
   cd bizocr
   ```
2. Cấu hình biến môi trường hoặc chỉnh sửa file `src/main/resources/application.yaml` (hoặc tạo `application-secret.yml`):
   * `DB_HOST`: Địa chỉ MariaDB (mặc định: `localhost`)
   * `DB_PORT`: Cổng MariaDB (mặc định: `3306`)
   * `DB_NAME`: Tên DB (mặc định: `bizocr`)
   * `DB_USERNAME`: Tên đăng nhập DB (mặc định: `root`)
   * `DB_PASSWORD`: Mật khẩu DB
   * `REDIS_HOST`: Địa chỉ Redis (mặc định: `localhost`)
   * `OLLAMA_BASE_URL`: URL Ollama (mặc định: `http://localhost:11434`)
   * `PYTHON_SERVICE_URL`: URL Python OCR (mặc định: `http://localhost:8000`)

3. Biên dịch và khởi chạy ứng dụng:
   ```bash
   # Sử dụng Maven Wrapper hoặc Maven đã cài đặt
   mvn clean spring-boot:run
   ```
   Ứng dụng Backend sẽ khởi chạy tại cổng mặc định `8080`.

---

### 3. Cài Đặt & Khởi Chạy Frontend (React UI)
1. Di chuyển vào thư mục frontend:
   ```bash
   cd bizocr-frontend/bizocr-frontend
   ```
2. Cài đặt các thư viện phụ thuộc:
   ```bash
   npm install
   ```
3. Chạy môi trường phát triển (Dev mode):
   ```bash
   npm run dev
   ```
   Giao diện người dùng sẽ chạy tại: `http://localhost:5173` (hoặc cổng được Vite thông báo).

---

## 📡 Hướng Dẫn Sử Dụng & API Endpoints

### 1. Luồng Sử Dụng Trên Giao Diện Web
1. **Tải lên chứng từ:** Truy cập giao diện `http://localhost:5173`, chọn chức năng *Tải ảnh / Quét chứng từ*.
2. **Nhận diện & Bóc tách:** Hệ thống tự động đẩy file qua pipeline OCR và hàng đợi Ollama LLM.
3. **Kiểm tra & Hiệu chỉnh:** Xem kết quả trích xuất dạng trường dữ liệu, đối soát trực quan với ảnh gốc và chỉnh sửa nếu cần.
4. **Lưu trữ & Xuất dữ liệu:** Xác nhận lưu vào hệ thống hoặc xuất báo cáo Excel/JSON.

### 2. Một Số API Chính (Backend)
* `POST /api/documents/upload` : Upload ảnh/tài liệu và bắt đầu xử lý.
* `GET /api/documents/{id}` : Lấy thông tin chi tiết và dữ liệu bóc tách của tài liệu.
* `GET /api/documents` : Danh sách lịch sử các tài liệu đã xử lý.
* `POST /api/cards` : Lưu/cập nhật thông tin danh thiếp.
* `GET /api/admin/ocr/...` : Các API quản trị, thống kê và giám sát trạng thái pipeline OCR.
