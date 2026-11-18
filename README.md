# HaircutScheduleBackend

Backend cho ứng dụng quản lý lịch làm việc của barber và đặt lịch cắt tóc, được xây dựng bằng Java và Spring Boot.

## 1. Công nghệ sử dụng

| Công nghệ | Vai trò |
|---|---|
| Java 17+ | Ngôn ngữ lập trình. Dự án được biên dịch với Java release 17. |
| Spring Boot 4.1.1 | Framework xây dựng backend và quản lý cấu hình ứng dụng. |
| Spring for GraphQL | Cung cấp GraphQL API tại endpoint `/graphql`. |
| Spring Data JPA / Hibernate | Truy cập và ánh xạ dữ liệu quan hệ giữa Java Entity và PostgreSQL. |
| PostgreSQL 18.6 | Cơ sở dữ liệu quan hệ cho môi trường phát triển; kiểm thử sử dụng PostgreSQL Testcontainers. |
| Flyway | Quản lý và thực thi database migration theo phiên bản. |
| Redis | Dịch vụ lưu trữ dữ liệu trong bộ nhớ, được ứng dụng sử dụng cùng Spring Data Redis. |
| JUnit, Spring Boot Test, Mockito | Framework và thư viện kiểm thử. |
| Testcontainers | Khởi chạy PostgreSQL container tạm thời phục vụ integration test. |
| Docker Compose | Khởi chạy các dịch vụ hạ tầng phục vụ phát triển cục bộ. |

## 2. Yêu cầu môi trường

Cài đặt các công cụ sau trước khi chạy dự án:

- **JDK 17 trở lên**. Maven compiler của dự án đang đặt Java release 17.
- **Docker Desktop** và Docker Compose v2. Docker cần được khởi động khi chạy PostgreSQL/Redis hoặc chạy test dùng Testcontainers.
- **Git** để clone mã nguồn.
- IDE như IntelliJ IDEA (không bắt buộc).
- DBeaver hoặc công cụ PostgreSQL client khác (không bắt buộc, dùng để xem dữ liệu trong database).

Kiểm tra môi trường:

```powershell
java -version
docker --version
docker compose version
```
## 3. Cấu hình biến môi trường

### 3.1. Tạo file `.env`

Tạo file `.env` tại thư mục gốc dự án, cùng cấp với `pom.xml` và file Docker Compose.

Ví dụ cấu hình cho môi trường phát triển cục bộ trên Windows:

```dotenv
# PostgreSQL administrator (dùng khi khởi tạo PostgreSQL)
POSTGRES_ADMIN_PASSWORD=change_me_admin_password
POSTGRES_PORT=5432

# Database account mà ứng dụng Spring Boot sử dụng
DB_USERNAME=haircut_app
DB_PASSWORD=change_me_app_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 3.2. Giải thích biến môi trường

| Biến | Bắt buộc | Ý nghĩa |
|---|---|---|
| `POSTGRES_ADMIN_PASSWORD` | Có | Mật khẩu tài khoản quản trị PostgreSQL (`postgres`) được Docker Compose sử dụng khi khởi tạo database. Đây không phải mật khẩu mà Spring Boot dùng để kết nối ứng dụng. |
| `POSTGRES_PORT` | Không, mặc định `5432` | Port PostgreSQL được publish trên máy host. Nếu đổi port này, cần cập nhật port trong `DB_URL` cho khớp. |
| `DB_USERNAME` | Không, mặc định `haircut_app` | Tên database user mà Spring Boot dùng để kết nối tới database phát triển. |
| `DB_PASSWORD` | Có | Mật khẩu của `DB_USERNAME`. Biến này cũng được script khởi tạo PostgreSQL sử dụng để tạo/cấu hình application role. |
| `REDIS_HOST` | Không, mặc định `localhost` | Hostname/IP của Redis. Khi chạy backend trực tiếp trên máy host, dùng `localhost`; khi backend chạy trong cùng Docker Compose network, dùng tên service Redis. |
| `REDIS_PORT` | Không, mặc định `6379` | Port Redis mà ứng dụng kết nối tới. |

**Lưu ý về file `.env`:** Docker Compose tự đọc `.env` để thay thế biến trong file Compose. Tuy nhiên, khi chạy Spring Boot trực tiếp bằng Maven trên máy host, Spring Boot không tự động đọc file `.env` theo mặc định. Để các giá trị trong `.env` được Spring đọc, cấu hình trong `src/main/resources/application.properties` cần có:

```properties
spring.config.import=optional:file:.env[.properties]
```

Một cách khác là khai báo các biến môi trường trực tiếp trong hệ điều hành hoặc cấu hình Run/Debug của IDE.

## 4. Hướng dẫn chạy dự án và chạy test

### 4.1. Clone dự án

```bash
git clone https://github.com/JustAnother-12/HaircutScheduleBackend.git
cd HaircutScheduleBackend
```

### 4.2. Khởi động PostgreSQL và Redis

Đảm bảo Docker Desktop đang chạy, sau đó mở terminal tại thư mục gốc dự án:

```bash
docker compose up -d
```

Kiểm tra trạng thái các container:

```bash
docker compose ps
```

Xem log các dịch vụ:

```bash
docker compose logs -f
```

### 4.3. Chạy Spring Boot backend

Trên Windows PowerShell:

```powershell
./mvnw.cmd spring-boot:run
```

Trên Linux/macOS:

```bash
./mvnw spring-boot:run
```

Nếu không chỉ định profile khác, ứng dụng sử dụng profile `dev`. Flyway thực thi các migration khi ứng dụng khởi động; Hibernate dùng cấu hình kiểm tra mapping/schema thay vì tự tạo schema.

Khi ứng dụng khởi động thành công, GraphQL endpoint mặc định có thể được truy cập tại:

```text
http://localhost:8080/graphql
```

### 4.4. Chạy test

Docker Desktop phải đang chạy vì integration test sử dụng Testcontainers để tạo PostgreSQL container riêng cho quá trình kiểm thử.

Trên Windows PowerShell:

```powershell
./mvnw.cmd clean test
```

Trên Linux/macOS:

```bash
./mvnw clean test
```
Test profile sử dụng PostgreSQL Testcontainers độc lập với database phát triển `barbershop_dev`. Container kiểm thử được Testcontainers quản lý và dọn dẹp sau khi test kết thúc.

Sau khi chạy, xem báo cáo tại:

```text
target/surefire-reports/
```

## 5. Hướng dẫn dừng dự án

### 5.1. Dừng backend

Trong terminal đang chạy Spring Boot, nhấn:

```text
Ctrl + C
```

### 5.2. Dừng các container nhưng giữ dữ liệu

Dừng PostgreSQL và Redis, không xóa container hoặc volume:

```bash
docker compose stop
```

Khởi động lại các container đã dừng:

```bash
docker compose start
```

### 5.3. Dừng và xóa container/network nhưng giữ dữ liệu

```bash
docker compose down
```

Lệnh này xóa container và network do Compose tạo, nhưng **giữ lại named volumes**. Dữ liệu PostgreSQL/Redis vẫn được lưu để sử dụng ở lần chạy tiếp theo.

### 5.4. Xóa cả dữ liệu PostgreSQL và Redis

```bash
docker compose down -v
```

> **Cảnh báo:** `-v` xóa các named volumes do Compose quản lý. Dữ liệu database và dữ liệu Redis được lưu trong các volume đó sẽ bị mất. Chỉ chạy lệnh này khi thực sự muốn reset dữ liệu môi trường phát triển.