import { useSyncExternalStore } from 'react';

let latestResponse = null;
const listeners = new Set();

export const publishResponse = (response) => {
  latestResponse = response;
  listeners.forEach((listener) => listener());
};

export const useLatestResponse = () =>
  useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => latestResponse,
  );

