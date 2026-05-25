Auto-Updates
Automatic Updates
Wails v3 provides a built-in updater system that supports automatic update checking, downloading, and installation. The updater includes support for binary delta updates (patches) for minimal download sizes.

Automatic Checking

Configure periodic update checks in the background

Delta Updates

Download only what changed with bsdiff patches

Cross-Platform

Works on macOS, Windows, and Linux

Secure

SHA256 checksums and optional signature verification

Quick Start
Add the updater service to your application:

main.go
package main

import (
    "github.com/wailsapp/wails/v3/pkg/application"
    "time"
)

func main() {
    // Create the updater service
    updater, err := application.CreateUpdaterService(
        "1.0.0", // Current version
        application.WithUpdateURL("https://updates.example.com/myapp/"),
        application.WithCheckInterval(24 * time.Hour),
    )
    if err != nil {
        panic(err)
    }

    app := application.New(application.Options{
        Name: "MyApp",
        Services: []application.Service{
            application.NewService(updater),
        },
    })

    // ... rest of your app
    app.Run()
}

Then use it from your frontend:

App.tsx
import { updater } from './bindings/myapp';

async function checkForUpdates() {
  const update = await updater.CheckForUpdate();

  if (update) {
    console.log(`New version available: ${update.version}`);
    console.log(`Release notes: ${update.releaseNotes}`);

    // Download and install
    await updater.DownloadAndApply();
  }
}

Configuration Options
The updater supports various configuration options:

updater, err := application.CreateUpdaterService(
    "1.0.0",
    // Required: URL where update manifests are hosted
    application.WithUpdateURL("https://updates.example.com/myapp/"),

    // Optional: Check for updates automatically every 24 hours
    application.WithCheckInterval(24 * time.Hour),

    // Optional: Allow pre-release versions
    application.WithAllowPrerelease(true),

    // Optional: Update channel (stable, beta, canary)
    application.WithChannel("stable"),

    // Optional: Require signed updates
    application.WithRequireSignature(true),
    application.WithPublicKey("your-ed25519-public-key"),
)

Update Manifest Format
Host an update.json file on your server:

update.json
{
  "version": "1.2.0",
  "release_date": "2025-01-15T00:00:00Z",
  "release_notes": "## What's New\n\n- Feature A\n- Bug fix B",
  "platforms": {
    "macos-arm64": {
      "url": "https://updates.example.com/myapp/myapp-1.2.0-macos-arm64.tar.gz",
      "size": 12582912,
      "checksum": "sha256:abc123...",
      "patches": [
        {
          "from": "1.1.0",
          "url": "https://updates.example.com/myapp/patches/1.1.0-to-1.2.0-macos-arm64.patch",
          "size": 14336,
          "checksum": "sha256:def456..."
        }
      ]
    },
    "macos-amd64": {
      "url": "https://updates.example.com/myapp/myapp-1.2.0-macos-amd64.tar.gz",
      "size": 13107200,
      "checksum": "sha256:789xyz..."
    },
    "windows-amd64": {
      "url": "https://updates.example.com/myapp/myapp-1.2.0-windows-amd64.zip",
      "size": 14680064,
      "checksum": "sha256:ghi789..."
    },
    "linux-amd64": {
      "url": "https://updates.example.com/myapp/myapp-1.2.0-linux-amd64.tar.gz",
      "size": 11534336,
      "checksum": "sha256:jkl012..."
    }
  },
  "minimum_version": "1.0.0",
  "mandatory": false
}

Platform Keys
Platform	Key
macOS (Apple Silicon)	macos-arm64
macOS (Intel)	macos-amd64
Windows (64-bit)	windows-amd64
Linux (64-bit)	linux-amd64
Linux (ARM64)	linux-arm64
Frontend API
The updater exposes methods that are automatically bound to your frontend:

TypeScript Types
interface UpdateInfo {
  version: string;
  releaseDate: Date;
  releaseNotes: string;
  size: number;
  patchSize?: number;
  mandatory: boolean;
  hasPatch: boolean;
}

interface Updater {
  // Get the current application version
  GetCurrentVersion(): string;

  // Check if an update is available
  CheckForUpdate(): Promise<UpdateInfo | null>;

  // Download the update (emits progress events)
  DownloadUpdate(): Promise<void>;

  // Apply the downloaded update (restarts app)
  ApplyUpdate(): Promise<void>;

  // Download and apply in one call
  DownloadAndApply(): Promise<void>;

  // Get current state: idle, checking, available, downloading, ready, installing, error
  GetState(): string;

  // Get the available update info
  GetUpdateInfo(): UpdateInfo | null;

  // Get the last error message
  GetLastError(): string;

  // Reset the updater state
  Reset(): void;
}

Progress Events
Listen for download progress events:

import { Events } from '@wailsio/runtime';

Events.On('updater:progress', (data) => {
  console.log(`Downloaded: ${data.downloaded} / ${data.total}`);
  console.log(`Progress: ${data.percentage.toFixed(1)}%`);
  console.log(`Speed: ${(data.bytesPerSecond / 1024 / 1024).toFixed(2)} MB/s`);
});

Complete Example
Here’s a complete example with a React component:

UpdateChecker.tsx
import { useState, useEffect } from 'react';
import { updater } from './bindings/myapp';
import { Events } from '@wailsio/runtime';

interface Progress {
  downloaded: number;
  total: number;
  percentage: number;
  bytesPerSecond: number;
}

export function UpdateChecker() {
  const [checking, setChecking] = useState(false);
  const [updateInfo, setUpdateInfo] = useState<any>(null);
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    // Listen for progress events
    const cleanup = Events.On('updater:progress', (data: Progress) => {
      setProgress(data);
    });

    // Check for updates on mount
    checkForUpdates();

    return () => cleanup();
  }, []);

  async function checkForUpdates() {
    setChecking(true);
    setError(null);

    try {
      const info = await updater.CheckForUpdate();
      setUpdateInfo(info);
    } catch (err) {
      setError(err.message);
    } finally {
      setChecking(false);
    }
  }

  async function downloadAndInstall() {
    setDownloading(true);
    setError(null);

    try {
      await updater.DownloadAndApply();
      // App will restart automatically
    } catch (err) {
      setError(err.message);
      setDownloading(false);
    }
  }

  if (checking) {
    return <div>Checking for updates...</div>;
  }

  if (error) {
    return (
      <div>
        <p>Error: {error}</p>
        <button onClick={checkForUpdates}>Retry</button>
      </div>
    );
  }

  if (!updateInfo) {
    return (
      <div>
        <p>You're up to date! (v{updater.GetCurrentVersion()})</p>
        <button onClick={checkForUpdates}>Check Again</button>
      </div>
    );
  }

  if (downloading) {
    return (
      <div>
        <p>Downloading update...</p>
        {progress && (
          <div>
            <progress value={progress.percentage} max={100} />
            <p>{progress.percentage.toFixed(1)}%</p>
            <p>{(progress.bytesPerSecond / 1024 / 1024).toFixed(2)} MB/s</p>
          </div>
        )}
      </div>
    );
  }

  return (
    <div>
      <h3>Update Available!</h3>
      <p>Version {updateInfo.version} is available</p>
      <p>Size: {updateInfo.hasPatch
        ? `${(updateInfo.patchSize / 1024).toFixed(0)} KB (patch)`
        : `${(updateInfo.size / 1024 / 1024).toFixed(1)} MB`}
      </p>
      <div dangerouslySetInnerHTML={{ __html: updateInfo.releaseNotes }} />
      <button onClick={downloadAndInstall}>Download & Install</button>
      <button onClick={() => setUpdateInfo(null)}>Skip</button>
    </div>
  );
}

Update Strategies
Check on Startup
func (a *App) OnStartup(ctx context.Context) {
    // Check for updates after a short delay
    go func() {
        time.Sleep(5 * time.Second)
        info, err := a.updater.CheckForUpdate()
        if err == nil && info != nil {
            // Emit event to frontend
            application.Get().EmitEvent("update-available", info)
        }
    }()
}

Background Checking
Configure automatic background checks:

updater, _ := application.CreateUpdaterService(
    "1.0.0",
    application.WithUpdateURL("https://updates.example.com/myapp/"),
    application.WithCheckInterval(6 * time.Hour), // Check every 6 hours
)

Manual Check Menu Item
menu := application.NewMenu()
menu.Add("Check for Updates...").OnClick(func(ctx *application.Context) {
    info, err := updater.CheckForUpdate()
    if err != nil {
        application.InfoDialog().SetMessage("Error checking for updates").Show()
        return
    }
    if info == nil {
        application.InfoDialog().SetMessage("You're up to date!").Show()
        return
    }
    // Show update dialog...
})

Delta Updates (Patches)
Delta updates (patches) allow users to download only the changes between versions, dramatically reducing download sizes.

How It Works
When building a new version, generate patches from previous versions
Host patches alongside full updates on your server
The updater automatically downloads patches when available
If patching fails, it falls back to the full download
Generating Patches
Patches are generated using the bsdiff algorithm. You’ll need the bsdiff tool:

Terminal window
# Install bsdiff (macOS)
brew install bsdiff

# Install bsdiff (Ubuntu/Debian)
sudo apt-get install bsdiff

# Generate a patch
bsdiff old-binary new-binary patch.bsdiff

Patch File Naming
Organize your patches in your update directory:

updates/
├── update.json
├── myapp-1.2.0-macos-arm64.tar.gz
├── myapp-1.2.0-windows-amd64.zip
└── patches/
    ├── 1.0.0-to-1.2.0-macos-arm64.patch
    ├── 1.1.0-to-1.2.0-macos-arm64.patch
    ├── 1.0.0-to-1.2.0-windows-amd64.patch
    └── 1.1.0-to-1.2.0-windows-amd64.patch

Tip

Keep patches from the last few versions. Users on very old versions will automatically download the full update.

Hosting Updates
Static File Hosting
Updates can be hosted on any static file server:

Amazon S3 / Cloudflare R2
Google Cloud Storage
GitHub Releases
Any CDN or web server
Example S3 bucket structure:

s3://my-updates-bucket/myapp/
├── stable/
│   ├── update.json
│   ├── myapp-1.2.0-macos-arm64.tar.gz
│   └── patches/
│       └── 1.1.0-to-1.2.0-macos-arm64.patch
└── beta/
    └── update.json

CORS Configuration
If hosting on a different domain, configure CORS:

{
  "CORSRules": [
    {
      "AllowedOrigins": ["*"],
      "AllowedMethods": ["GET"],
      "AllowedHeaders": ["*"]
    }
  ]
}

Security
Checksum Verification
All downloads are verified against SHA256 checksums in the manifest:

{
  "checksum": "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}

Signature Verification
For additional security, enable signature verification:

Generate a key pair:

Terminal window
# Generate Ed25519 key pair
openssl genpkey -algorithm Ed25519 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem

Sign your update manifest:

Terminal window
openssl pkeyutl -sign -inkey private.pem -in update.json -out update.json.sig

Configure the updater:

updater, _ := application.CreateUpdaterService(
    "1.0.0",
    application.WithUpdateURL("https://updates.example.com/myapp/"),
    application.WithRequireSignature(true),
    application.WithPublicKey("MCowBQYDK2VwAyEA..."), // Base64-encoded public key
)

Best Practices
Do
Test updates thoroughly before deploying
Keep previous versions available for rollback
Show release notes to users
Allow users to skip non-mandatory updates
Use HTTPS for all downloads
Verify checksums before applying updates
Handle errors gracefully
Don’t
Force immediate restarts without warning
Skip checksum verification
Interrupt users during important work
Delete the previous version immediately
Ignore update failures
Troubleshooting
Update Not Found
Verify the manifest URL is correct
Check the platform key matches (e.g., macos-arm64)
Ensure the version in the manifest is newer
Download Fails
Check network connectivity
Verify the download URL is accessible
Check CORS configuration if cross-origin
Patch Fails
The updater automatically falls back to full download
Ensure bspatch is available on the system
Verify the patch checksum is correct
Application Won’t Restart
On macOS, ensure the app is properly code-signed
On Windows, check for file locks
On Linux, verify file permissions
Next Steps
Code Signing - Sign your updates
Creating Installers - Package your application
CI/CD Integration - Automate your release process