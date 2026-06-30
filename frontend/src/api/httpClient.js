import axios from 'axios';

import { getUiId } from '../routes/uiIdRegistry';
import { publishResponse } from './requestInspectorStore';

const isSqlLogRequest = (url = '') => url.startsWith('/api/sql-logs');
const TRACE_TYPE = 'query';
const afterNextPaint = (callback) => {
  if (typeof requestAnimationFrame !== 'function') {
    setTimeout(callback, 0);
    return;
  }
  requestAnimationFrame(() => requestAnimationFrame(callback));
};
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
  afterRender = afterNextPaint,
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

    config.headers.set('X-Trace-Type', TRACE_TYPE);
    config.headers.set('X-Ui-Id', getUiId(getPathname()));

    if (isSqlLogRequest(config.url)) {
      config.headers.set('X-Sql-Capture-Paused', 'true');
    }

    return config;
  });

  client.interceptors.response.use(
    (response) => {
      const elapsedMillis = Math.round(now() - response.config.requestStartedAt);
      onResponse({
        method: response.config.method?.toUpperCase(),
        url: response.config.url,
        status: response.status,
        elapsedMillis,
        data: response.data,
      });

      const apiStartedAt = response.headers?.get?.('x-api-started-at')
        ?? response.headers?.['x-api-started-at'];
      if (
        response.config.method?.toLowerCase() === 'get'
        && apiStartedAt
        && !isSqlLogRequest(response.config.url)
      ) {
        afterRender(() => {
          const totalTimeMillis = Math.round(
            now() - response.config.requestStartedAt,
          );
          onTiming({
            traceType: TRACE_TYPE,
            apiStartedAt,
            uiId: getUiId(getPathname()),
            clientTimeMillis: Math.max(0, totalTimeMillis - elapsedMillis),
            totalTimeMillis,
          });
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
