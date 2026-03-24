변경된 파일을 커밋하고 push하는 작업을 수행합니다. Co-Authored-By는 포함하지 않습니다.

## 절차

1. `git status`로 변경 파일 확인, `git diff`로 변경 내용 확인, `git log --oneline -5`로 최근 커밋 스타일 확인 (병렬 실행)
2. 변경 사항을 분석하여 커밋 메시지 작성:
   - 이 프로젝트의 커밋 메시지 형식: `type: 한글 설명` (예: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`)
   - 본문이 필요한 경우 빈 줄 후 bullet point로 상세 내용 기술
   - **Co-Authored-By 절대 포함하지 않음**
3. 관련 파일만 선택적으로 `git add` (`.omc/`, `.env`, credentials 등 민감 파일 제외)
4. `git commit` 실행
5. `git push` 실행 (force push 필요 시 사용자에게 확인)
6. `git status`로 최종 상태 확인

## 추가 인자

$ARGUMENTS 가 있으면 커밋 메시지에 참고합니다. 없으면 변경 내용을 분석하여 자동으로 작성합니다.
