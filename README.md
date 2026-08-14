# Money Transfer Service

Digital Banking — Money Transfer Service Backend API

## 1. วิธีรันตั้งแต่ศูนย์

### Prerequisites
- Docker & Docker Compose
- Java 21
- Maven 3.6+

### Quick Start (All Services)
```bash
docker-compose up -d
```
สั่งครั้งเดียวจะได้: app + SQL Server + Redis + IBM MQ ครบถ้วน

### Start Infrastructure Only
```bash
docker-compose up -d sqlserver redis ibmmq
```

### Run Application
```bash
mvn spring-boot:run
```

หรือ build JAR แล้วรัน:
```bash
mvn clean package
java -jar target/money-transfer-service-0.0.1-SNAPSHOT.jar
```

### API Base URL
```
http://localhost:8081/api/v1
```

## 2. วิธีรันเทสและวิธีดู Coverage

### Test Coverage

**Unit Tests** (service-level tests with mocks):
- `AccountServiceTest.java` - Test AccountService business logic
  - Deposit operations
  - Withdraw operations
  - Lock acquisition and release
  - Insufficient funds validation
- `TransferServiceTest.java` - Test TransferService business logic
  - Transfer operations
  - Validation (transfer to self, currency mismatch, insufficient funds)
  - Rate limiting
  - Lock acquisition and release

### Run Tests
```bash
mvn test
```

### View Test Coverage
```bash
mvn test jacoco:report
```
Coverage report จะอยู่ที่: `target/site/jacoco/index.html`

## 3. ลิงก์ Swagger UI และวิธีเข้าถึง

### API Documentation (Swagger UI)
```
http://localhost:8081/api/v1/swagger-ui.html
```
หรือ
```
http://localhost:8081/api/v1/swagger-ui/index.html
```

## 4. ตัวอย่างเรียก API ด้วย curl

### เปิดบัญชี
```bash
curl -X POST http://localhost:8081/api/v1/accounts \
  -H "Content-Type: application/json" \
  -d '{
    "ownerName": "สมชาย ใจดี",
    "currency": "THB",
    "initialBalance": 1000.00
  }'
```

### ฝากเงิน
```bash
curl -X POST http://localhost:8081/api/v1/accounts/1/deposit \
  -H "Content-Type: application/json" \
  -d '{"amount": 500.00}'
```

### ถอนเงิน
```bash
curl -X POST http://localhost:8081/api/v1/accounts/1/withdraw \
  -H "Content-Type: application/json" \
  -d '{"amount": 200.00}'
```

### โอนเงิน (with idempotency)
```bash
curl -X POST http://localhost:8081/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-request-id-123" \
  -d '{
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 100.00,
    "currency": "THB"
  }'
```

### ดูยอดคงเหลือ
```bash
curl http://localhost:8081/api/v1/accounts/1/balance
```

### ดูรายการเดินบัญชี
```bash
curl "http://localhost:8081/api/v1/accounts/1/transactions?page=0&size=20"
```

## 5. ตารางสรุปสถานะงาน

| Requirement | Status | Notes |
|-------------|--------|-------|
| Tech Stack (Java 21, Spring Boot 3.x, SQL Server, Redis, IBM MQ, Liquibase) | ✅ Complete | All technologies implemented as specified |
| Account Management (Create, Read, Balance, Transactions, Status, Deposit, Withdraw) | ✅ Complete | All endpoints implemented with validation |
| Transfer Management (Transfer with idempotency, rate limiting, lock) | ✅ Complete | Full idempotency with key + payload hash comparison |
| Distributed Lock (Redis) for withdraw/transfer | ✅ Complete | Lock acquisition ordered by account ID to prevent deadlock |
| Rate Limiting (Redis) for POST /transfers | ✅ Complete | Sliding window: 10 requests/60 seconds per account |
| Idempotency (Key + Payload Hash) | ✅ Complete | Returns existing result if key+payload match, 409 if key+payload differ |
| Database Schema (PK, FK, UNIQUE, CHECK constraints) | ✅ Complete | DDL implemented via Liquibase migrations |
| Ledger (Insert-only, immutable audit trail) | ✅ Complete | All financial operations logged to ledger |
| Error Handling (RFC 7807 Problem Details) | ✅ Complete | All errors return standardized format with X-Request-Id |
| IBM MQ Outbox Pattern | ✅ Complete | Transactional outbox with scheduled publisher |
| IBM MQ Consumer (TransferCompleted) | ✅ Complete | Implemented with Redis deduplication, disabled in dev env |
| Redis Cache (TTL 60s, read-through) | ✅ Complete | Account data cached, invalidated on balance/status changes |
| Database Seed Data | ✅ Complete | 3 sample accounts for testing |
| OpenAPI/Swagger Documentation | ✅ Complete | Auto-generated via springdoc-openapi |
| Unit Tests (AccountService, TransferService) | ✅ Complete | Mock-based unit tests for business logic |
| Integration Tests with Testcontainers | ⚠️ Partial | Not implemented (bonus item) |
| Concurrency Tests | ⚠️ Partial | Not implemented (bonus item) |
| Idempotency Tests | ⚠️ Partial | Not implemented (bonus item) |
| Reconciliation Check | ⚠️ Partial | Not implemented (bonus item) |
| Metrics (Micrometer/Prometheus) | ⚠️ Partial | Not implemented (bonus item) |
| Graceful Shutdown | ⚠️ Partial | Not implemented (bonus item) |
| Load Testing | ⚠️ Partial | Not implemented (bonus item) |

### Future Enhancements (If Time Permits)
- Implement integration tests with Testcontainers for end-to-end testing
- Add concurrency tests to verify thread-safety of withdraw/transfer operations
- Create dedicated idempotency test suite
- Implement reconciliation endpoint/script to verify ledger = balance
- Add Prometheus metrics for monitoring
- Implement graceful shutdown with proper cleanup
- Add load testing with tools like JMeter or k6

## 6. ข้อจำกัด / สมมติฐาน

1. **IBM MQ Consumer Disabled in Dev Environment**
   - IBM MQ Consumer (TransferCompletedConsumer) ถูก disabled ใน dev environment
   - เนื่องจาก IBM MQ dev container มี authentication limitations ที่ไม่สามารถ configure ได้ใน local environment
   - Code ถูก implement ตาม spec และพร้อมใช้งานใน production
   - สามารถ enable ได้โดยตั้งค่า property ใน application.yml

2. **Balance Caching**
   - Account balance ถูก cache ใน Redis พร้อม TTL 60 วินาที
   - Cache ถูก invalidate ทันทีเมื่อ balance เปลี่ยน (deposit/withdraw/transfer)
   - เพื่อรักษา backward compatibility กับ API responses ที่คาดหวัง balance

3. **Database Seeding**
   - Liquibase migration สร้าง sample accounts 3 บัญชีสำหรับทดสอบ
   - Account IDs: 1, 2, 3 พร้อมยอดเงินเริ่มต้น 1000.00, 5000.00, 10000.00 THB

4. **Rate Limiting**
   - ใช้ sliding window algorithm (ไม่ใช่ simple counter)
   - 10 requests per 60 seconds per account สำหรับ POST /transfers

## Additional Information

### Tech Stack
- **Java 21**
- **Spring Boot 3.5.16**
- **Microsoft SQL Server 2022**
- **Redis** (Cache, Distributed Lock, Rate Limiting)
- **IBM MQ** (JMS Messaging with Outbox Pattern)
- **Liquibase** (Database Migration)
- **Maven** (Build Tool)

### Environment Variables
ตั้งค่าใน `application.yml`:
- `spring.datasource.url` - SQL Server connection string
- `spring.redis.host` - Redis host
- `ibm.mq.conn-name` - IBM MQ connection
- `ibm.mq.queue-manager` - Queue manager name
- `ibm.mq.channel` - Channel name
