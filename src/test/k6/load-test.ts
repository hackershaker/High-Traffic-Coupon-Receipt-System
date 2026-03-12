import { check, fail, sleep } from "k6"
import http from "k6/http"

const BASE_URL = __ENV.BASE_URL ?? "http://localhost:8080"
const LOGIN_PATH = __ENV.LOGIN_PATH ?? "/login"
const ISSUE_PATH = __ENV.ISSUE_PATH ?? "/coupon/me"
const USER_PREFIX = __ENV.USER_PREFIX ?? "load-test-user-"
const USER_PASSWORD = __ENV.USER_PASSWORD ?? "loadtest123"
const USER_COUNT = Number(__ENV.USER_COUNT ?? 1000)
const MAX_SETUP_USERS = Number(__ENV.MAX_SETUP_USERS ?? 500)

// k6 옵션: 시나리오별 도달률, 지속시간, 스레드 수 등을 조정할 수 있습니다.
export const options = {
    scenarios: {
        steadyLoad: {
            executor: "constant-arrival-rate",
            rate: Number(__ENV.REQUEST_RATE ?? 200), // 초당 요청 수
            timeUnit: "1s",
            duration: __ENV.DURATION ?? "1m",
            preAllocatedVUs: Number(__ENV.PREALLOCATED_VUS ?? 100),
            maxVUs: Number(__ENV.MAX_VUS ?? 400),
        },
    },
    thresholds: {
        http_req_failed: ["rate<0.05"], // 실패율 5% 미만을 목표로 설정
        http_req_duration: ["p(95)<1200"],
    },
}

type Session = {
    username: string
    cookie: string
}

/**
 * setup()는 k6가 실행되기 전에 딱 한 번 호출되어 로그인 토큰을 미리 가져옵니다.
 * load-test-user-1 ... load-test-user-N 계정은 DataInitializer에서 미리 생성되므로
 * 그 이름과 비밀번호를 조합해서 로그인하고, 서버가 반환하는 JSESSIONID 쿠키를 수집합니다.
 */
export function setup(): Session[] {
    const sessions: Session[] = []
    const loginTargets = Math.min(USER_COUNT, MAX_SETUP_USERS)

    for (let i = 1; i <= loginTargets; i++) {
        const username = `${USER_PREFIX}${i}`
        const res = http.post(
            `${BASE_URL}${LOGIN_PATH}`,
            `username=${encodeURIComponent(username)}&password=${encodeURIComponent(USER_PASSWORD)}`,
            {
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                redirects: 0,
            },
        )

        const statusOk = check(res, {
            "login status": (r) => r.status === 302,
            "login cookie": (r) => !!r.cookies["JSESSIONID"],
        })

        if (!statusOk) {
            fail(`login failed for ${username} (status=${res.status})`)
        }

        const cookie = res.cookies["JSESSIONID"][0].value
        sessions.push({ username, cookie })
    }

    return sessions
}

/**
 * 기본 함수: setup()이 반환한 세션 중 하나를 뽑아서 쿠폰 발급 API를 호출합니다.
 * 발급 직전에 /coupon/me에 POST 요청만 보내면 Spring Security에서 현재 로그인한 회원을
 * 기반으로 CouponService.issueCouponForUsername이 실행됩니다.
 */
export default function (sessions: Session[]): void {
    if (!sessions.length) {
        fail("no sessions available; setup() must return at least one login result")
    }

    const session = sessions[Math.floor(Math.random() * sessions.length)]
    const headers = {
        Cookie: `JSESSIONID=${session.cookie}`,
        "Content-Type": "application/json",
    }

    const res = http.post(`${BASE_URL}${ISSUE_PATH}`, null, { headers })
    console.log("coupon issue response : ", res.status, res.body);
    check(res, {
        "issue coupon succeeded": (r) => r.status === 200,
    })

    sleep(Number(__ENV.SLEEP_SECONDS ?? 0.25))
}
