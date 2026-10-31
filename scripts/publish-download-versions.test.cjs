const { test } = require('node:test')
const assert = require('node:assert/strict')
const publish = require('./publish-download-versions.cjs')

function fixture() {
  const state = {
    files: {
      'public/static/config.json': JSON.stringify({ downloadVersion: '1.0.0', mobileDownloadVersion: '1.0.0', other: true }),
      'releases/latest-release.json': JSON.stringify({ desktop: { tag: 'v1.0.0' }, android: { tag: 'mobile-v1.0.0' } }),
      'build/config.yml': 'version: "3"\ninfo:\n  version: "1.3.3" # app version\ndev_mode:\n  root_path: .\n',
      'mobile/androidApp/build.gradle.kts': '    versionName = "1.2.1"\n',
    },
    head: 'head-1', commits: [], blobs: {}, tree: [], releases: [], conflict: false, invalid: null,
  }
  const github = { rest: {
    repos: {
      getContent: async ({ path, ref }) => {
        assert.equal(ref, state.head)
        return { data: { content: Buffer.from(state.files[path]).toString('base64') } }
      },
      getReleaseByTag: async ({ tag }) => {
        state.releases.push(tag)
        if (state.invalid === 'missing') throw Object.assign(new Error('Not found'), { status: 404 })
        return { data: { tag_name: tag, draft: state.invalid === 'draft', published_at: '2026-10-31',
          assets: state.invalid === 'apk' ? [] : [{ name: 'app.apk', state: 'uploaded' }] } }
      },
    },
    git: {
      getRef: async () => ({ data: { object: { sha: state.head } } }),
      getCommit: async () => ({ data: { tree: { sha: 'base-tree' } } }),
      createBlob: async ({ content }) => {
        const sha = `blob-${Object.keys(state.blobs).length}`
        state.blobs[sha] = content
        return { data: { sha } }
      },
      createTree: async ({ tree }) => { state.tree = tree; return { data: { sha: 'new-tree' } } },
      createCommit: async args => { state.commits.push(args); return { data: { sha: 'new-commit' } } },
      updateRef: async ({ force }) => {
        assert.equal(force, false)
        if (state.conflict) {
          state.conflict = false
          state.head = 'head-2'
          const config = JSON.parse(state.files['public/static/config.json'])
          config.other = 'concurrent change'
          state.files['public/static/config.json'] = JSON.stringify(config)
          const manifest = JSON.parse(state.files['releases/latest-release.json'])
          manifest.desktop.tag = 'v1.1.0'
          state.files['releases/latest-release.json'] = JSON.stringify(manifest)
          throw Object.assign(new Error('Conflict'), { status: 422 })
        }
        for (const entry of state.tree) state.files[entry.path] = state.blobs[entry.sha]
      },
    },
  } }
  return { state, run: options => publish({ github, context: { repo: { owner: 'misa198', repo: 'airmedy' } }, ...options }) }
}

for (const options of [{ desktop: true }, { mobile: true }, { desktop: true, mobile: true }]) {
  test(`updates selected platforms atomically: ${JSON.stringify(options)}`, async () => {
    const { state, run } = fixture()
    await run(options)
    assert.equal(state.commits.length, 1)
    assert.equal(state.tree.length, 2)
    const config = JSON.parse(state.files['public/static/config.json'])
    const manifest = JSON.parse(state.files['releases/latest-release.json'])
    assert.equal(config.downloadVersion, options.desktop ? '1.3.3' : '1.0.0')
    assert.equal(config.mobileDownloadVersion, options.mobile ? '1.2.1' : '1.0.0')
    assert.equal(manifest.desktop.tag, options.desktop ? 'v1.3.3' : 'v1.0.0')
    assert.equal(manifest.android.tag, options.mobile ? 'mobile-v1.2.1' : 'mobile-v1.0.0')
    assert.equal(config.other, true)
    await run(options)
    assert.equal(state.commits.length, 1, 'unchanged values must not create a commit')
  })
}

for (const invalid of ['missing', 'draft', 'apk']) {
  test(`does not commit either file for invalid release: ${invalid}`, async () => {
    const { state, run } = fixture()
    state.invalid = invalid
    const before = { ...state.files }
    await assert.rejects(run({ desktop: true, mobile: true }))
    assert.equal(state.commits.length, 0)
    assert.deepEqual(state.files, before)
  })
}

test('retries from current HEAD and preserves concurrent changes', async () => {
  const { state, run } = fixture()
  state.conflict = true
  await run({ mobile: true })
  assert.equal(state.commits.length, 2)
  assert.deepEqual(state.commits[1].parents, ['head-2'])
  assert.equal(JSON.parse(state.files['public/static/config.json']).other, 'concurrent change')
  assert.equal(JSON.parse(state.files['releases/latest-release.json']).desktop.tag, 'v1.1.0')
})

test('requires a platform selection', async () => {
  const { run } = fixture()
  await assert.rejects(run({}), /Select desktop or mobile/)
})
