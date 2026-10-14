#!/usr/bin/env python3
"""Exercise reconciliation decisions against a stateful Admin API transport fixture."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
EXPORTS = HERE.parent.parent / 'infrastructure/docker/keycloak/export'


def fixture_kc(args):
    state_file = Path(os.environ['ACCESS_STATE'])
    state = json.loads(state_file.read_text())
    action, endpoint = args[:2]
    if state.get('fail_get') and action == 'get':
        print('Admin API unavailable', file=sys.stderr)
        return 1
    query = dict(args[i + 1].split('=', 1) for i, a in enumerate(args) if a == '-q')
    if action == 'get':
        # docker exec -i consumes stdin even for a GET; do not let it steal loop rows.
        sys.stdin.read()
        if endpoint == 'roles':
            result = [{'id': 'role-' + name, 'name': name} for name in state['roles']]
        elif endpoint.endswith('/role-mappings/realm'):
            result = [{'name': name} for name in state['groups'][endpoint.split('/')[1]]['roles']]
        else:
            parent = '' if endpoint == 'groups' else endpoint.split('/')[1]
            result = [{'id': key, 'name': value['name']} for key, value in state['groups'].items() if value['parent'] == parent]
        if 'first' in query:
            first = int(query['first'])
            result = result[first:first + int(query['max'])]
        print(json.dumps(result))
        return 0
    if action != 'create':
        raise AssertionError('Unexpected destructive operation: ' + repr(args))
    body = json.load(sys.stdin)
    if endpoint == 'roles':
        assert body['name'] not in state['roles'], 'duplicate role'
        state['roles'].append(body['name'])
    elif endpoint.endswith('/role-mappings/realm'):
        group = state['groups'][endpoint.split('/')[1]]
        for role in body:
            assert role['id'] == 'role-' + role['name']
            assert role['name'] not in group['roles'], 'duplicate mapping'
            group['roles'].append(role['name'])
    else:
        parent = '' if endpoint == 'groups' else endpoint.split('/')[1]
        assert not parent or parent in state['groups'], 'missing parent'
        assert not any(g['parent'] == parent and g['name'] == body['name'] for g in state['groups'].values()), 'duplicate group'
        group_id = 'generated-' + str(len(state['groups']))
        state['groups'][group_id] = dict(name=body['name'], parent=parent, roles=[], members=[])
        print(group_id)
    state['writes'] += 1
    state_file.write_text(json.dumps(state))
    return 0


class RealmAccessTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.state_file = Path(self.tmp.name) / 'state.json'
        self.exports = []
        for name in ['hanmaum-realm.json', 'realm-export.json']:
            p = Path(self.tmp.name) / name
            p.write_text((EXPORTS / name).read_text())
            self.exports.append(p)
        self.state = dict(roles=['default-roles-st', 'offline_access', 'uma_authorization', 'custom'], groups={}, writes=0)
        self.save()

    def save(self):
        self.state_file.write_text(json.dumps(self.state))

    def run_access(self, apply=False, check=True):
        env = dict(os.environ, ACCESS_STATE=str(self.state_file), TEST_SCRIPT=str(Path(__file__).resolve()),
                   HELPER=str(HERE / 'realm-access.sh'), PRIMARY=str(self.exports[0]), SECONDARY=str(self.exports[1]),
                   APPLY='1' if apply else '0', KC_REALM='st-fixture')
        result = subprocess.run(['bash', '-c', '''set -euo pipefail
source "$HELPER"
changes=0
log() { printf '%s\\n' "$*"; }
plan() { changes=$((changes + 1)); log "$*"; }
kc() { python3 "$TEST_SCRIPT" --kc "$@"; }
kc_write_in() { if [[ "$APPLY" == 1 ]]; then kc "$@"; else cat >/dev/null; fi; }
config="$(realm_access_config "$PRIMARY" "$SECONDARY")"
ensure_realm_access "$config"
log "changes=$changes"
'''], env=env, input='', text=True, capture_output=True)
        if check:
            self.assertEqual(0, result.returncode, result.stderr)
        self.state = json.loads(self.state_file.read_text())
        return result

    def paths(self):
        def path(group_id):
            g = self.state['groups'][group_id]
            return (path(g['parent']) if g['parent'] else '') + '/' + g['name']
        return {path(key): value for key, value in self.state['groups'].items()}

    def test_dry_run_plans_nested_groups_and_mappings_without_writes(self):
        before = self.state_file.read_bytes()
        result = self.run_access()
        self.assertEqual(before, self.state_file.read_bytes())
        self.assertIn('create group /ministries/training/editors', result.stdout)
        self.assertIn('map TRAINING_EDITOR, TRAINING_VIEWER to /ministries/training/editors', result.stdout)

    def test_apply_from_empty_uses_server_ids_and_is_repeatable(self):
        self.run_access(True)
        paths = self.paths()
        self.assertEqual(17, len(paths))
        self.assertEqual(['TRAINING_EDITOR', 'TRAINING_VIEWER'], sorted(paths['/ministries/training/editors']['roles']))
        self.assertEqual(['group_leader'], paths['/leaders/groups']['roles'])
        self.assertIn('default-roles-st', self.state['roles'])
        self.assertNotIn('default-roles-hanmaum', self.state['roles'])
        before = self.state_file.read_bytes()
        self.assertIn('changes=0', self.run_access(True).stdout)
        self.assertEqual(before, self.state_file.read_bytes())
        self.assertIn('changes=0', self.run_access().stdout)
        self.assertEqual(before, self.state_file.read_bytes())

    def test_partial_state_preserves_memberships_and_foreign_grants(self):
        self.state['roles'].append('TRAINING_VIEWER')
        self.state['groups'] = {
            'live-root': dict(name='ministries', parent='', roles=[], members=[]),
            'live-training': dict(name='training', parent='live-root', roles=[], members=[]),
            'live-editors': dict(name='editors', parent='live-training', roles=['TRAINING_VIEWER', 'custom'], members=['test-user']),
            'foreign': dict(name='unmanaged', parent='', roles=['custom'], members=['other-user']),
        }
        self.save()
        self.run_access(True)
        self.assertEqual(['test-user'], self.state['groups']['live-editors']['members'])
        self.assertEqual({'custom', 'TRAINING_VIEWER', 'TRAINING_EDITOR'}, set(self.state['groups']['live-editors']['roles']))
        self.assertEqual(['other-user'], self.state['groups']['foreign']['members'])

    def test_existing_groups_after_first_page_are_found(self):
        self.state['groups'] = {f'foreign-{i}': dict(name=f'foreign-{i}', parent='', roles=[], members=[]) for i in range(105)}
        self.state['groups']['existing-staff'] = dict(name='staff', parent='', roles=[], members=['test-user'])
        self.save()
        self.run_access(True)
        self.assertEqual(122, len(self.state['groups']))
        self.assertEqual(['test-user'], self.state['groups']['existing-staff']['members'])

    def test_new_role_and_group_are_consumed_without_script_changes(self):
        for p in self.exports:
            data = json.loads(p.read_text())
            data['roles']['realm'].append(dict(name='EXAMPLE_VIEWER', composite=False))
            data['groups'].append(dict(name='example', realmRoles=['EXAMPLE_VIEWER']))
            p.write_text(json.dumps(data))
        self.run_access(True)
        self.assertEqual(['EXAMPLE_VIEWER'], self.paths()['/example']['roles'])

    def test_export_drift_fails_before_writes(self):
        data = json.loads(self.exports[1].read_text())
        data['groups'].append(dict(name='unexpected'))
        self.exports[1].write_text(json.dumps(data))
        self.assertNotEqual(0, self.run_access(True, False).returncode)
        self.assertEqual(0, self.state['writes'])

    def test_undefined_role_mapping_fails_before_writes(self):
        for p in self.exports:
            data = json.loads(p.read_text())
            data['groups'][0]['realmRoles'] = ['UNDEFINED']
            p.write_text(json.dumps(data))
        self.assertNotEqual(0, self.run_access(True, False).returncode)
        self.assertEqual(0, self.state['writes'])

    def test_admin_read_failure_is_not_treated_as_missing_configuration(self):
        self.state['fail_get'] = True
        self.save()
        result = self.run_access(True, False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Admin API unavailable', result.stderr)
        self.assertEqual(0, self.state['writes'])


if __name__ == '__main__':
    if sys.argv[1:2] == ['--kc']:
        sys.exit(fixture_kc(sys.argv[2:]))
    unittest.main()
