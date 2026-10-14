#!/usr/bin/env python3
"""Run configure-realm.sh against an owned, disposable Keycloak 26.3 container."""
import json
import http.client
import os
from pathlib import Path
import secrets
import sys
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
CONTAINER = f'hdn-realm-access-test-{os.getpid()}'
PORT = int(os.environ.get('KC_TEST_PORT', '18091'))
BASE = f'http://127.0.0.1:{PORT}'
REALM = 'realm-access-test-st'
ADMIN = 'realm-test-admin'
PASSWORD = secrets.token_hex(24)


def main():
    with tempfile.TemporaryDirectory(prefix='hdn-realm-access-') as tmp:
        failed = True
        owned = False
        token = None

        def refresh_token():
            nonlocal token
            body = urllib.parse.urlencode(dict(client_id='admin-cli', grant_type='password', username=ADMIN, password=PASSWORD)).encode()
            with urllib.request.urlopen(BASE + '/realms/master/protocol/openid-connect/token', data=body, timeout=15) as response:
                token = json.load(response)['access_token']

        def request(path, data=None, method=None, retry=True):
            body = None if data is None else json.dumps(data).encode()
            req = urllib.request.Request(BASE + path, data=body, method=method,
                                         headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
            try:
                with urllib.request.urlopen(req, timeout=15) as response:
                    raw = response.read()
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as error:
                if error.code != 401 or not retry:
                    raise
                refresh_token()
                return request(path, data, method, retry=False)

        def api(path='', data=None, method=None):
            return request('/admin/realms/' + REALM + ('/' + path if path else ''), data, method)

        def create_group(name, parent=None):
            api('groups' if parent is None else f'groups/{parent}/children', {'name': name}, 'POST')
            groups = api('groups' if parent is None else f'groups/{parent}/children')
            return next(g['id'] for g in groups if g['name'] == name)

        def groups():
            result = {}

            def walk(nodes, parent=''):
                for g in nodes:
                    path = parent + '/' + g['name']
                    result[path] = dict(id=g['id'], roles=sorted(r['name'] for r in api(f'groups/{g["id"]}/role-mappings/realm')))
                    walk(api(f'groups/{g["id"]}/children?max=1000'), path)

            walk(api('groups?max=1000'))
            return result

        def snapshot():
            return dict(roles={r['name']: r.get('description', '') for r in api('roles?max=1000')}, groups=groups(),
                        memberships=sorted(g['id'] for g in api(f'users/{user_id}/groups')),
                        clients=sorted(c['clientId'] for c in api('clients')))

        def configure(apply=False):
            env = dict(os.environ, KC_CONTAINER=CONTAINER, KC_REALM=REALM, KC_ACCESS_ONLY='1', APPLY='1' if apply else '0')
            # The access-only mode requires neither a dashboard URL nor host credentials.
            env['DASHBOARD_URLS'] = 'http://localhost:3000'  # Must be ignored in access-only mode.
            for key in ['KC_ADMIN_USER', 'VERIFY_USERNAME']:
                env.pop(key, None)
            with (Path(tmp) / 'configure.log').open('w') as output:
                result = subprocess.run(['bash', str(ROOT / 'scripts/keycloak/configure-realm.sh')], env=env,
                                        stdout=output, stderr=subprocess.STDOUT, timeout=300)
            output = (Path(tmp) / 'configure.log').read_text()
            if result.returncode:
                raise AssertionError('configure-realm.sh failed:\n' + output)
            return output

        try:
            env = dict(os.environ, KC_BOOTSTRAP_ADMIN_USERNAME=ADMIN, KC_BOOTSTRAP_ADMIN_PASSWORD=PASSWORD)
            subprocess.run(['docker', 'run', '-d', '--name', CONTAINER, '-p', f'127.0.0.1:{PORT}:8090',
                            '--env', 'KC_BOOTSTRAP_ADMIN_USERNAME', '--env', 'KC_BOOTSTRAP_ADMIN_PASSWORD',
                            '-v', f'{ROOT}/infrastructure/docker/keycloak/themes/hanmaum:/opt/keycloak/themes/hanmaum:ro',
                            'quay.io/keycloak/keycloak:26.3.0', 'start-dev', '--http-port=8090'],
                           env=env, check=True, stdout=subprocess.DEVNULL)
            owned = True
            deadline = time.monotonic() + 120
            while True:
                try:
                    with urllib.request.urlopen(BASE + '/realms/master', timeout=2):
                        break
                except (OSError, http.client.HTTPException):
                    if time.monotonic() > deadline:
                        raise TimeoutError('Disposable Keycloak did not start')
                    time.sleep(1)
            refresh_token()
            request('/admin/realms', dict(realm=REALM, enabled=True, displayName='Preserve this fixture'), 'POST')
            for role in ['TRAINING_VIEWER', 'external-role']:
                api('roles', {'name': role, 'description': 'outdated fixture description', 'attributes': {'fixture': ['preserve']}}, 'POST')
            ministries = create_group('ministries')
            training = create_group('training', ministries)
            editors = create_group('editors', training)
            external = create_group('external-group')
            role_objects = [api('roles/' + name) for name in ['TRAINING_VIEWER', 'external-role']]
            api(f'groups/{editors}/role-mappings/realm', role_objects, 'POST')
            api('users', dict(username='membership-fixture', enabled=True), 'POST')
            user_id = api('users?username=membership-fixture&exact=true')[0]['id']
            api(f'users/{user_id}/groups/{editors}', method='PUT')
            api(f'users/{user_id}/groups/{external}', method='PUT')

            before = snapshot()
            dry_run = configure()
            assert 'create realm role TRAINING_EDITOR' in dry_run
            assert snapshot() == before, 'Dry-run changed realm state'
            print('PASS: dry-run plans missing descendants without changing the live fixture', flush=True)

            configure(True)
            applied = snapshot()
            expected = json.loads((ROOT / 'infrastructure/docker/keycloak/export/hanmaum-realm.json').read_text())
            expected_roles = {r['name'] for r in expected['roles']['realm'] if not r['name'].startswith('default-roles-')
                              and r['name'] not in ['offline_access', 'uma_authorization']}
            assert expected_roles <= set(applied['roles'])
            assert 'default-roles-hanmaum' not in applied['roles']
            expected_paths = set()

            def expected_walk(nodes, parent=''):
                for group in nodes:
                    path = parent + '/' + group['name']
                    expected_paths.add(path)
                    expected_walk(group.get('subGroups', []), path)

            expected_walk(expected['groups'])
            assert set(applied['groups']) == expected_paths | set(before['groups'])
            for role in expected['roles']['realm']:
                if role['name'] in expected_roles:
                    assert applied['roles'][role['name']] == role.get('description', '')
            assert api('roles/TRAINING_VIEWER')['attributes'] == {'fixture': ['preserve']}
            for path in ['/ministries', '/ministries/training', '/ministries/training/editors']:
                assert applied['groups'][path]['id'] == before['groups'][path]['id'], 'Existing group replaced'
            assert applied['memberships'] == before['memberships']
            assert applied['clients'] == before['clients'], 'Access-only mode changed clients'
            assert api()['displayName'] == 'Preserve this fixture'
            assert set(applied['groups']['/ministries/training/editors']['roles']) == {'TRAINING_EDITOR', 'TRAINING_VIEWER', 'external-role'}
            assert applied['groups']['/ministries/training/viewers']['roles'] == ['TRAINING_VIEWER']
            assert applied['groups']['/ministries/general_operations/editors']['roles'] == ['GENERAL_OPERATIONS_EDITOR', 'GENERAL_OPERATIONS_VIEWER']
            print('PASS: missing roles/groups/mappings created; existing IDs, grants and memberships preserved', flush=True)

            assert '0 change(s) applied' in configure(True)
            assert snapshot() == applied, 'Second apply changed realm state'
            assert '0 change(s) pending' in configure()
            assert snapshot() == applied
            print('PASS: second apply and final dry-run report zero changes', flush=True)

            # Evaluate server-generated claims; never print or decode an access token.
            api('clients', dict(clientId='role-verification', protocol='openid-connect', publicClient=True, fullScopeAllowed=True), 'POST')
            client_id = api('clients?clientId=role-verification')[0]['id']
            claims = api(f'clients/{client_id}/evaluate-scopes/generate-example-access-token?userId={user_id}&scope=openid')
            assert {'TRAINING_EDITOR', 'TRAINING_VIEWER'} <= set(claims['realm_access']['roles'])
            assert 'admin' not in claims['realm_access']['roles']
            print('PASS: server-evaluated user claims inherit both training roles without admin', flush=True)
            failed = False
        finally:
            if owned:
                if failed:
                    diagnostics = subprocess.run(['docker', 'logs', CONTAINER], capture_output=True, text=True)
                    output = diagnostics.stdout + diagnostics.stderr
                    # Only test-generated credentials exist here; still redact them if logged.
                    output = output.replace(PASSWORD, '<redacted>')
                    if token:
                        output = output.replace(token, '<redacted>')
                    print('Disposable Keycloak diagnostics:\n' + output, file=sys.stderr)
                    config_log = Path(tmp) / 'configure.log'
                    if config_log.exists():
                        print('Reconciler diagnostics:\n' + config_log.read_text(), file=sys.stderr)
                subprocess.run(['docker', 'rm', '-f', CONTAINER], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == '__main__':
    main()
