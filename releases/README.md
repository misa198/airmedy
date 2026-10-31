# Release discovery

`latest-release.json` is the public pointer to the desktop and Android releases
currently offered for download. It is independent of landing-page hosting:

```text
https://raw.githubusercontent.com/misa198/airmedy/master/releases/latest-release.json
```

The `desktop.tag` value uses `vX.Y.Z` and covers Windows, macOS and Linux.
The `android.tag` value uses `mobile-vX.Y.Z`. Clients fetch release notes and
assets from the GitHub Releases API by that exact tag, so editing release notes
does not require regenerating this file. Published prereleases are supported.

The Mobile Release workflow attaches the signed APK and `SHA256SUMS`, which
contains SHA-256 hashes of the staged APKs under their published filenames.
After downloading both assets into the same directory, verify them with
`shasum -a 256 -c SHA256SUMS` (or `sha256sum -c SHA256SUMS` on Linux).

## Publishing a download version

After publishing a release, run **Bump Landing Download Version**
(`landing-download-version.yml`) and select desktop, mobile, or both. The workflow
reads the selected versions from desktop build configuration and Android Gradle
configuration, verifies their published GitHub releases, and requires an uploaded
APK for Android. It updates the selected manifest entries and landing download
versions in one commit on `master`; unselected entries are preserved.

Publishing a release alone does not change these pointers. If validation fails,
neither file is committed. Repeating the workflow with unchanged values creates
no commit. Branch conflicts are retried from fresh source files up to three
attempts, without force pushing. The existing Release GitHub App must have
Contents write permission, as required by the workflow's previous commit step.

Android's `prod` flavor checks the pointer once per app Activity/ViewModel
session; configuration changes and returning from background do not repeat the
check. Only a newer, previously unannounced version fetches release details.
Notes render as Markdown, including network images and HTML `<img>` tags.
The Update action opens the release page;
the app does not download or install APKs itself. The `dev` flavor does not check.

Android persists each announced version before exposing its dialog. It remains
suppressed after dismissal, restarting, or editing/re-publishing the same version.
Clearing app storage resets this history; Android system backup may restore it.
There is an intentional at-most-once tradeoff: process death between persistence
and rendering can suppress a notice the user did not see. Network, parsing, and
persistence failures skip the dialog and allow retry at the next session.

Verification: `node --test scripts/publish-download-versions.test.cjs`, shared
update tests, Android update host tests, and `AppUpdateDialogTest`. The workflow
runs its publication tests before committing; Mobile CI runs Android update host
tests. Workflow tests use a fake GitHub API and never publish a release or commit.
