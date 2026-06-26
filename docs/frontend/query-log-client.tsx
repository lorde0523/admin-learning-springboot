import axios from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

export type QueryLogRow = {
  requestId: string;
  uiId: string;
  executedAt: string;
  sqlElapsedMillis: number;
  clientApiElapsedMillis?: number;
  clientTotalElapsedMillis?: number;
  sql: string;
};

export type QueryLogResponse = {
  uiId: string;
  logs: QueryLogRow[];
};

export const fetchQueryLogs = async (uiId: string): Promise<QueryLogResponse> => {
  const { data } = await axios.get<QueryLogResponse>('/api/sql-logs', {
    params: { uiId },
    withCredentials: true,
  });

  return data;
};

export const clearQueryLogs = async (uiId: string): Promise<void> => {
  await axios.delete('/api/sql-logs', {
    params: { uiId },
    withCredentials: true,
  });
};

export const saveQueryTiming = async (params: {
  requestId: string;
  uiId: string;
  clientApiElapsedMillis: number;
  clientTotalElapsedMillis: number;
}): Promise<void> => {
  await axios.post('/api/sql-logs/timing', params, {
    withCredentials: true,
  });
};

const getCookie = (name: string): string => {
  const value = document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.split('=')[1];

  return value ? decodeURIComponent(value) : '';
};

export const queryLogsCacheKeys = {
  all: ['queryLogs'] as const,
  list: (userId: string, uiId: string) =>
    [...queryLogsCacheKeys.all, userId, uiId] as const,
};

export const useQueryLogs = (uiId: string, open: boolean) => {
  const userId = getCookie('LASTUSER');

  return useQuery({
    queryKey: queryLogsCacheKeys.list(userId, uiId),
    queryFn: () => fetchQueryLogs(uiId),
    enabled: open && Boolean(userId) && Boolean(uiId),
  });
};

export const useClearQueryLogs = (uiId: string) => {
  const queryClient = useQueryClient();
  const userId = getCookie('LASTUSER');

  return useMutation({
    mutationFn: () => clearQueryLogs(uiId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: queryLogsCacheKeys.list(userId, uiId),
      });
    },
  });
};

export type QueryLogModalProps = {
  open: boolean;
  uiId: string;
  onClose: () => void;
};

export const QueryLogModal = ({ open, uiId, onClose }: QueryLogModalProps) => {
  const queryLogs = useQueryLogs(uiId, open);
  const clearLogs = useClearQueryLogs(uiId);

  if (!open) {
    return null;
  }

  return (
    <div role="dialog" aria-modal="true" aria-label="조회쿼리">
      <button type="button" onClick={onClose}>
        닫기
      </button>
      <button type="button" onClick={() => clearLogs.mutate()}>
        초기화
      </button>

      {queryLogs.isLoading ? (
        <div>Loading...</div>
      ) : (
        <ul>
          {queryLogs.data?.logs.map((log) => (
            <li key={`${log.requestId}-${log.sqlElapsedMillis}-${log.sql}`}>
              <pre>{log.sql}</pre>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
