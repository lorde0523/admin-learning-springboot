import { useState } from 'react';

import { AsyncState } from '../components/molecules/AsyncState';
import { SearchBar } from '../components/molecules/SearchBar';
import { EntityEditor } from '../components/organisms/EntityEditor';
import { EntityGrid } from '../components/organisms/EntityGrid';
import { WorkbenchTemplate } from '../components/templates/WorkbenchTemplate';
import { useRoles, useUserMutations, useUsers } from '../queries/adminQueries';

export const UsersPage = () => {
  const [input, setInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState(null);
  const [roleIds, setRoleIds] = useState([]);
  const users = useUsers(keyword);
  const roles = useRoles();
  const mutations = useUserMutations();
  const save = (request) => selected ? mutations.update.mutate({ id: selected.id, request }) : mutations.create.mutate(request);

  return (
    <WorkbenchTemplate eyebrow="JPA CRUD" title="사용자 워크벤치" description="사용자 생명주기와 역할 관계를 검증합니다."
      toolbar={<SearchBar value={input} onChange={setInput} onSearch={() => setKeyword(input)} placeholder="로그인 ID 검색" />}
      side={<EntityEditor kind="사용자" selected={selected} fields={[{ name: 'loginId', label: '로그인 ID', immutable: true }, { name: 'name', label: '이름' }]} onSave={save} onDelete={() => confirm('사용자를 삭제할까요?') && mutations.remove.mutate(selected.id)}>
        {selected && <fieldset><legend>역할 배정</legend>{roles.data?.map((role) => <label key={role.id} className="checkbox"><input type="checkbox" checked={roleIds.includes(role.id)} onChange={() => setRoleIds((ids) => ids.includes(role.id) ? ids.filter((id) => id !== role.id) : [...ids, role.id])}/>{role.roleName}</label>)}<button type="button" className="button button--secondary" onClick={() => mutations.assignRoles.mutate({ id: selected.id, roleIds })}>역할 저장</button></fieldset>}
      </EntityEditor>}
    >
      <AsyncState query={users} empty="사용자가 없습니다.">
        <EntityGrid rows={users.data ?? []} columns={[{ field: 'id', headerName: 'ID', maxWidth: 90 }, { field: 'loginId', headerName: '로그인 ID' }, { field: 'name', headerName: '이름' }, { field: 'enabled', headerName: '사용' }, { field: 'createdAt', headerName: '등록일' }]} onSelectionChanged={({ api }) => { const row = api.getSelectedRows()[0] ?? null; setSelected(row); setRoleIds(row?.roles?.map(({ id }) => id) ?? []); }} />
      </AsyncState>
    </WorkbenchTemplate>
  );
};

