Creating Installers
Overview
Create professional installers for your Wails application on all platforms.

Platform Installers
Windows
macOS
Linux
NSIS Installer
Terminal window
# Install NSIS
# Download from: https://nsis.sourceforge.io/

# Create installer script (installer.nsi)
makensis installer.nsi

installer.nsi:

!define APPNAME "MyApp"
!define VERSION "1.0.0"

Name "${APPNAME}"
OutFile "MyApp-Setup.exe"
InstallDir "$PROGRAMFILES\${APPNAME}"

Section "Install"
    SetOutPath "$INSTDIR"
    File "build\bin\myapp.exe"
    CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\myapp.exe"
SectionEnd

WiX Toolset
Alternative for MSI installers.

Automated Packaging
Using GoReleaser
.goreleaser.yml
project_name: myapp

builds:
  - binary: myapp
    goos:
      - windows
      - darwin
      - linux
    goarch:
      - amd64
      - arm64

archives:
  - format: zip
    name_template: "{{ .ProjectName }}_{{ .Version }}_{{ .Os }}_{{ .Arch }}"

nfpms:
  - formats:
      - deb
      - rpm
    vendor: Your Company
    homepage: https://example.com
    description: My Application

Best Practices
✅ Do
Code sign on all platforms
Include version information
Create uninstallers
Test installation process
Provide clear documentation
❌ Don’t
Don’t skip code signing
Don’t forget file associations
Don’t hardcode paths
Don’t skip testing
Next Steps
In-App Updater - Add self-updates to your app
Cross-Platform Building - Build for multiple platforms