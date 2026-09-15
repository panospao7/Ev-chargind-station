import fs from 'fs';
const base = 'http://127.0.0.1:18080';

async function adminToken() {
  for (let i = 0; i < 10; i++) {
    try {
      const res = await fetch(base + '/realms/master/protocol/openid-connect/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: 'grant_type=password&client_id=admin-cli&username=admin&password=admin'
      });
      if (res.status === 200) {
        const body = await res.json();
        if (body.access_token) return body.access_token;
      }
    } catch (ignored) { }
    await new Promise(r => setTimeout(r, 3000));
  }
  throw new Error('admin token never became available');
}

const at = await adminToken();
const H = () => ({ Authorization: 'Bearer ' + at, 'Content-Type': 'application/json' });

let r = await fetch(base + '/admin/realms/master/clients', {
  method: 'POST', headers: H(), body: JSON.stringify({
    clientId: 'ladder-client', enabled: true, protocol: 'openid-connect',
    publicClient: false, serviceAccountsEnabled: true,
    clientAuthenticatorType: 'client-jwt'
  })
});
console.log('bare create:', r.status);
const list = await (await fetch(base + '/admin/realms/master/clients?client_id=ladder-client', { headers: H() })).json();
const id = list[0].id;

const putAttrs = async (attrs) => {
  const res = await fetch(base + '/admin/realms/master/clients/' + id, {
    method: 'PUT', headers: H(), body: JSON.stringify({ clientId: 'ladder-client', attributes: attrs })
  });
  return res.status + ' ' + (await res.text()).slice(0, 100);
};

console.log('a) foo:bar     →', await putAttrs({ 'x-foo': 'bar' }));
console.log('b) stx=true    →', await putAttrs({ 'standard.token.exchange.enabled': 'true' }));
console.log('c) use.jwks    →', await putAttrs({ 'use.jwks.string': 'true' }));
console.log('d) signing.alg →', await putAttrs({ 'token.endpoint.auth.signing.alg': 'RS256' }));
console.log('e) empty jwks  →', await putAttrs({ 'jwks.string': '{"keys":[]}' }));
console.log('f) pkce        →', await putAttrs({ 'pkce.code.challenge.method': 'S256' }));
