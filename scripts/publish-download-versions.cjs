// Runs inside actions/github-script; all reads use the same branch snapshot.
module.exports = async function publishDownloadVersions({ github, context, desktop, mobile }) {
  if (!desktop && !mobile) throw new Error('Select desktop or mobile')
  const { owner, repo } = context.repo
  const api = github.rest
  const configPath = 'public/static/config.json'
  const manifestPath = 'releases/latest-release.json'

  for (let attempt = 0; attempt < 3; attempt++) {
    const { data: ref } = await api.git.getRef({ owner, repo, ref: 'heads/master' })
    const head = ref.object.sha
    const read = async path => {
      const { data } = await api.repos.getContent({ owner, repo, path, ref: head })
      return Buffer.from(data.content, 'base64').toString('utf8')
    }
    const configText = await read(configPath)
    const manifestText = await read(manifestPath)
    const config = JSON.parse(configText)
    const manifest = JSON.parse(manifestText)
    const updates = []
    if (desktop) {
      const source = await read('build/config.yml')
      const info = source.match(/^info:\s*\n([\s\S]*?)(?=^\S|$(?![\s\S]))/m)?.[1]
      const version = info?.match(/^\s+version:\s*"(\d+\.\d+\.\d+)"/m)?.[1]
      if (!version) throw new Error('Missing desktop version')
      updates.push({ key: 'desktop', configKey: 'downloadVersion', version, tag: `v${version}` })
    }
    if (mobile) {
      const source = await read('mobile/androidApp/build.gradle.kts')
      const version = source.match(/^\s*versionName = "(\d+\.\d+\.\d+)"/m)?.[1]
      if (!version) throw new Error('Missing Android version')
      updates.push({ key: 'android', configKey: 'mobileDownloadVersion', version, tag: `mobile-v${version}` })
    }
    for (const update of updates) {
      const { data: release } = await api.repos.getReleaseByTag({ owner, repo, tag: update.tag })
      if (release.draft || !release.published_at || release.tag_name !== update.tag) {
        throw new Error(`Release is not published: ${update.tag}`)
      }
      if (update.key === 'android' && !release.assets.some(asset => asset.state === 'uploaded' && asset.name.endsWith('.apk'))) {
        throw new Error(`Release has no uploaded APK: ${update.tag}`)
      }
      config[update.configKey] = update.version
      manifest[update.key] = { ...manifest[update.key], tag: update.tag }
    }
    const files = [
      [configPath, configText, config],
      [manifestPath, manifestText, manifest],
    ].filter(([, old, value]) => JSON.stringify(JSON.parse(old)) !== JSON.stringify(value))
    if (!files.length) return
    const { data: parent } = await api.git.getCommit({ owner, repo, commit_sha: head })
    const treeEntries = []
    for (const [path, , value] of files) {
      const { data: blob } = await api.git.createBlob({
        owner, repo, content: `${JSON.stringify(value, null, 2)}\n`, encoding: 'utf-8',
      })
      treeEntries.push({ path, mode: '100644', type: 'blob', sha: blob.sha })
    }
    const { data: tree } = await api.git.createTree({ owner, repo, base_tree: parent.tree.sha, tree: treeEntries })
    const { data: commit } = await api.git.createCommit({
      owner, repo, message: 'chore(app): update download versions and release manifest',
      tree: tree.sha, parents: [head],
    })
    try {
      await api.git.updateRef({ owner, repo, ref: 'heads/master', sha: commit.sha, force: false })
      return
    } catch (error) {
      if (attempt === 2 || ![409, 422].includes(error.status)) throw error
      const { data: current } = await api.git.getRef({ owner, repo, ref: 'heads/master' })
      if (current.object.sha === head) throw error
    }
  }
}
