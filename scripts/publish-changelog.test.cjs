const assert = require('node:assert/strict')
const test = require('node:test')
const { renderChangelog, releaseNotes } = require('./publish-changelog.cjs')

test('renders each release from its own previous tag, newest first', () => {
  const ranges = []
  const result = renderChangelog('Mobile', ['mobile-v1.0.0', 'mobile-v1.1.0'], range => {
    ranges.push(range)
    return [`change for ${range}`]
  })
  assert.deepEqual(ranges, ['mobile-v1.0.0', 'mobile-v1.0.0..mobile-v1.1.0'])
  assert.ok(result.indexOf('## mobile-v1.1.0') < result.indexOf('## mobile-v1.0.0'))
  assert.match(result, /Changes since mobile-v1\.0\.0\./)
})

test('replaces only the old generated commit list and keeps curated notes', () => {
  const generated = '> [!WARNING]\nBeta release.\n\n## What\'s Changed\n\nChanges since v1.2.0:\n\n- feat(ui): example (abcdef0)'
  const desktop = releaseNotes(generated, 'v1.3.0', 'v1.2.0')
  assert.ok(!desktop.includes('- feat(ui)'))
  assert.match(desktop, /Beta release\./)
  assert.match(desktop, /## What's Changed\n\nChanges since v1\.2\.0:\n\nFull changelog: \[CHANGELOG\.md\]/)
  const mobile = releaseNotes('# Airmedy Mobile — What\'s New\n\nChanges since mobile-v1.2.1.\n\n- Better lyrics.', 'mobile-v1.3.0', 'mobile-v1.2.1')
  assert.match(mobile, /- Better lyrics\./)
  assert.match(mobile, /## What's Changed\n\nChanges since mobile-v1\.2\.1:/)
  assert.match(mobile, /\[mobile\/CHANGELOG\.md\]/)
  assert.equal(releaseNotes(mobile, 'mobile-v1.3.0', 'mobile-v1.2.1'), mobile)
  assert.equal(releaseNotes('## What\'s Changed\n\nAll commits:\n\nNo major changes (features or fixes) in this release.', 'v1.0.0', 'v0.1.14'), "## What's Changed\n\nChanges since v0.1.14:\n\nFull changelog: [CHANGELOG.md](https://github.com/misa198/airmedy/blob/master/CHANGELOG.md)")
})
