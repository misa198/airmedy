import AppKit

var showClicks = 0
@_cdecl("goTrayPillShow") func showAirmedy() { showClicks += 1 }
@_cdecl("goTrayPanelLostFocus") func lostFocus() {}
@_cdecl("goTrayPanelEscape") func escape() {}
var favoriteClicks = 0
@_cdecl("goTrayPanelFavorite") func favorite() { favoriteClicks += 1 }
@_cdecl("goTrayPanelTick") func tick() {}
var controlActions: [Int32] = []
@_cdecl("goTrayPanelControl") func control(_ action: Int32) { controlActions.append(action) }
var seekPosition = -1.0
var seekTrack = ""
@_cdecl("goTrayPanelSeek") func seek(_ position: Double, _ trackID: UnsafePointer<CChar>) {
    seekPosition = position
    seekTrack = String(cString: trackID)
}

@_silgen_name("TrayPanelShow") func show(_ x: Int32, _ y: Int32)
@_silgen_name("TrayPanelHide") func hide()
@_silgen_name("TrayPanelIsVisible") func isVisible() -> Bool
@_silgen_name("TrayPanelDestroy") func destroy()
@_silgen_name("TrayPanelSetTheme") func setTheme(_ theme: UnsafePointer<CChar>)
@_silgen_name("TrayPanelUpdate") func update(_ title: UnsafePointer<CChar>, _ artist: UnsafePointer<CChar>, _ artworkPath: UnsafePointer<CChar>, _ hasTrack: Bool, _ favorite: Bool, _ favoriteLabel: UnsafePointer<CChar>)
@_silgen_name("TrayPanelSetProgress") func progress(_ position: Double, _ duration: Double, _ trackID: UnsafePointer<CChar>, _ label: UnsafePointer<CChar>)
@_silgen_name("TrayPanelSetIndicator") func indicator(_ show: Bool)
@_silgen_name("TrayPanelSetControls") func controls(_ playing: Bool, _ shuffle: Bool, _ repeatMode: Int32, _ shuffleLabel: UnsafePointer<CChar>, _ previousLabel: UnsafePointer<CChar>, _ playLabel: UnsafePointer<CChar>, _ nextLabel: UnsafePointer<CChar>, _ repeatLabel: UnsafePointer<CChar>)

let app = NSApplication.shared
func colorHex(_ color: NSColor?, _ appearance: NSAppearance.Name) -> Int {
    var result = -1
    NSAppearance(named: appearance)!.performAsCurrentDrawingAppearance {
        guard let color = color?.usingColorSpace(.sRGB) else { return }
        result = (Int((color.redComponent * 255).rounded()) << 16)
            | (Int((color.greenComponent * 255).rounded()) << 8)
            | Int((color.blueComponent * 255).rounded())
    }
    return result
}
"dark".withCString(setTheme)
show(100, 100)
defer { hide(); destroy() }

guard let panel = app.windows.compactMap({ $0 as? NSPanel }).first else {
    fatalError("tray panel missing")
}

precondition(isVisible())
if !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion {
    guard let layer = panel.contentView?.subviews.first?.layer else { fatalError("content layer missing") }
    precondition(layer.animation(forKey: "visibility") != nil)
    let fade = layer.animation(forKey: "visibilityOpacity") as? CABasicAnimation
    precondition(fade?.fromValue as? Float == 0 && fade?.toValue as? Int == 1)
    RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.1))
    if let visible = layer.presentation() {
        let top = visible.convert(CGPoint(x: visible.bounds.midX, y: visible.bounds.maxY), to: visible.superlayer)
        precondition(abs(top.x - 155) < 0.5 && abs(top.y - 170) < 0.5)
    }
}
hide()
precondition(!isVisible())
if !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion {
    let fade = panel.contentView?.subviews.first?.layer?.animation(forKey: "visibilityOpacity") as? CABasicAnimation
    precondition(fade?.toValue as? Int == 0 && fade?.duration == 0.24)
}
show(100, 100)
precondition(isVisible())
RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.35))
precondition(panel.isVisible && isVisible())
hide()
RunLoop.current.run(until: Date(timeIntervalSinceNow: 0.35))
precondition(!panel.isVisible && !isVisible())
show(100, 100)
precondition(panel.frame.size == NSSize(width: 310, height: 170))
precondition(panel.styleMask.contains(.nonactivatingPanel))
precondition(panel.styleMask.contains(.titled))
precondition(panel.titleVisibility == .hidden)

guard let content = panel.contentView?.subviews.first,
      let artwork = content.subviews.compactMap({ $0 as? NSImageView }).first,
      content.subviews.count >= 4,
      let title = content.subviews[1].subviews.first?.subviews.first as? NSTextField,
      let artist = content.subviews[2].subviews.first?.subviews.first as? NSTextField,
      let star = content.subviews.compactMap({ $0 as? NSButton }).first else {
    fatalError("tray panel content missing")
}
for view in [artwork, content.subviews[1], content.subviews[2]] {
    guard let gesture = view.gestureRecognizers.first as? NSClickGestureRecognizer,
          let action = gesture.action else { fatalError("show action missing") }
    NSApp.sendAction(action, to: gesture.target, from: gesture)
}
precondition(showClicks == 3)
let times = content.subviews.compactMap { $0 as? NSTextField }
let playbackButtons = content.subviews.compactMap { $0 as? NSButton }.filter { (0..<5).contains($0.tag) && $0 !== star }
precondition(playbackButtons.count == 5)
let dots = [0, 4].map { index in
    content.subviews.first { $0.accessibilityIdentifier() == "tray-indicator-\(index)" }!
}
let buttonFrames = playbackButtons.map(\.frame)
indicator(false)
"Control".withCString { controls(true, true, 2, $0, $0, $0, $0, $0) }
precondition(dots.allSatisfy(\.isHidden))
indicator(true)
precondition(dots.allSatisfy { !$0.isHidden })
precondition(playbackButtons.map(\.frame) == buttonFrames)
precondition(zip(dots, [playbackButtons[0], playbackButtons[4]]).allSatisfy { $0.frame.midX == $1.frame.midX && $0.frame.maxY < $1.frame.minY })
"Control".withCString { controls(true, false, 1, $0, $0, $0, $0, $0) }
precondition(dots[0].isHidden && !dots[1].isHidden)
"Control".withCString { controls(true, true, 0, $0, $0, $0, $0, $0) }
precondition(!dots[0].isHidden && dots[1].isHidden)

"Control".withCString { label in controls(true, true, 2, label, label, label, label, label) }
for (index, button) in playbackButtons.enumerated() {
    precondition((index == 2 || button.image != nil) && button.alphaValue == 1 && colorHex(button.contentTintColor, .darkAqua) == 0xfcfcfc)
    precondition(button.tag == index && button.frame.midY == 36.75)
    if index == 2 || index == 3 { precondition(button.frame.midX - playbackButtons[index - 1].frame.midX == 55) }
}
if !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion {
    RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.1))
    let progress = (playbackButtons[2].value(forKey: "pauseProgress") as? CGFloat) ?? 0
    precondition(progress > 0 && progress < 1)
    RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.3))
}
precondition((playbackButtons[2].value(forKey: "pauseProgress") as? CGFloat) == 1)
for button in playbackButtons { button.performClick(nil) }
precondition(controlActions == [0, 1, 2, 3, 4])
"Control".withCString { label in controls(false, false, 0, label, label, label, label, label) }
precondition(colorHex(playbackButtons[0].contentTintColor, .darkAqua) == 0x9b9b9b)
precondition(colorHex(playbackButtons[4].contentTintColor, .darkAqua) == 0x9b9b9b)
precondition(colorHex(title.textColor, .darkAqua) == 0xfcfcfc && colorHex(artist.textColor, .darkAqua) == colorHex(playbackButtons[0].contentTintColor, .darkAqua))
precondition(colorHex(title.textColor, .aqua) == 0x181818 && colorHex(artist.textColor, .aqua) == colorHex(playbackButtons[0].contentTintColor, .aqua))
precondition(colorHex(playbackButtons[2].contentTintColor, .aqua) == 0x181818)
precondition(colorHex(playbackButtons[0].contentTintColor, .aqua) == 0x777777)
guard times.count == 2, let slider = content.subviews.compactMap({ $0 as? NSSlider }).first else {
    fatalError("tray progress missing")
}
precondition(times[0].frame.width == times[1].frame.width)
precondition(times.allSatisfy { colorHex($0.textColor, .darkAqua) == 0xa3a3a3 && colorHex($0.textColor, .aqua) == 0x303030 })
precondition(slider.frame.minX == times[0].frame.maxX && times[1].frame.minX == slider.frame.maxX)
precondition(content.bounds.maxY - artwork.frame.maxY == 18 && artwork.frame.minY - times[0].frame.maxY == 13.5)
precondition(!slider.isEnabled)
"track-1".withCString { id in
    "Seek".withCString { label in progress(75, 125, id, label) }
}
precondition(times[0].stringValue == "1:15" && times[1].stringValue == "2:05")
precondition(slider.isEnabled && slider.doubleValue == 75)
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 200, pixelsHigh: 20, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
NSAppearance(named: .darkAqua)!.performAsCurrentDrawingAppearance {
    (slider.cell as! NSSliderCell).drawBar(inside: NSRect(x: 0, y: 0, width: 200, height: 20), flipped: false)
}
NSGraphicsContext.restoreGraphicsState()
precondition(colorHex(bitmap.colorAt(x: 50, y: 10), .darkAqua) == 0xa3a3a3)
precondition(colorHex(bitmap.colorAt(x: 175, y: 10), .darkAqua) == 0x393939)
if !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion {
    let event = NSEvent.enterExitEvent(with: .mouseEntered, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: panel.windowNumber, context: nil, eventNumber: 0, trackingNumber: 0, userData: nil)!
    slider.mouseEntered(with: event)
    precondition((slider.value(forKey: "barHeight") as? CGFloat) == 6)
    precondition((slider.value(forKey: "colorProgress") as? CGFloat) == 0)
    RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.1))
    let intermediateColor = (slider.value(forKey: "colorProgress") as? CGFloat) ?? 0
    precondition(intermediateColor > 0 && intermediateColor < 1)
    let intermediateLabel = colorHex(times[0].textColor, .darkAqua)
    precondition(intermediateLabel > 0xa3a3a3 && intermediateLabel < 0xe6e6e6 && intermediateLabel == colorHex(times[1].textColor, .darkAqua))
    RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.3))
    precondition((slider.value(forKey: "barHeight") as? CGFloat) == 7)
    precondition((slider.value(forKey: "colorProgress") as? CGFloat) == 1)
    precondition(times.allSatisfy { colorHex($0.textColor, .darkAqua) == 0xe6e6e6 && colorHex($0.textColor, .aqua) == 0x303030 })
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    NSAppearance(named: .darkAqua)!.performAsCurrentDrawingAppearance {
        (slider.cell as! NSSliderCell).drawBar(inside: NSRect(x: 0, y: 0, width: 200, height: 20), flipped: false)
    }
    NSGraphicsContext.restoreGraphicsState()
    precondition(colorHex(bitmap.colorAt(x: 50, y: 10), .darkAqua) == 0xe6e6e6)
    slider.mouseExited(with: event)
    RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.35))
    precondition((slider.value(forKey: "barHeight") as? CGFloat) == 6)
    precondition((slider.value(forKey: "colorProgress") as? CGFloat) == 0)
    precondition(times.allSatisfy { colorHex($0.textColor, .darkAqua) == 0xa3a3a3 })
}
slider.doubleValue = 50
NSApp.sendAction(slider.action!, to: slider.target, from: slider)
precondition(seekPosition == 50 && seekTrack == "track-1")
"".withCString { id in
    "Seek".withCString { label in progress(0, 0, id, label) }
}
precondition(!slider.isEnabled && times[0].stringValue == "0:00" && times[1].stringValue == "0:00")
precondition(artwork.frame.size == NSSize(width: 58, height: 58))
precondition(artwork.frame.minX == 18 && content.bounds.maxY - artwork.frame.maxY == 18)
precondition(content.bounds.maxX - star.frame.maxX == 16)
precondition(content.layer?.cornerRadius == 36 && content.layer?.masksToBounds == true)
precondition(artwork.layer?.cornerRadius == 14 && artwork.layer?.masksToBounds == true)
precondition(artwork.layer?.borderWidth == 1 && artwork.layer?.borderColor != nil)
precondition(!star.isEnabled)
precondition(colorHex(star.contentTintColor, .darkAqua) == 0x9b9b9b)
star.performClick(nil)
precondition(favoriteClicks == 0)

"Song".withCString { song in
    "Artist".withCString { performer in
        "".withCString { path in
            "Favorite".withCString { label in
                update(song, performer, path, true, true, label)
            }
        }
    }
}
precondition(title.stringValue == "Song")
precondition(artist.stringValue == "Artist")
precondition(artwork.image != nil)
precondition(star.isEnabled)
precondition(star.state == .on)
precondition(colorHex(star.contentTintColor, .darkAqua) == 0xfcfcfc)

let titleMoving = content.subviews[1].subviews[0]
let artistMoving = content.subviews[2].subviews[0]
precondition(titleMoving.layer?.animation(forKey: "marquee") == nil)
"A long title that is much wider than the available metadata area".withCString { song in
    "An artist name that is much wider than the available metadata area".withCString { performer in
        "".withCString { path in
            "Favorite".withCString { label in
                update(song, performer, path, true, true, label)
                let animation = titleMoving.layer?.animation(forKey: "marquee") as? CAKeyframeAnimation
                precondition(animation != nil)
                let copies = titleMoving.layer?.sublayers?.filter { !($0.delegate is NSView) && !$0.isHidden } ?? []
                precondition(copies.count == 2 && copies.allSatisfy { $0.contents != nil })
                precondition(copies.allSatisfy { !($0.animationKeys() ?? []).contains("bounds") && !($0.animationKeys() ?? []).contains("position") && !($0.animationKeys() ?? []).contains("contents") })
                precondition(abs(copies[1].frame.minX - copies[0].frame.maxX - 32) < 0.01)
                precondition(artistMoving.layer?.animation(forKey: "marquee") != nil)
                precondition(abs((animation?.keyTimes?[1].doubleValue ?? 0) * (animation?.duration ?? 0) - 5) < 0.001)
                update(song, performer, path, true, true, label)
                precondition(titleMoving.layer?.animation(forKey: "marquee") != nil)
            }
        }
    }
}
hide()
precondition(titleMoving.layer?.animation(forKey: "marquee") == nil)
precondition(artistMoving.layer?.animation(forKey: "marquee") == nil)
show(100, 100)
precondition(titleMoving.layer?.animation(forKey: "marquee") != nil)
star.performClick(nil)
precondition(favoriteClicks == 1)
precondition(star.state == .on)

"dark".withCString(setTheme)
precondition(panel.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua)
precondition(colorHex(title.textColor, .darkAqua) == colorHex(playbackButtons[2].contentTintColor, .darkAqua))
"light".withCString(setTheme)
precondition(panel.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .aqua)
precondition(colorHex(title.textColor, .aqua) == colorHex(playbackButtons[2].contentTintColor, .aqua))
let lightBorder = artwork.layer?.borderColor
precondition(lightBorder != nil)
"black".withCString(setTheme)
precondition(panel.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua)
precondition(artwork.layer?.borderColor != lightBorder)
precondition(colorHex(title.textColor, .darkAqua) == colorHex(playbackButtons[2].contentTintColor, .darkAqua) && colorHex(artist.textColor, .darkAqua) == colorHex(playbackButtons[0].contentTintColor, .darkAqua))
precondition(colorHex(playbackButtons[0].contentTintColor, .darkAqua) == 0x878787)
precondition(times.allSatisfy { colorHex($0.textColor, .darkAqua) == 0x949494 })
let blackHover = NSEvent.enterExitEvent(with: .mouseEntered, location: .zero, modifierFlags: [], timestamp: 0, windowNumber: panel.windowNumber, context: nil, eventNumber: 0, trackingNumber: 0, userData: nil)!
slider.mouseEntered(with: blackHover)
RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.35))
precondition(times.allSatisfy { colorHex($0.textColor, .darkAqua) == 0xdfdfdf })
slider.mouseExited(with: blackHover)
RunLoop.main.run(until: Date(timeIntervalSinceNow: 0.35))
content.displayIfNeeded()
let blackBitmap = content.bitmapImageRepForCachingDisplay(in: content.bounds)!
content.cacheDisplay(in: content.bounds, to: blackBitmap)
precondition(colorHex(blackBitmap.colorAt(x: 155, y: 85), .darkAqua) == 0x000000)
