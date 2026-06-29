import { useAuth } from '../queries/adminQueries';
import { StatusBadge } from '../components/atoms/StatusBadge';
import { WorkbenchTemplate } from '../components/templates/WorkbenchTemplate';

export const DashboardPage = () => {
  const auth = useAuth();
  const principal = auth.data?.principal;
  return (
    <WorkbenchTemplate
      eyebrow="OVERVIEW"
      title="백엔드 학습 워크벤치"
      description="Spring Boot API의 데이터 흐름, 응답, SQL 실행 결과를 한곳에서 확인합니다."
    >
      <div className="metric-grid">
        <article className="metric"><span>백엔드</span><strong>{auth.isError ? '연결 오류' : '연결됨'}</strong></article>
        <article className="metric"><span>SSO 세션</span><strong>{auth.data?.authenticated ? '인증됨' : '미인증'}</strong></article>
        <article className="metric"><span>현재 사용자</span><strong>{principal?.displayName ?? principal?.username ?? '-'}</strong></article>
      </div>
      <StatusBadge active={auth.data?.authenticated}>{auth.data?.authenticated ? 'SQL 추적 사용 가능' : 'SSO 로그인 필요'}</StatusBadge>
    </WorkbenchTemplate>
  );
};

