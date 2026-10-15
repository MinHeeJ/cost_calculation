# UI 개선 계약 체크리스트

## 구현 전 확인

- 실제 ref.html을 Playwright 1.51.1의 설치된 Chromium으로 열고 대시보드, 프로젝트 목록,
  등록 전환을 직접 확인했다. 흰색 56px 헤더, SI 로고, 240/64px 메뉴, 연회색 본문,
  작은 탭, breadcrumb/제목/별/ID 구조를 적용한다.
- 기존 React Router, CSS Modules, lucide-react를 유지한다. 외부 UI 라이브러리 추가 없음.
- backend Controller → Service → MyBatis Mapper 및 OpenAPI 대조: GET /api/menus의
  data는 MenuNode[], GET /api/month-close의 data는 {month,status}[]이다.
- 모든 화면 archetype은 공통 shell placeholder. 목적은 업무 화면 탐색이며 업무 CRUD는 금지.
- 공통 CTA: 메뉴/최근/즐겨찾기/탭의 실제 route 이동, 별 토글, 순서 변경, 탭 닫기·초과 확인.
- 필드: 메뉴 이름·icon·mobile·screenId는 API 읽기 전용. 기간만 화면별 메모리 편집.
- payload: 서버 쓰기 없음. MONTH만 from=to=선택 YYYY-MM GET. YEAR/CUM은 로컬.
- validation: 연도·연월 범위, 즐겨찾기 10개, 최근 5개, 탭 8개 및 dirty 확인 유지.
- 성공: 조회 결과 표시, 로컬 상태 즉시 반영. error/requestId/수동 retry, loading/empty 제공.
- 무인증·권한 항상 허용. 모바일 N은 메뉴 제외 및 PC 안내/대시보드 CTA만 표시.

## 화면별 체크리스트

각 행에 위 공통 CTA/field/payload/validation/success/constraints를 적용한다.
NONE은 기간 없음, YEAR는 연도, YEAR_CUM은 연도·종료월·누계,
MONTH는 연월·API 마감 상태·CLOSED 잠금 안내이며 마감 변경 버튼은 만들지 않는다.

| route | 사용자 목적·화면명 | 기간 | 모바일 |
|---|---|---|---|
| /UI-16 | 대시보드 | NONE | Y |
| /UI-15 | 거래처·영업 파트 | NONE | Y |
| /UI-02 | 개발자 풀 | NONE | Y |
| /UI-01 | 프로젝트 | YEAR | Y |
| /UI-31 | 프로젝트 현황판 | NONE | Y |
| /UI-12 | 투입 배정 | YEAR | Y |
| /UI-05 | 철수 리스트 | YEAR | Y |
| /UI-11 | 인원 계획 대비 투입 | YEAR | Y |
| /UI-28 | 인력 운영 보드 | YEAR | Y |
| /UI-17 | 인력 수요 예측 | YEAR | Y |
| /UI-29 | 영업 파이프라인 | NONE | Y |
| /UI-33 | 외부 인력 후보 | NONE | Y |
| /UI-13 | 청구·입금 | YEAR | Y |
| /UI-03 | 개인 원가·수익표 | YEAR | Y |
| /UI-04 | 프로젝트 원가 분석 | YEAR | Y |
| /UI-19 | 영업 파트 이익금 | MONTH | Y |
| /UI-32 | 단가 재협상 대상 | NONE | Y |
| /UI-10 | 재무 항목 | YEAR | N |
| /UI-07 | 전체 수익 구조 | YEAR_CUM | Y |
| /UI-08 | 영업이익표 | YEAR_CUM | Y |
| /UI-09 | 순이익표 | YEAR_CUM | Y |
| /UI-18 | 사업계획 대비 실적 | YEAR_CUM | Y |
| /UI-20 | 지급 관리 | MONTH | N |
| /UI-21 | 자금수지표 | YEAR | Y |
| /UI-14 | 월 마감 | MONTH | N |
| /UI-26 | AI 분석 도우미 | NONE | Y |
| /UI-30 | AI 인력 추천 | NONE | Y |
| /UI-23 | 사용자 | NONE | N |
| /UI-24 | 권한 | NONE | N |
| /UI-25 | 메뉴 | NONE | N |
| /UI-27 | 공통코드 | NONE | N |

## 최종 검증 결과

- `npm run test:unit`: 19개 파일, 155개 테스트 통과.
- `npm run build`: TypeScript 검사와 Vite 프로덕션 빌드 통과.
- `E2E_BASE_URL=http://127.0.0.1:18321 npm run test:e2e -- --workers=2`: 13개 통과.
- PostgreSQL 16 + 원본 backend를 별도 Compose 프로젝트로 실행했다.
  V1/V2 migration 적용 후 실제 /api/menus와 /api/month-close를 프록시하여 검증했다.
- DB 메뉴의 31개 화면을 직접 열고 제목/자리 본문/신규 CRUD 부재를 확인했다.
- 1440/1280/1279/1024/768/767/375/360px에서 문서 가로 넘침 없음, 헤더 56px 확인.
- 월 조회 CLOSED/OPEN, 화면별 기간 보존, 즐겨찾기 새로고침 복원, 탭·최근 이동 확인.
- 태블릿 헤더/하단 메뉴 접기 동기화 및 모바일 화면 이동 후 메뉴 닫힘 확인.
- 네트워크 실패에 문의 번호 표시, 사용자 재시도 후 실제 API 복구 확인.
- Playwright 1.51.1 및 기존 headless shell만 사용. install/install-deps 실행 없음.
- 실제 렌더링 스크린샷을 데스크톱과 모바일에서 확인했다.
- React Router의 v7 future flag 경고는 기존 비차단 경고로 남아 있다.
- 화면/메뉴/조회 계약 누락 없음. 업무 본문, 저장·삭제 API, 인증/AI/엑셀은
  명세의 명시적 제외 범위로 유지했으며 backend·DB 파일은 변경하지 않았다.

## 화면별 메뉴·API 대조

| route | 메뉴 경로 | operation |
|---|---|---|
| /UI-16 | 대시보드 › 대시보드 | getMenus |
| /UI-15 | 기준정보 › 거래처·영업 파트 | getMenus |
| /UI-02 | 기준정보 › 개발자 풀 | getMenus |
| /UI-01 | 프로젝트·투입 › 프로젝트 | getMenus |
| /UI-31 | 프로젝트·투입 › 프로젝트 현황판 | getMenus |
| /UI-12 | 프로젝트·투입 › 투입 배정 | getMenus |
| /UI-05 | 프로젝트·투입 › 철수 리스트 | getMenus |
| /UI-11 | 프로젝트·투입 › 인원 계획 대비 투입 | getMenus |
| /UI-28 | 인력 계획 › 인력 운영 보드 | getMenus |
| /UI-17 | 인력 계획 › 인력 수요 예측 | getMenus |
| /UI-29 | 인력 계획 › 영업 파이프라인 | getMenus |
| /UI-33 | 인력 계획 › 외부 인력 후보 | getMenus |
| /UI-13 | 매출 › 청구·입금 | getMenus |
| /UI-03 | 원가·수익 › 개인 원가·수익표 | getMenus |
| /UI-04 | 원가·수익 › 프로젝트 원가 분석 | getMenus |
| /UI-19 | 원가·수익 › 영업 파트 이익금 | getMenus, getMonthClose |
| /UI-32 | 원가·수익 › 단가 재협상 대상 | getMenus |
| /UI-10 | 재무·자금 › 재무 항목 | getMenus |
| /UI-07 | 재무·자금 › 전체 수익 구조 | getMenus |
| /UI-08 | 재무·자금 › 영업이익표 | getMenus |
| /UI-09 | 재무·자금 › 순이익표 | getMenus |
| /UI-18 | 재무·자금 › 사업계획 대비 실적 | getMenus |
| /UI-20 | 재무·자금 › 지급 관리 | getMenus, getMonthClose |
| /UI-21 | 재무·자금 › 자금수지표 | getMenus |
| /UI-14 | 마감 › 월 마감 | getMenus, getMonthClose |
| /UI-26 | AI › AI 분석 도우미 | getMenus |
| /UI-30 | AI › AI 인력 추천 | getMenus |
| /UI-23 | 시스템 › 사용자 | getMenus |
| /UI-24 | 시스템 › 권한 | getMenus |
| /UI-25 | 시스템 › 메뉴 | getMenus |
| /UI-27 | 시스템 › 공통코드 | getMenus |
