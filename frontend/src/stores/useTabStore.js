import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

const initialState = {
  tabs: [],
  activeTabId: null,
};

/**
 * Owns workspace tab metadata. HTTP code reads this store through getState(),
 * while React components subscribe through the hook.
 */
export const useTabStore = create(
  persist(
    (set, get) => ({
      ...initialState,

      openTab: (tab) => {
        const existing = get().tabs.find(({ path }) => path === tab.path);
        if (existing) {
          set({ activeTabId: existing.id });
          return existing;
        }

        set((state) => ({
          tabs: [...state.tabs, tab],
          activeTabId: tab.id,
        }));
        return tab;
      },

      selectTab: (tabId) => {
        if (get().tabs.some(({ id }) => id === tabId)) {
          set({ activeTabId: tabId });
        }
      },

      closeTab: (tabId) => {
        const { tabs, activeTabId } = get();
        const index = tabs.findIndex(({ id }) => id === tabId);
        if (index < 0) return;

        const nextTabs = tabs.filter(({ id }) => id !== tabId);
        const nextActiveTab = activeTabId === tabId
          ? nextTabs[Math.max(0, index - 1)] ?? null
          : nextTabs.find(({ id }) => id === activeTabId) ?? null;

        set({
          tabs: nextTabs,
          activeTabId: nextActiveTab?.id ?? null,
        });
      },

      getActiveTab: () => {
        const { tabs, activeTabId } = get();
        return tabs.find(({ id }) => id === activeTabId) ?? null;
      },

      reset: () => set(initialState),
    }),
    {
      name: 'admin-learning-workspace-tabs',
      storage: createJSONStorage(() => localStorage),
      partialize: ({ tabs, activeTabId }) => ({ tabs, activeTabId }),
    },
  ),
);
