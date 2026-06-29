export const AsyncState = ({ query, empty, children }) => {
  if (query.isLoading) return <p className="state">불러오는 중...</p>;
  if (query.isError) {
    const status = query.error?.response?.status;
    return <p className="state state--error">{status === 401 ? 'SSO 로그인이 필요합니다.' : '요청에 실패했습니다.'}</p>;
  }
  if (!query.data?.length && empty) return <p className="state">{empty}</p>;
  return children;
};

