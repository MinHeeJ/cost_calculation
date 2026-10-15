# UI Contract 느슨한 점검 및 최소 보강

## 기준과 범위

- `.aiops-spec/ui-design.md:3~20`의 BASIC-91 구현 인계 확정 절을 우선한다.
- ASCII 배치·간격·컬럼 순서는 판정하지 않았다. 공통 사용자 기능만 대조했다.
- 기존 React Router, Zustand, CSS Modules와 API 봉투·schema를 유지했다.
- backend, persistence, 승인 명세, tests/e2e는 변경하지 않았다.
- 이 기록은 기존 `ui-review.md`의 과거 실행 결과와 별개인 이번 점검 결과다.

## 계약 체크리스트와 확인한 구현

| 영역 | 사용자 기능 계약 | 확인·조치 |
|---|---|---|
| route·menu | 승인 31개 /UI-xx, API 메뉴 이름·그룹, / 및 로고의 /UI-16 이동 | registry, router, Sidebar, SharedWorkspace 및 기존 route 테스트 유지 |
| 화면 목적 | 제목·해당 기간·자리 안내, 업무 CRUD 없음 | PlaceholderScreen 유지; 저장·삭제·등록 CTA를 추가하지 않음 |
| 필드·payload | API 메뉴 정보는 읽기 전용, 기간만 로컬 변경, MONTH는 from=to GET | PeriodBar/api 유지; 서버 쓰기와 lifecycle identity 편집을 추가하지 않음 |
| 성공·상태 | 화면별 기간, 즐겨찾기 10개·최근 5개, 탭 8개·LRU·dirty 취소 보존 | 기존 stores 및 테스트 유지 |
| 오류·재시도 | 수동 retry, 문의 번호·오류 띠 보존, 중복 실행 차단 | retry 중 오류를 유지하고 모든 연결된 retry 버튼을 비활성화 |
| 입력 검증 | 실제 API의 singular field=from/to 오류를 연월 입력에 연결 | 입력의 aria-describedby와 연결된 오류 설명 추가; 성공 시 제거 |
| 즐겨찾기 취소 | 드롭 없이 드래그를 중단하면 순서 불변 | dragEnd에서 보류한 드래그 항목을 해제 |
| 권한·기기 | 무인증·항상 허용; 모바일 N 7개는 메뉴 제외·직접 진입 PC 안내 | 기존 Can, registry, Sidebar, PlaceholderScreen 유지 |
| 월 상태 | OPEN/CLOSED 조회와 마감 안내만 제공 | 기존 상태 표시 유지; 마감 변경 API·잠금 정책을 발명하지 않음 |

## 수정 근거

1. ui-design.md:15, :419 이하 RETRY action mapping:
   useRequest가 retry 시작 시 error를 지우고 RequestState가 loading을 먼저 표시해
   오류 띠와 문의 번호가 사라졌다. 같은 조회의 retry에서는 기존 오류를 유지한다.
   조회 key가 바뀌면 이전 조회 오류·결과는 초기화하여 다른 월의 오류를 섞지 않는다.
2. ui-design.md:1219, :1229:
   Favorites의 dragEnd 처리가 없어 취소한 drag의 항목 ID가 남았다.
   이후 무관한 drop으로 이전 항목이 이동하지 않도록 보류 상태만 해제한다.
3. ui-design.md:10, :12 및 실제 frontend/src/api/types.ts·client.ts:
   현재 API는 ApiError.fields가 아니라 ApiError.field를 사용한다.
   from/to가 모두 하나의 선택 연월에서 만들어지므로 해당 400 오류를 그 입력의 설명에 연결한다.
   전체 오류 띠·문의 번호·다시 시도는 그대로 유지한다.

## 남은 gap·보류 범위

- 실제 업무별 create/edit 모드, 불변 식별자, 저장 payload, 저장 후 목록 refresh/selection,
  업무별 최대 글자수·빈 상태 CTA는 이번 승인 범위의 API/schema에 매핑할 계약이 없다.
  ui-design.md:15~16, :108에 따라 기능을 임의 생성하지 않았고 후속 업무 명세 대상으로 남긴다.
- 현재 API에는 복수 필드 오류 `ApiError.fields` 계약이 없다. 현재 singular field만 연결했다.
  미래 저장 폼의 다중 필드 오류 매핑은 해당 API 확정 뒤 처리해야 한다.
- 실제 인증·권한 거부, 계정/조직/알림 조회·AI·엑셀은 명시적 제외 범위다.
  문서 하단의 일반 permission state 표현만으로 차단 UI를 추가하지 않았다.
- 실제 브라우저의 drag/E2E·실제 backend 연동은 이번 실행에서 재검증하지 않았다.
  jsdom 컴포넌트 검증을 브라우저 또는 DB 통합 검증으로 간주하지 않는다.
- 명백한 누락에 대한 제한된 점검이며 전체 명세의 모든 문장을 완전 검증했다는 의미는 아니다.

## 이번 실행 검증

- 보강한 테스트의 수정 전 실행: 4개 실패, 50개 통과.
  실패 원인은 retry 중 alert 부재, 취소한 drag의 순서 변경,
  from/to 오류와 연월 입력의 접근성 설명 연결 부재였다.
- 최소 수정 후 대상 테스트: 2개 파일, 54개 통과.
- `npm run test:unit`: 19개 파일, 158개 통과.
- `npm run typecheck`: 통과.
- `npm run build`: TypeScript 검사 및 Vite production build 통과.
- React Router v7 future flag 경고가 출력되며 비차단 상태다.
- tests/e2e는 수정하지 않았고 실행하지 않았다. playwright install도 실행하지 않았다.
- 변경한 TypeScript 파일의 120자 초과 행·trailing whitespace 없음.

## 변경 파일

- frontend/src/components/ErrorBanner.tsx
- frontend/src/components/RequestState.tsx
- frontend/src/pages/shared/Favorites.tsx
- frontend/src/pages/shared/PeriodBar.tsx
- frontend/src/pages/shared/useRequest.ts
- frontend/src/pages/shared/Screens.test.tsx
- frontend/src/pages/shared/Workspace.test.tsx
- frontend/docs/ui-contract-audit.md
