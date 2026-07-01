import { useNavigate } from 'react-router-dom';

import { useTabStore } from '../../stores/useTabStore';

/**
 * Renders the Zustand workspace tabs and keeps selection changes aligned with
 * React Router. Screen creation remains in AppLayout where route context exists.
 */
export const WorkspaceTabs = () => {
  const navigate = useNavigate();
  const tabs = useTabStore((state) => state.tabs);
  const activeTabId = useTabStore((state) => state.activeTabId);
  const selectTab = useTabStore((state) => state.selectTab);
  const closeTab = useTabStore((state) => state.closeTab);

  const activate = (tab) => {
    selectTab(tab.id);
    navigate(tab.path);
  };

  const close = (event, tab) => {
    event.stopPropagation();
    closeTab(tab.id);
    const nextTab = useTabStore.getState().getActiveTab();
    navigate(nextTab?.path ?? '/');
  };

  return (
    <div className="workspace-tabs" role="tablist" aria-label="열린 화면">
      {tabs.map((tab) => (
        <div
          key={tab.id}
          className={`workspace-tab ${activeTabId === tab.id ? 'workspace-tab--active' : ''}`}
        >
          <button
            type="button"
            role="tab"
            aria-selected={activeTabId === tab.id}
            onClick={() => activate(tab)}
          >
            {tab.title}
          </button>
          {!tab.pinned && (
            <button
              type="button"
              className="workspace-tab__close"
              aria-label={`${tab.title} 탭 닫기`}
              onClick={(event) => close(event, tab)}
            >
              ×
            </button>
          )}
        </div>
      ))}
    </div>
  );
};

