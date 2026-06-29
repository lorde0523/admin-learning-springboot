import axios from 'axios';

import { getUiId } from '../routes/uiIdRegistry';
import { publishResponse } from './requestInspectorStore';

const isSqlLogRequest = (url = '') => url.startsWith('/api/sql-logs');
const sendTiming = (request) =>
  axios.post('/api/sql-logs/timing', request, { withCredentials: true }).catch(() => undefined);

/**
 * Creates the shared Axios boundary.
 * Dependency injection keeps header behavior testable without a browser or network.
 */
export const createHttpClient = ({
  getPathname = () => window.location.pathname,
  adapter,
  onResponse = publishResponse,
  onTiming = sendTiming,
  now = () => performance.now(),
} = {}) => {
  const client = axios.create({
    withCredentials: true,
    ...(adapter ? { adapter } : {}),
  });

  client.interceptors.request.use((config) => {
    config.requestStartedAt = now();

    if (config.method?.toLowerCase() !== 'get') {
      return config;
    }

    config.headers.set('X-Ui-Id', getUiId(getPathname()));

    if (isSqlLogRequest(config.url)) {
      config.headers.set('X-Sql-Capture-Paused', 'true');
    }

    return config;
  });

  client.interceptors.response.use(
    (response) => {
      const elapsedMillis = Math.round(now() - response.config.requestStartedAt);
      const requestId = response.headers?.get?.('x-request-id')
        ?? response.headers?.['x-request-id'];
      onResponse({
        method: response.config.method?.toUpperCase(),
        url: response.config.url,
        status: response.status,
        elapsedMillis,
        requestId,
        data: response.data,
      });

      if (
        response.config.method?.toLowerCase() === 'get'
        && requestId
        && !isSqlLogRequest(response.config.url)
      ) {
        onTiming({
          requestId,
          uiId: getUiId(getPathname()),
          clientApiElapsedMillis: elapsedMillis,
          clientTotalElapsedMillis: elapsedMillis,
        });
      }
      return response;
    },
    (error) => {
      const config = error.config ?? {};
      onResponse({
        method: config.method?.toUpperCase(),
        url: config.url,
        status: error.response?.status ?? 0,
        elapsedMillis: config.requestStartedAt
          ? Math.round(now() - config.requestStartedAt)
          : null,
        data: error.response?.data,
        error: error.message,
      });
      return Promise.reject(error);
    },
  );

  return client;
};

export const httpClient = createHttpClient();
