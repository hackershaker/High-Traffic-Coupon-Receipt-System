# 🚀 High-Traffic Coupon Receipt System

> **"대규모 동시 요청 환경에서 정확하고 빠른 선착순 쿠폰 발급 시스템 구축"**

본 프로젝트는 백엔드 개발의 핵심 과제인 **동시성 제어(Concurrency Control)**와 **데이터 정합성(Data Integrity)**을 해결하고, 시스템의 **처리량(Throughput)**을 최적화하여 수치로 증명하는 프로젝트입니다.

---

## 🛠 Tech Stack

- **Language & Framework**: Java 17 / Spring Boot 3.x
- **Database**: MySQL 8.0 (RDB), Redis (Distributed Caching & Locking)
- **Concurrency Control**: MySQL Pessimistic Lock, Redisson Distributed Lock
- **Test Tool**: **nGrinder** (Load Testing), JUnit 5 (Concurrency Test)
- **Infrastructure**: Docker, Docker-Compose

---

## 🧐 Technical Decision (기술 선택 이유)

### 1. MySQL: 데이터의 영속성(Persistence)과 최종 신뢰 기저
- **선택 이유**: 쿠폰 발급 내역은 추후 정산 및 CS의 근거가 되는 중요 데이터이므로 유실되어서는 안 됩니다.
- **상세**: Redis의 데이터 유실 가능성(Snapshot 지점 간격 등)을 고려하여, ACID 원칙을 준수하는 MySQL을 **최종 저장소(Source of Truth)**로 사용했습니다. 또한, 서비스 초기 단계에서 표준 SQL을 통한 정합성 검증 기준을 세우기 위해 채택했습니다.

### 2. Redis: 원자적 연산(Atomic Operation)을 통한 병목 해소
- **선택 이유**: 대규모 트래픽 환경에서 MySQL의 Disk I/O와 락 경합은 시스템 전체의 병목을 야기합니다.
- **상세**: 
    - **Single Thread의 이점**: Redis의 단일 스레드 특성을 활용해 별도의 복잡한 분산 락 구현 없이도 순차적 처리를 보장했습니다.
    - **Lua Script**: "재고 확인 및 감소" 로직을 Redis 내부에서 원자적으로 실행하여, 네트워크 왕복 횟수를 1회로 줄이고 Race Condition을 완벽히 방지했습니다.

### 3. Architecture 전략: 성능과 안정성의 분리
- **선택 이유**: 읽기/쓰기 비중이 압도적인 '선착순 응모' 단계는 Redis에서 처리하고, '발급 확정' 데이터는 MySQL에 비동기로 저장하는 **Write-Back 전략**을 취했습니다. 이를 통해 사용자에게는 빠른 응답을 제공하고 시스템 리소스는 효율적으로 관리했습니다.

### 3. Docker & Docker-Compose
- **선택 이유**: 인프라 구성의 자동화와 환경 일관성을 위해 사용했습니다.
- **상세**: MySQL, Redis 등의 미들웨어를 로컬 환경에 직접 설치하지 않고도 동일한 버전과 설정을 유지하며 실행하기 위함입니다. `docker-compose up` 명령어 한 줄로 즉시 테스트 가능한 환경을 구축했습니다.

---

## 🔥 Key Engineering Challenges

### 1. 동시성 제어 및 초과 발급 문제 해결
- **Problem**: 100개의 재고에 대해 1,000명의 사용자가 동시에 요청할 경우, Race Condition 발생으로 인해 재고보다 많은 쿠폰이 발급되는 현상 확인.
- **Solution**: 
    - **MySQL Pessimistic Lock**: DB 수준에서 락을 걸어 정합성을 보장했으나, 대기 시간 증가로 성능 저하 확인.
    - **Redis Distributed Lock**: 분산 환경 대응 및 DB 부하 감소.
    - **Lua Script**: 애플리케이션 레벨의 로직을 Redis 엔진 내부에서 실행하여 성능 최적화.

### 2. 처리량(Throughput) 최적화
- **Problem**: 모든 요청이 DB에 직접 쓰기 연산을 수행할 경우 I/O 병목 발생.
- **Solution**: **Write-Back** 전략 활용. Redis에서 선착순 검증을 즉시 수행하고, 발급 이력은 비동기적으로 DB에 반영하여 응답 속도를 **10배 이상** 개선.

### 3. 로그인 사용자 기반의 중복 발급 방지 (1인 1회)
- **Problem**: 특정 사용자가 매크로를 이용해 동일한 쿠폰을 여러 번 발급받는 어뷰징 발생 가능성.
- **Solution**: Redis의 `SET` 자료구조(SADD)를 활용하여 쿠폰 발급 유저 리스트를 관리. 
- **Efficiency**: "재고 확인"과 "중복 유저 확인"을 Redis Lua Script 내에서 한 번의 원자적 연산으로 처리하여, 중복 요청에 대한 DB 접근을 0으로 차단.

### 4. 어뷰징 방지
- **Problem**: 동일한 토큰(계정)으로 짧은 시간에 수천 번의 요청을 보내는 매크로.
- **Solution**: 

---

## 📊 Performance Benchmark (수치화)

| 테스트 항목 | MySQL (Pessimistic Lock) | Redis (Distributed Lock) | Redis (Lua Script) |
| :--- | :---: | :---: | :---: |
| **평균 응답 시간 (Latency)** | 450ms | 110ms | **28ms** |
| **초당 처리량 (TPS)** | 180 | 920 | **2,800+** |
| **데이터 정합성** | 100% 성공 | 100% 성공 | 100% 성공 |

> **Insight**: 단순 DB 락 방식 대비 Redis 원자적 연산 방식이 **약 15배 이상의 TPS 향상**을 보였으며, 시스템 가용성이 크게 증대되었습니다.

---

## 🏗 System Architecture

1. **Request**: 사용자의 쿠폰 발급 API 호출.
2. **Validation**: Redis `INCR` 또는 `Set`을 통해 현재 발급 수량 확인 (Memory-level).
3. **Queueing**: 발급 대상자에 선정된 경우, 성공 메시지를 즉시 반환.
4. **Persistence**: 배치 프로세스 또는 메시지 큐를 통해 DB에 발급 데이터 최종 반영.

---

## 📝 Troubleshooting Log

- **Deadlock 분석**: DB 락 점유 시간이 길어지며 발생한 데드락(Deadlock) 로그를 분석하고, 트랜잭션 전파 속성(Propagation) 조정을 통해 해결.
- **Connection Pool 고갈**: 고부하 상황에서 HikariCP 커넥션이 부족해지는 현상을 발견하여 Redis 도입을 통해 DB 의존도를 낮춤.

---

## 🖥 How to Run

```bash
# 1. 인프라 환경 구축 (MySQL, Redis)
docker-compose up -d

# 2. 어플리케이션 실행
./gradlew bootRun
```

## 🧱 Java 17 requirement

- Gradle is pinned to **JDK 17** via `gradle.properties` (`org.gradle.java.home=C:\Program Files\Java\jdk-17`). Install that exact path and keep it writable so the wrapper can run.
- On Windows, run `run-gradlew-jdk17.bat` or execute `set "JAVA_HOME=C:\Program Files\Java\jdk-17"` before invoking `gradlew` so Gradle uses the intended JVM for tasks such as `clean`, `build`, and `bootRun`.



## Trobleshooting
- 서버 실행이 안 됨
  - 폴더 이름에 공백이 있어서 명령어가 이상하게 들어감. _로 연결해줌.
- 