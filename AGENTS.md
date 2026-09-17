# desyp 작업 지침

작업 전 제품 정책과 실행법은 `README.md`, 개발 규칙과 서비스 설계는 `CLAUDE.md`를 읽고 따른다. 두 문서가 저장소의 유일한 프로젝트 문서 기준이다.

## Graphify

- 코드 구조 질문은 `graphify-out/graph.json`이 있으면 `graphify query "질문"`으로 먼저 탐색한다.
- 그래프의 근거 파일을 확인하고, 문서의 계획과 실제 구현을 구분한다.
- 코드 변경 후 프로젝트 루트에서 `graphify update .`를 실행한다.
- 문서의 의미 분석까지 갱신하려면 Codex에서 `/graphify . --update`를 요청한다.
- 로컬 결과: `graphify-out/graph.html`, `graphify-out/GRAPH_REPORT.md`, `graphify-out/graph.json`.
- 결과와 캐시는 Git에 포함하지 않는다. 다른 환경에서는 `/graphify .`로 생성한다.
