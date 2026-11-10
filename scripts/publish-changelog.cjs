const fs = require('node:fs')
const { execFileSync } = require('node:child_process')

const repository = 'https://github.com/misa198/airmedy'

function renderChangelog(title, tags, changes) {
  const sections = tags.map((tag, index) => {
    const previous = tags[index - 1]
    const commits = changes(previous ? `${previous}..${tag}` : tag)
    return `## ${tag}\n\n${previous ? `Changes since ${previous}.` : 'Initial release.'}\n\n${commits.length ? commits.map(commit => `- ${commit}`).join('\n') : 'No changes in this release.'}`
  })
  return `# ${title} changelog\n\n${sections.reverse().join('\n\n')}\n`
}

function releaseNotes(body, tag, previous) {
  const mobile = tag.startsWith('mobile-')
  const path = mobile ? 'mobile/CHANGELOG.md' : 'CHANGELOG.md'
  const heading = `## What's Changed\n\nChanges since ${previous || 'repository start'}:`
  const link = `Full changelog: [${path}](${repository}/blob/master/${path})`
  const withoutGeneratedList = body.replace(/\n*## What's Changed\n\n(?:Changes since [^\n]+|All commits):\n\n(?:(?:- (?:feat|fix)[^\n]+\n?)+|No major changes \(features or fixes\) in this release\.)\s*$/, '')
  const withoutOldLink = withoutGeneratedList.replace(/\n*Full changelog(?::| at) \[[^\]]+\]\([^\n]+\)\.?\s*$/, '').trim()
  let notes = withoutOldLink
  if (/## What's Changed\n\n(?:Changes since [^\n]+:\n\n)?/.test(notes)) {
    notes = notes.replace(/## What's Changed\n\n(?:Changes since [^\n]+:\n\n)?/, heading + '\n\n')
  } else if (/^Changes since [^\n]+[.:]$/m.test(notes)) {
    notes = notes.replace(/^Changes since [^\n]+[.:]$/m, heading)
  } else {
    notes = `${notes}${notes ? '\n\n' : ''}${heading}`
  }
  return `${notes.trim()}\n\n${link}`
}

function git(...args) {
  return execFileSync('git', args, { encoding: 'utf8' }).trim()
}

function changelog(prefix, title, paths) {
  // shortcut: two early desktop tags diverge; use reachable commits until exact release reconstruction is needed.
  const tags = git('tag', '--sort=creatordate', '--list', `${prefix}v*`).split('\n').filter(Boolean)
  return renderChangelog(title, tags, range => {
    const output = git('log', range, '--no-merges', '--format=%s (%h)', '--', ...paths)
    return output ? output.split('\n') : []
  })
}

function generateFiles() {
  return {
    'CHANGELOG.md': changelog('', 'Desktop', ['.', ':(exclude)mobile/**', ':(exclude)CHANGELOG.md']),
    'mobile/CHANGELOG.md': changelog('mobile-', 'Mobile', ['mobile/', 'scripts/build-ffmpeg-android.sh', 'scripts/bump-mobile-version.sh', ':(exclude)mobile/CHANGELOG.md']),
  }
}

async function publish({ github, context, tag }) {
  const allTags = git('tag', '--list').split('\n').filter(name => /^(?:mobile-)?v\d+\.\d+\.\d+$/.test(name))
  if (tag !== 'all' && !allTags.includes(tag)) {
    throw new Error(`Unknown release tag: ${tag}`)
  }

  const { owner, repo } = context.repo
  const files = generateFiles()
  const changed = Object.entries(files).filter(([path, content]) => !fs.existsSync(path) || fs.readFileSync(path, 'utf8') !== content)

  if (changed.length) {
    const ref = await github.rest.git.getRef({ owner, repo, ref: 'heads/master' })
    const parent = await github.rest.git.getCommit({ owner, repo, commit_sha: ref.data.object.sha })
    const entries = []
    for (const [path, content] of changed) {
      const blob = await github.rest.git.createBlob({ owner, repo, content, encoding: 'utf-8' })
      entries.push({ path, mode: '100644', type: 'blob', sha: blob.data.sha })
    }
    const tree = await github.rest.git.createTree({ owner, repo, base_tree: parent.data.tree.sha, tree: entries })
    const commit = await github.rest.git.createCommit({
      owner, repo, message: `docs: update changelogs through ${tag}`,
      tree: tree.data.sha, parents: [ref.data.object.sha],
    })
    await github.rest.git.updateRef({ owner, repo, ref: 'heads/master', sha: commit.data.sha, force: false })
  }

  for (const releaseTag of tag === 'all' ? allTags : [tag]) {
    let release
    try {
      release = await github.rest.repos.getReleaseByTag({ owner, repo, tag: releaseTag })
    } catch (error) {
      if (tag === 'all' && error.status === 404) continue
      throw error
    }
    const series = git('tag', '--sort=creatordate', '--list', releaseTag.startsWith('mobile-') ? 'mobile-v*' : 'v*').split('\n')
    const previous = series[series.indexOf(releaseTag) - 1]
    const body = releaseNotes(release.data.body || '', releaseTag, previous)
    if (body !== release.data.body) {
      await github.rest.repos.updateRelease({ owner, repo, release_id: release.data.id, body })
    }
  }
}

module.exports = publish
module.exports.renderChangelog = renderChangelog
module.exports.releaseNotes = releaseNotes

if (require.main === module) {
  for (const [path, content] of Object.entries(generateFiles())) fs.writeFileSync(path, content)
}
