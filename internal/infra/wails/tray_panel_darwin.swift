import AppKit

@_silgen_name("goTrayPillShow")
private func goTrayPillShow()

@_silgen_name("goTrayPanelLostFocus")
private func goTrayPanelLostFocus()

@_silgen_name("goTrayPanelEscape")
private func goTrayPanelEscape()

@_silgen_name("goTrayPanelFavorite")
private func goTrayPanelFavorite()

@_silgen_name("goTrayPanelTick")
private func goTrayPanelTick()

@_silgen_name("goTrayPanelSeek")
private func goTrayPanelSeek(_ position: Double, _ trackID: UnsafePointer<CChar>)

@_silgen_name("goTrayPanelControl")
private func goTrayPanelControl(_ action: Int32)

private enum TrayColors {
    private static func color(light: Int, dark: Int, black: Int? = nil) -> NSColor {
        let darkValue = panelTheme == "black" ? (black ?? dark) : dark
        return NSColor(name: nil) { appearance in
            let value = appearance.bestMatch(from: [.aqua, .darkAqua]) == .darkAqua ? darkValue : light
            return NSColor(srgbRed: CGFloat((value >> 16) & 255) / 255,
                           green: CGFloat((value >> 8) & 255) / 255,
                           blue: CGFloat(value & 255) / 255, alpha: 1)
        }
    }
    static var title: NSColor { color(light: 0x303030, dark: 0xe6e6e6, black: 0xdfdfdf) }
    static var artist: NSColor { color(light: 0x707070, dark: 0x858585, black: 0x686868) }
    static var active: NSColor { color(light: 0x181818, dark: 0xfcfcfc) }
    static var inactive: NSColor { color(light: 0x777777, dark: 0x9b9b9b, black: 0x878787) }
    static var progress: NSColor { color(light: 0x303030, dark: 0xa3a3a3, black: 0x949494) }
    static var unplayed: NSColor { color(light: 0xdedede, dark: 0x393939, black: 0x232323) }
    static var background: NSColor { panelTheme == "black" ? NSColor(srgbRed: 0, green: 0, blue: 0, alpha: 1) : .windowBackgroundColor }

    static func progressColor(_ fraction: CGFloat) -> NSColor {
        NSColor(name: nil) { appearance in
            var color = progress
            appearance.performAsCurrentDrawingAppearance {
                color = progress.blended(withFraction: fraction, of: title) ?? title
            }
            return color
        }
    }
}

private final class SeekCell: NSSliderCell {
    override func drawBar(inside rect: NSRect, flipped: Bool) {
        let colorProgress = (controlView as? SeekSlider)?.colorProgress ?? 0
        let height = (controlView as? SeekSlider)?.barHeight ?? 6
        let bar = NSRect(x: rect.minX, y: rect.midY - height / 2, width: rect.width, height: height)
        TrayColors.unplayed.setFill()
        NSBezierPath(roundedRect: bar, xRadius: height / 2, yRadius: height / 2).fill()
        let fraction = maxValue > minValue ? CGFloat((doubleValue - minValue) / (maxValue - minValue)) : 0
        let filled = NSRect(x: bar.minX, y: bar.minY, width: bar.width * max(0, min(1, fraction)), height: height)
        TrayColors.progressColor(colorProgress).setFill()
        NSBezierPath(roundedRect: filled, xRadius: height / 2, yRadius: height / 2).fill()
    }

    override func drawKnob(_ knobRect: NSRect) {}
}

private final class SeekSlider: NSSlider {
    var hovering = false { didSet { if hovering != oldValue { animateInteraction() } } }
    var dragging = false { didSet { if dragging != oldValue { animateInteraction() } } }
    @objc dynamic private(set) var barHeight: CGFloat = 6
    @objc dynamic private(set) var colorProgress: CGFloat = 0 {
        didSet { onColorChange?(colorProgress) }
    }
    private var heightTimer: Timer?
    var trackID = ""
    var onPreview: ((Double) -> Void)?
    var onColorChange: ((CGFloat) -> Void)?
    var interacting: Bool { hovering || dragging }

    private func animateInteraction() {
        heightTimer?.invalidate()
        let target: CGFloat = interacting ? 7 : 6
        let initial = barHeight
        let colorTarget: CGFloat = interacting ? 1 : 0
        let colorInitial = colorProgress
        guard (initial != target || colorInitial != colorTarget), !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion else {
            barHeight = target
            colorProgress = colorTarget
            needsDisplay = true
            return
        }
        let start = ProcessInfo.processInfo.systemUptime
        let timer = Timer(timeInterval: 1.0 / 60, repeats: true) { [weak self] timer in
            guard let self else { timer.invalidate(); return }
            let progress = min(1, (ProcessInfo.processInfo.systemUptime - start) / 0.3)
            let eased = progress * progress * (3 - 2 * progress)
            self.barHeight = initial + (target - initial) * eased
            self.colorProgress = colorInitial + (colorTarget - colorInitial) * eased
            self.needsDisplay = true
            if progress >= 1 { timer.invalidate(); self.heightTimer = nil }
        }
        heightTimer = timer
        RunLoop.main.add(timer, forMode: .common)
        needsDisplay = true
    }

    func stopTransition() {
        heightTimer?.invalidate()
        heightTimer = nil
        hovering = false
        dragging = false
        heightTimer?.invalidate()
        heightTimer = nil
        barHeight = 6
        colorProgress = 0
    }

    override func updateTrackingAreas() {
        trackingAreas.forEach { removeTrackingArea($0) }
        addTrackingArea(NSTrackingArea(rect: .zero, options: [.mouseEnteredAndExited, .activeInKeyWindow, .inVisibleRect], owner: self))
        super.updateTrackingAreas()
    }

    override func mouseEntered(with event: NSEvent) { hovering = true; needsDisplay = true }
    override func mouseExited(with event: NSEvent) { hovering = false; needsDisplay = true }

    override func mouseDown(with event: NSEvent) {
        guard isEnabled else { return }
        dragging = true
        let id = trackID
        needsDisplay = true
        super.mouseDown(with: event)
        dragging = false
        needsDisplay = true
        if !id.isEmpty && id == trackID { id.withCString { goTrayPanelSeek(doubleValue, $0) } }
    }

    @objc func changed() {
        onPreview?(doubleValue)
        if !dragging && !trackID.isEmpty { trackID.withCString { goTrayPanelSeek(doubleValue, $0) } }
    }
}

private func trayTime(_ seconds: Double) -> String {
    guard seconds.isFinite, seconds >= 0, seconds < Double(Int.max) else { return "0:00" }
    let total = Int(seconds)
    return String(format: "%d:%02d", total / 60, total % 60)
}

private final class MarqueeText: NSView {
    let label = NSTextField(labelWithString: "")
    private let copy = NSTextField(labelWithString: "")
    private let moving = NSView()
    private let textLayers = [CALayer(), CALayer()]
    private var textWidth: CGFloat = 0

    init(frame: NSRect, font: NSFont, color: NSColor) {
        super.init(frame: frame)
        wantsLayer = true
        layer?.masksToBounds = true
        moving.wantsLayer = true
        addSubview(moving)
        for field in [label, copy] {
            field.font = font
            field.textColor = color
            field.maximumNumberOfLines = 1
            moving.addSubview(field)
        }
        copy.isHidden = true
        copy.setAccessibilityElement(false)
        for textLayer in textLayers {
            textLayer.isHidden = true
            moving.layer?.addSublayer(textLayer)
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func setText(_ text: String) {
        guard label.stringValue != text else { return }
        label.stringValue = text
        copy.stringValue = text
        setAccessibilityLabel(text)
        textWidth = ceil((text as NSString).size(withAttributes: [.font: label.font!]).width)
        label.frame = NSRect(x: 0, y: 0, width: max(bounds.width, textWidth), height: bounds.height)
        copy.frame = NSRect(x: textWidth + 32, y: 0, width: textWidth, height: bounds.height)
        moving.frame = NSRect(x: 0, y: 0, width: textWidth * 2 + 32, height: bounds.height)
        copy.isHidden = textWidth <= bounds.width + 1
        renderText()
        if window?.isVisible == true { start() }
    }

    func setTextColor(_ color: NSColor) {
        label.textColor = color
        copy.textColor = color
        renderText()
    }

    private func renderText() {
        guard textWidth > 0 else { return }
        let size = NSSize(width: textWidth, height: bounds.height)
        let image = NSImage(size: size, flipped: false) { rect in
            self.label.cell?.draw(withFrame: rect, in: self.label)
            return true
        }
        var rect = NSRect(origin: .zero, size: size)
        var contents: CGImage?
        effectiveAppearance.performAsCurrentDrawingAppearance {
            contents = image.cgImage(forProposedRect: &rect, context: nil, hints: nil)
        }
        for (index, textLayer) in textLayers.enumerated() {
            textLayer.frame = NSRect(x: CGFloat(index) * (textWidth + 32), y: 0, width: textWidth, height: bounds.height)
            textLayer.contents = contents
            textLayer.contentsScale = window?.backingScaleFactor ?? NSScreen.main?.backingScaleFactor ?? 1
        }
    }

    func start() {
        moving.layer?.removeAllAnimations()
        layer?.mask = nil
        let scroll = textWidth > bounds.width + 1 && !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion
        label.isHidden = scroll
        copy.isHidden = true
        for textLayer in textLayers { textLayer.isHidden = !scroll }
        guard scroll else { return }
        let mask = CAGradientLayer()
        mask.frame = bounds
        mask.colors = [NSColor.black.cgColor, NSColor.black.cgColor, NSColor.clear.cgColor]
        mask.locations = [0, NSNumber(value: Double(max(0, bounds.width - 16) / bounds.width)), 1]
        mask.startPoint = CGPoint(x: 0, y: 0.5)
        mask.endPoint = CGPoint(x: 1, y: 0.5)
        layer?.mask = mask
        let distance = textWidth + 32
        let duration = 5 + Double(distance / 20)
        let animation = CAKeyframeAnimation(keyPath: "transform.translation.x")
        animation.values = [0, 0, -distance]
        animation.keyTimes = [0, NSNumber(value: 5 / duration), 1]
        animation.duration = duration
        animation.repeatCount = .infinity
        moving.layer?.add(animation, forKey: "marquee")
    }

    func stop() { moving.layer?.removeAllAnimations() }
}

private final class PlayPauseButton: NSButton {
    @objc dynamic private(set) var pauseProgress: CGFloat = 0
    private var playing: Bool?
    private var transition: Timer?

    func setPlaying(_ value: Bool) {
        guard playing != value else { return }
        let initial = pauseProgress
        let target: CGFloat = value ? 1 : 0
        let animate = playing != nil && window?.isVisible == true && !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion
        playing = value
        transition?.invalidate()
        guard animate else { pauseProgress = target; needsDisplay = true; return }
        let start = ProcessInfo.processInfo.systemUptime
        let timer = Timer(timeInterval: 1.0 / 60, repeats: true) { [weak self] timer in
            guard let self else { timer.invalidate(); return }
            let progress = min(1, (ProcessInfo.processInfo.systemUptime - start) / 0.15)
            let eased = progress * progress * (3 - 2 * progress)
            self.pauseProgress = initial + (target - initial) * eased
            self.needsDisplay = true
            if progress >= 1 { timer.invalidate(); self.transition = nil }
        }
        transition = timer
        RunLoop.main.add(timer, forMode: .common)
    }

    override func mouseDown(with event: NSEvent) {
        animatePress(to: 0.82, duration: 0.06)
        super.mouseDown(with: event)
        animatePress(to: 1, duration: 0.10)
    }

    private func animatePress(to scale: CGFloat, duration: CFTimeInterval) {
        guard let layer else { return }
        let animation = CABasicAnimation(keyPath: "transform.scale")
        animation.fromValue = layer.presentation()?.value(forKeyPath: "transform.scale") ?? 1
        animation.toValue = scale
        animation.duration = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? 0 : duration
        animation.timingFunction = CAMediaTimingFunction(controlPoints: 0.4, 0, 0.2, 1)
        layer.anchorPoint = CGPoint(x: 0.5, y: 0.5)
        layer.position = CGPoint(x: frame.midX, y: frame.midY)
        layer.setAffineTransform(CGAffineTransform(scaleX: scale, y: scale))
        layer.add(animation, forKey: "press")
    }

    override func draw(_ dirtyRect: NSRect) {
        let size: CGFloat = 36
        func point(_ x: CGFloat, _ y: CGFloat, scale: CGFloat) -> NSPoint {
            NSPoint(x: bounds.midX + (x / 24 - 0.5) * size * scale,
                    y: bounds.midY - (y / 24 - 0.5) * size * scale)
        }
        func drawShape(_ points: [(CGFloat, CGFloat)], scale: CGFloat, alpha: CGFloat, radius: CGFloat) {
            guard alpha > 0 else { return }
            let vertices = points.map { point($0.0, $0.1, scale: scale) }
            func along(_ from: NSPoint, _ to: NSPoint) -> NSPoint {
                let dx = to.x - from.x, dy = to.y - from.y
                let length = hypot(dx, dy)
                let fraction = length == 0 ? 0 : min(radius * size * scale, length / 2) / length
                return NSPoint(x: from.x + dx * fraction, y: from.y + dy * fraction)
            }
            let path = NSBezierPath()
            path.move(to: along(vertices[0], vertices[vertices.count - 1]))
            for i in vertices.indices {
                let end = along(vertices[i], vertices[(i + 1) % vertices.count])
                let vertex = vertices[i]
                let start = along(vertex, vertices[(i + vertices.count - 1) % vertices.count])
                path.line(to: start)
                path.curve(to: end, controlPoint1: NSPoint(x: start.x + (vertex.x - start.x) * 2 / 3, y: start.y + (vertex.y - start.y) * 2 / 3), controlPoint2: NSPoint(x: end.x + (vertex.x - end.x) * 2 / 3, y: end.y + (vertex.y - end.y) * 2 / 3))
            }
            path.close()
            (contentTintColor ?? .labelColor).withAlphaComponent(alpha).setFill()
            path.fill()
        }
        let p = pauseProgress
        drawShape([(6, 3.5), (20, 12), (6, 20.5)], scale: 1 - p * 0.48, alpha: 1 - p, radius: 0.10)
        let scale = 0.52 + p * 0.48
        drawShape([(5.5, 4), (10.2, 4), (10.2, 20), (5.5, 20)], scale: scale, alpha: p, radius: 0.075)
        drawShape([(13.8, 4), (18.5, 4), (18.5, 20), (13.8, 20)], scale: scale, alpha: p, radius: 0.075)
    }
}

private final class TrayPanelContent: NSView {
    let artwork = NSImageView(frame: NSRect(x: 18, y: 94, width: 58, height: 58))
    let title = MarqueeText(frame: NSRect(x: 90, y: 120, width: 164, height: 20), font: .systemFont(ofSize: 13, weight: .semibold), color: TrayColors.active)
    let artist = MarqueeText(frame: NSRect(x: 90, y: 98, width: 164, height: 20), font: .systemFont(ofSize: 12), color: TrayColors.artist)
    let favorite = NSButton(frame: NSRect(x: 272, y: 111, width: 24, height: 24))
    let elapsed = NSTextField(labelWithString: "0:00")
    let total = NSTextField(labelWithString: "0:00")
    let seek = SeekSlider(frame: NSRect(x: 56, y: 63.5, width: 198, height: 20))
    private var controls: [NSButton] = []
    private var indicators: [NSView] = []
    private var isFavorite = false
    private var hasRealArtwork = false

    override init(frame: NSRect) {
        super.init(frame: frame)
        wantsLayer = true
        layer?.cornerRadius = 36
        layer?.masksToBounds = true
        artwork.imageScaling = .scaleProportionallyUpOrDown
        artwork.wantsLayer = true
        artwork.layer?.cornerRadius = 14
        artwork.layer?.masksToBounds = true
        artwork.setAccessibilityIdentifier("tray-artwork")
        addSubview(artwork)

        for label in [title, artist] {
            addSubview(label)
        }
        for view in [artwork, title, artist] as [NSView] {
            view.addGestureRecognizer(NSClickGestureRecognizer(target: self, action: #selector(showAirmedy)))
        }
        title.setAccessibilityIdentifier("tray-title")
        artist.setAccessibilityIdentifier("tray-artist")

        favorite.isBordered = false
        favorite.setButtonType(.toggle)
        favorite.target = self
        favorite.action = #selector(toggleFavorite)
        favorite.setAccessibilityIdentifier("tray-favorite")
        addSubview(favorite)

        for (label, x) in [(elapsed, 18.0), (total, 254.0)] {
            label.frame = NSRect(x: x, y: 60.5, width: 38, height: 20)
            label.font = .monospacedDigitSystemFont(ofSize: 10, weight: .regular)
            label.textColor = TrayColors.progressColor(0)
            addSubview(label)
        }
        elapsed.setAccessibilityIdentifier("tray-elapsed")
        total.setAccessibilityIdentifier("tray-duration")
        total.alignment = .right
        seek.cell = SeekCell()
        seek.minValue = 0
        seek.maxValue = 1
        seek.doubleValue = 0
        seek.isEnabled = false
        seek.isContinuous = true
        seek.target = seek
        seek.action = #selector(SeekSlider.changed)
        seek.onPreview = { [weak self] value in self?.elapsed.stringValue = trayTime(value) }
        seek.onColorChange = { [weak self] progress in
            let color = TrayColors.progressColor(progress)
            self?.elapsed.textColor = color
            self?.total.textColor = color
        }
        seek.setAccessibilityIdentifier("tray-seek")
        addSubview(seek)
        for (index, x) in [26.0, 86.0, 141.0, 196.0, 256.0].enumerated() {
            let button: NSButton = index == 2
                ? PlayPauseButton(frame: NSRect(x: x - 3, y: 19.75, width: 34, height: 34))
                : NSButton(frame: NSRect(x: x, y: 22.75, width: 28, height: 28))
            if index == 2 { button.wantsLayer = true }
            button.isBordered = false
            button.imagePosition = .imageOnly
            button.contentTintColor = TrayColors.active
            button.tag = index
            button.target = self
            button.action = #selector(playbackControl(_:))
            button.setAccessibilityIdentifier("tray-control-\(index)")
            addSubview(button)
            controls.append(button)
        }
        for index in [0, 4] {
            let dot = NSView(frame: NSRect(x: controls[index].frame.midX - 2, y: 18, width: 4, height: 4))
            dot.wantsLayer = true
            dot.layer?.cornerRadius = 2
            dot.setAccessibilityElement(false)
            dot.setAccessibilityIdentifier("tray-indicator-\(index)")
            addSubview(dot)
            indicators.append(dot)
        }
        refreshColors()
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func draw(_ dirtyRect: NSRect) {
        TrayColors.background.setFill()
        bounds.fill()
    }

    @objc private func showAirmedy() { goTrayPillShow() }

    @objc private func toggleFavorite() {
        favorite.state = isFavorite ? .on : .off
        goTrayPanelFavorite()
    }

    @objc private func playbackControl(_ sender: NSButton) {
        goTrayPanelControl(Int32(sender.tag))
    }

    func updateControls(playing: Bool, shuffle: Bool, repeatMode: Int32, labels: [String]) {
        indicators[0].isHidden = !controlShowIndicator || !shuffle
        indicators[1].isHidden = !controlShowIndicator || repeatMode == 0
        effectiveAppearance.performAsCurrentDrawingAppearance {
            for dot in indicators { dot.layer?.backgroundColor = TrayColors.active.cgColor }
        }
        let symbols = ["shuffle", "backward.fill", playing ? "pause.fill" : "play.fill", "forward.fill", repeatMode == 2 ? "repeat.1" : "repeat"]
        for (index, button) in controls.enumerated() {
            let size: CGFloat = index == 2 ? 30 : (index == 1 || index == 3 ? 20 : 17)
            let image = NSImage(systemSymbolName: symbols[index], accessibilityDescription: nil)
            if let playButton = button as? PlayPauseButton {
                playButton.setPlaying(playing)
            } else {
                button.image = image?.withSymbolConfiguration(.init(pointSize: size, weight: .medium))
            }
            button.toolTip = labels[index]
            button.setAccessibilityLabel(labels[index])
            button.contentTintColor = (index == 0 && !shuffle) || (index == 4 && repeatMode == 0) ? TrayColors.inactive : TrayColors.active
        }
    }

    func updateProgress(position: Double, duration: Double, trackID: String, seekLabel: String) {
        let length = duration.isFinite && duration > 0 ? duration : 0
        let value = position.isFinite ? max(0, min(position, length)) : 0
        seek.setAccessibilityLabel(seekLabel)
        let changedTrack = seek.trackID != trackID
        seek.trackID = trackID
        seek.isEnabled = !trackID.isEmpty && length > 0
        seek.maxValue = max(1, length)
        total.stringValue = trayTime(length)
        if !seek.dragging || changedTrack || !seek.isEnabled {
            seek.doubleValue = value
            elapsed.stringValue = trayTime(value)
        }
        seek.needsDisplay = true
    }

    func update(title: String, artist: String, artworkPath: String, hasTrack: Bool, favorite: Bool, favoriteLabel: String) {
        self.title.setText(title)
        self.artist.setText(artist)
        self.artwork.image = artworkPath.isEmpty ? nil : NSImage(contentsOfFile: artworkPath)
        hasRealArtwork = self.artwork.image != nil
        self.favorite.isEnabled = hasTrack
        self.favorite.toolTip = favoriteLabel
        self.favorite.setAccessibilityLabel(favoriteLabel)
        isFavorite = favorite
        self.favorite.state = favorite ? .on : .off
        refreshColors()
    }

    func refreshColors() {
        needsDisplay = true
        title.setTextColor(TrayColors.active)
        artist.setTextColor(TrayColors.inactive)
        let progressColor = TrayColors.progressColor(seek.colorProgress)
        elapsed.textColor = progressColor
        total.textColor = progressColor
        seek.needsDisplay = true
        updateControls(playing: controlPlaying, shuffle: controlShuffle, repeatMode: controlRepeat, labels: controlLabels)
        artwork.layer?.backgroundColor = NSColor.labelColor.withAlphaComponent(0.08).cgColor
        artwork.layer?.borderWidth = 1
        effectiveAppearance.performAsCurrentDrawingAppearance {
            artwork.layer?.borderColor = NSColor.labelColor.withAlphaComponent(0.1).cgColor
        }
        if !hasRealArtwork {
            let note = NSImage(size: artwork.bounds.size, flipped: false) { rect in
                let text = "♫" as NSString
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: NSFont.systemFont(ofSize: rect.height * 0.63),
                    .foregroundColor: TrayColors.artist,
                ]
                let size = text.size(withAttributes: attributes)
                text.draw(at: NSPoint(x: (rect.width - size.width) / 2, y: (rect.height - size.height) / 2), withAttributes: attributes)
                return true
            }
            artwork.image = note
        }
        let selected = isFavorite
        let star = NSImage(size: NSSize(width: 20, height: 20), flipped: false) { _ in
            let points: [NSPoint] = [
                .init(x: 10, y: 19), .init(x: 12.6, y: 13.8), .init(x: 18.3, y: 13),
                .init(x: 14.2, y: 8.9), .init(x: 15.2, y: 3.2), .init(x: 10, y: 5.9),
                .init(x: 4.8, y: 3.2), .init(x: 5.8, y: 8.9), .init(x: 1.7, y: 13),
                .init(x: 7.4, y: 13.8),
            ]
            let path = NSBezierPath()
            path.move(to: points[0])
            for point in points.dropFirst() { path.line(to: point) }
            path.close()
            path.lineWidth = 1.6
            path.lineJoinStyle = .round
            NSColor.labelColor.setStroke()
            if selected {
                NSColor.labelColor.setFill()
                path.fill()
            }
            path.stroke()
            return true
        }
        star.isTemplate = true
        favorite.image = star
        favorite.contentTintColor = selected ? TrayColors.active : TrayColors.inactive
        favorite.alphaValue = favorite.isEnabled ? 1 : 0.4
    }
}

private final class TrayPanel: NSPanel {
    override var canBecomeKey: Bool { true }

    override func keyDown(with event: NSEvent) {
        if event.keyCode == 53 {
            goTrayPanelEscape()
        } else {
            super.keyDown(with: event)
        }
    }
}

private final class TrayPanelDelegate: NSObject, NSWindowDelegate {
    func windowDidResignKey(_ notification: Notification) {
        goTrayPanelLostFocus()
    }
}

private var trayPanel: TrayPanel?
private var trayContent: TrayPanelContent?
private var panelVisible = false
private var panelAnimationID = 0
private let trayPanelDelegate = TrayPanelDelegate()
private var panelTitle = ""
private var panelArtist = ""
private var panelArtworkPath = ""
private var panelHasTrack = false
private var panelFavorite = false
private var panelFavoriteLabel = ""
private var panelTheme = "black"
private var panelPosition = 0.0
private var panelDuration = 0.0
private var panelTrackID = ""
private var panelSeekLabel = ""
private var progressTimer: Timer?
private var controlShowIndicator = true
private var controlPlaying = false
private var controlShuffle = false
private var controlRepeat: Int32 = 0
private var controlLabels = ["", "", "", "", ""]

@_cdecl("TrayPanelSetIndicator")
public func trayPanelSetIndicator(_ show: Bool) {
    controlShowIndicator = show
    trayContent?.updateControls(playing: controlPlaying, shuffle: controlShuffle, repeatMode: controlRepeat, labels: controlLabels)
}

@_cdecl("TrayPanelSetControls")
public func trayPanelSetControls(_ playing: Bool, _ shuffle: Bool, _ repeatMode: Int32, _ shuffleLabel: UnsafePointer<CChar>?, _ previousLabel: UnsafePointer<CChar>?, _ playLabel: UnsafePointer<CChar>?, _ nextLabel: UnsafePointer<CChar>?, _ repeatLabel: UnsafePointer<CChar>?) {
    controlPlaying = playing
    controlShuffle = shuffle
    controlRepeat = repeatMode
    controlLabels = [shuffleLabel, previousLabel, playLabel, nextLabel, repeatLabel].map { $0.map { String(cString: $0) } ?? "" }
    trayContent?.updateControls(playing: playing, shuffle: shuffle, repeatMode: repeatMode, labels: controlLabels)
}

@_cdecl("TrayPanelSetProgress")
public func trayPanelSetProgress(_ position: Double, _ duration: Double, _ trackID: UnsafePointer<CChar>?, _ seekLabel: UnsafePointer<CChar>?) {
    panelPosition = position
    panelDuration = duration
    panelTrackID = trackID.map { String(cString: $0) } ?? ""
    panelSeekLabel = seekLabel.map { String(cString: $0) } ?? ""
    trayContent?.updateProgress(
        position: panelPosition, duration: panelDuration, trackID: panelTrackID, seekLabel: panelSeekLabel
    )
}

private func applyPanelTheme() {
    let dark = panelTheme != "light"
    trayPanel?.appearance = NSAppearance(named: dark ? .darkAqua : .aqua)
    trayContent?.refreshColors()
}

@_cdecl("TrayPanelSetTheme")
public func trayPanelSetTheme(_ theme: UnsafePointer<CChar>?) {
    panelTheme = theme.map { String(cString: $0) } ?? "black"
    applyPanelTheme()
}

@_cdecl("TrayPanelRefreshTheme")
public func trayPanelRefreshTheme() { applyPanelTheme() }

@_cdecl("TrayPanelUpdate")
public func trayPanelUpdate(_ title: UnsafePointer<CChar>?, _ artist: UnsafePointer<CChar>?, _ artworkPath: UnsafePointer<CChar>?, _ hasTrack: Bool, _ favorite: Bool, _ favoriteLabel: UnsafePointer<CChar>?) {
    panelTitle = title.map { String(cString: $0) } ?? ""
    panelArtist = artist.map { String(cString: $0) } ?? ""
    panelArtworkPath = artworkPath.map { String(cString: $0) } ?? ""
    panelHasTrack = hasTrack
    panelFavorite = favorite
    panelFavoriteLabel = favoriteLabel.map { String(cString: $0) } ?? ""
    trayContent?.update(
        title: panelTitle, artist: panelArtist, artworkPath: panelArtworkPath,
        hasTrack: panelHasTrack, favorite: panelFavorite, favoriteLabel: panelFavoriteLabel
    )
}

private let panelAnimationDuration = 0.24

private func panelScaleTransform(_ layer: CALayer, _ scale: CGFloat) -> CATransform3D {
    let x = layer.bounds.width * (1 - scale) / 2
    let y = layer.bounds.height * (1 - scale)
    return CATransform3DScale(CATransform3DMakeTranslation(x, y, 0), scale, scale, 1)
}

@_cdecl("TrayPanelShow")
public func trayPanelShow(_ x: Int32, _ y: Int32) {
    if trayPanel == nil {
        let panel = TrayPanel(
            contentRect: NSRect(x: 0, y: 0, width: 310, height: 170),
            styleMask: [.titled, .fullSizeContentView, .nonactivatingPanel],
            backing: .buffered,
            defer: false
        )
        panel.isFloatingPanel = true
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.level = .floating
        panel.collectionBehavior = [.transient, .fullScreenAuxiliary]
        panel.titleVisibility = .hidden
        panel.titlebarAppearsTransparent = true
        panel.standardWindowButton(.closeButton)?.isHidden = true
        panel.standardWindowButton(.miniaturizeButton)?.isHidden = true
        panel.standardWindowButton(.zoomButton)?.isHidden = true
        panel.setFrame(NSRect(x: 0, y: 0, width: 310, height: 170), display: false)
        let content = TrayPanelContent(frame: NSRect(x: 0, y: 0, width: 310, height: 170))
        let container = NSView(frame: content.frame)
        container.wantsLayer = true
        container.addSubview(content)
        panel.contentView = container
        trayContent = content
        panel.delegate = trayPanelDelegate
        trayPanel = panel
        applyPanelTheme()
        content.update(title: panelTitle, artist: panelArtist, artworkPath: panelArtworkPath,
                       hasTrack: panelHasTrack, favorite: panelFavorite, favoriteLabel: panelFavoriteLabel)
        content.updateProgress(position: panelPosition, duration: panelDuration, trackID: panelTrackID, seekLabel: panelSeekLabel)
        content.updateControls(playing: controlPlaying, shuffle: controlShuffle, repeatMode: controlRepeat, labels: controlLabels)
    }

    guard let panel = trayPanel else { return }
    let screenTop = NSScreen.screens.first?.frame.maxY ?? 0
    panel.setFrameOrigin(NSPoint(x: CGFloat(x), y: screenTop - CGFloat(y) - panel.frame.height))
    let wasVisible = panel.isVisible
    panelVisible = true
    panelAnimationID += 1
    if let layer = trayContent?.layer {
        let start = wasVisible ? (layer.presentation()?.transform ?? CATransform3DIdentity) : panelScaleTransform(layer, 0.3)
        let startOpacity = wasVisible ? (layer.presentation()?.opacity ?? 1) : 0
        layer.removeAnimation(forKey: "visibility")
        layer.removeAnimation(forKey: "visibilityOpacity")
        layer.setAffineTransform(.identity)
        layer.opacity = 1
        if !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion {
            let animation = CABasicAnimation(keyPath: "transform")
            animation.fromValue = NSValue(caTransform3D: start)
            animation.toValue = NSValue(caTransform3D: CATransform3DIdentity)
            animation.duration = panelAnimationDuration
            animation.timingFunction = CAMediaTimingFunction(controlPoints: 0.4, 0, 0.2, 1)
            layer.add(animation, forKey: "visibility")
            let fade = CABasicAnimation(keyPath: "opacity")
            fade.fromValue = startOpacity
            fade.toValue = 1
            fade.duration = animation.duration
            fade.timingFunction = animation.timingFunction
            layer.add(fade, forKey: "visibilityOpacity")
        }
    }
    if !wasVisible { panel.alphaValue = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? 1 : 0 }
    panel.makeKeyAndOrderFront(nil)
    NSAnimationContext.runAnimationGroup { context in
        context.duration = NSWorkspace.shared.accessibilityDisplayShouldReduceMotion ? 0 : panelAnimationDuration
        panel.animator().alphaValue = 1
    }
    progressTimer?.invalidate()
    progressTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { _ in goTrayPanelTick() }
    goTrayPanelTick()
    if let content = trayContent {
        content.title.start()
        content.artist.start()
    }
}

@_cdecl("TrayPanelIsVisible")
public func trayPanelIsVisible() -> Bool {
    panelVisible
}

@_cdecl("TrayPanelHide")
public func trayPanelHide() {
    progressTimer?.invalidate()
    progressTimer = nil
    if let content = trayContent {
        content.title.stop()
        content.artist.stop()
        content.seek.stopTransition()
    }
    guard panelVisible, let panel = trayPanel else { return }
    panelVisible = false
    panelAnimationID += 1
    let animationID = panelAnimationID
    guard let layer = trayContent?.layer, !NSWorkspace.shared.accessibilityDisplayShouldReduceMotion else {
        panel.orderOut(nil)
        return
    }
    let animation = CABasicAnimation(keyPath: "transform")
    animation.fromValue = NSValue(caTransform3D: layer.presentation()?.transform ?? CATransform3DIdentity)
    animation.toValue = NSValue(caTransform3D: panelScaleTransform(layer, 0.3))
    animation.duration = panelAnimationDuration
    animation.timingFunction = CAMediaTimingFunction(controlPoints: 0.4, 0, 0.2, 1)
    let fade = CABasicAnimation(keyPath: "opacity")
    fade.fromValue = layer.presentation()?.opacity ?? 1
    fade.toValue = 0
    fade.duration = animation.duration
    layer.transform = panelScaleTransform(layer, 0.3)
    layer.opacity = 0
    layer.add(animation, forKey: "visibility")
    layer.add(fade, forKey: "visibilityOpacity")
    NSAnimationContext.runAnimationGroup { context in
        context.duration = animation.duration
        panel.animator().alphaValue = 0
    }
    DispatchQueue.main.asyncAfter(deadline: .now() + panelAnimationDuration) {
        if panelAnimationID == animationID { panel.orderOut(nil) }
    }
}

@_cdecl("TrayPanelDestroy")
public func trayPanelDestroy() {
    trayPanelHide()
    panelAnimationID += 1
    trayPanel?.orderOut(nil)
    trayPanel?.delegate = nil
    trayPanel = nil
    trayContent = nil
}
