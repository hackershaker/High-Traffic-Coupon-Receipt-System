# Portfolio Review TODO (2026-03-14)

## 프로젝트 냉정 평가 요약

- 현재 상태 평가는 대략 `7/10`.
- 주제와 방향성은 좋음: 고트래픽 쿠폰 발급에서 정합성 보장이라는 문제 정의가 명확함.
- 강점:
  - 비관적 락(`PESSIMISTIC_WRITE`) 기반 발급 로직 구현
  - JVM 동시성 테스트 + k6 HTTP 부하 테스트 분리
  - 시딩 로직의 멱등성(부족분만 추가)
- 약점:
  - 인가 취약점 가능성: `/coupon`이 `userId`를 직접 받아 타 사용자 발급 시도 가능
  - DTO 입력 검증/응답 검증 부족
  - 테스트 환경 의존성(Docker 없으면 `./gradlew test` 실패)
  - H2 기반 동시성 검증으로 MySQL 락 동작과 차이 가능
  - 문서/코드 불일치(README는 Boot 3.x, 실제는 4.0.3)
  - 운영 프로필/대량 시딩 기본값 등 운영 안전성 개선 필요

## 보완 우선순위 TODO

- [x] 1) `/coupon` 제거 또는 관리자 전용 제한, 일반 유저는 `/coupon/me`만 사용
  - `CouponController`에서 `POST /coupon` 엔드포인트 제거, `POST /coupon/me`만 유지
  - `SecurityConfig`의 CSRF 예외 목록에서 `"/coupon"` 제거
  - 관련 라우팅 검증 테스트 추가(`CouponControllerTest`)
- [x] 2) DTO 검증 추가(`@Valid`, `@NotBlank`, `@Size`) + 글로벌 예외 응답 포맷 통일
  - `MemberRegisterRequest`에 `@NotBlank`, `@Size` 검증 규칙 적용
  - `MemberController`의 `/signup` 요청에 `@Valid` 적용
  - `ApiErrorResponse` + `GlobalExceptionHandler`로 공통 에러 응답 포맷 통일
  - 검증 실패/중복 회원/사용자 미존재 케이스 응답 테스트 보강
- [x] 3) 쿠폰 API 컨트롤러 테스트 추가(상태코드, 응답 JSON, 인증/인가 실패 케이스)
  - `CouponControllerTest`를 MySQL Testcontainers 기반으로 실행하도록 전환
  - 정상 발급(`ISSUED`)과 재고 소진(`EMPTY`)의 응답 JSON(`result`) 검증 추가
  - 미인증 요청 시 로그인 리다이렉트(인증 실패) 케이스 검증 추가
  - 사용자 미존재(404) 및 구 `/coupon` 미노출(404) 케이스 유지/검증
- [x] 4) Docker 필요 테스트 분리(태그/프로파일)로 기본 `gradlew test` 안정화
  - Testcontainers 기반 테스트(`CouponControllerTest`, `MemberRepositoryMysqlTest`)에 `@Tag("docker")` 적용
  - 기본 `test` task에서 `docker` 태그를 제외하도록 설정해 Docker 없는 환경에서도 안정 실행
  - `dockerTest` 전용 task 추가로 Docker 의존 테스트를 분리 실행 가능하게 구성
- [x] 5) MySQL 기준 동시성 통합테스트 강화(락 대기/타임아웃/데드락 회복)
  - `CouponMysqlLockingIntegrationTest` 추가 (`@Tag("docker")`, Testcontainers + MySQL 8)
  - 락 대기: MySQL named lock(`GET_LOCK`)으로 대기 후 해제 시 진행되는지 검증
  - 락 타임아웃: `innodb_lock_wait_timeout=1` 설정 후 동일 행 충돌 시 timeout 예외 검증
  - 데드락 회복: 교차 잠금으로 deadlock 유도 후, 이후 쿠폰 발급이 정상 진행되는지 검증
- [ ] 6) README와 코드 동기화(버전, 실행 전제, 테스트 매트릭스 최신화)
- [ ] 7) `spring.profiles.active` 하드코딩 제거, 환경변수/배포 설정으로 외부화
- [ ] 8) 시딩 로직 성능 개선(존재 여부 조회 최적화, 배치 insert)
- [ ] 9) 관측성 추가(Micrometer 등): 발급 성공률, 에러율, p95/p99 지표 수집/리포트
- [ ] 10) 코드 품질 정리(필드 주입 -> 생성자 주입, 네이밍/일관성 개선)
- [ ] 11) Member에 현재 계정 상태를 나타내는 컬럼 추가(activate, deactivated 등)
- [ ] 12) 서버 로그를 주기적으로 저장하는 로직 생성
- [ ] 13) k6 대용량 부하 테스트를 모킹 없이 spring 서버를 http 프로토콜만 테스트하도록 별개 설정

## 참고 메모

- 최근 실행 결과에서 Docker 환경 부재 시 Testcontainers 테스트가 실패할 수 있음.
- 포트폴리오 제출 전에는 "재현 가능한 실행 경로"를 반드시 분리/문서화할 것.
