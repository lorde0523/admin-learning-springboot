import { useState } from 'react';

import { useLatestResponse } from '../../api/requestInspectorStore';
import { getUiId } from '../../routes/uiIdRegistry';
import { useClearSqlLogs, useSqlLogs } from '../../queries/adminQueries';
import { Button } from '../atoms/Button';

export const RequestInspector = () => {
  const [open, setOpen] = useState(false);
  const latest = useLatestResponse();
  const uiId = getUiId(window.location.pathname);
  const logs = useSqlLogs(uiId, open);
  const clearLogs = useClearSqlLogs(uiId);

  return (
    <aside className="inspector">
      <div className="inspector__header">
        <div>
          <strong>요청 검사</strong>
          <span>{latest ? `${latest.method} ${latest.status} · ${latest.elapsedMillis}ms` : '요청 없음'}</span>
        </div>
        <Button variant="secondary" onClick={() => setOpen((value) => !value)}>
          {open ? '접기' : '열기'}
        </Button>
      </div>
      {open && (
        <div className="inspector__body">
          <section>
            <h3>마지막 응답</h3>
            <pre>{JSON.stringify(latest, null, 2)}</pre>
          </section>
          <section>
            <div className="section-title">
              <h3>SQL 로그</h3>
              <Button variant="secondary" onClick={() => clearLogs.mutate()}>초기화</Button>
            </div>
            {logs.error?.response?.status === 404 ? (
              <p>SQL 추적 기능이 비활성화되어 있습니다.</p>
            ) : (
              <pre>{JSON.stringify(logs.data?.logs ?? [], null, 2)}</pre>
            )}
          </section>
        </div>
      )}
    </aside>
  );
};

