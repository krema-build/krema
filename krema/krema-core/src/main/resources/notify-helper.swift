#!/usr/bin/env swift
import Foundation
import UserNotifications

// Native notification helper for Krema
// Requires: macOS 10.14+, Swift 5+

let args = CommandLine.arguments
guard args.count >= 3 else {
    fputs("Usage: krema-notify-helper <title> <body> [subtitle] [sound]\n", stderr)
    exit(1)
}

let title = args[1]
let body = args[2]
let subtitle = args.count > 3 && !args[3].isEmpty ? args[3] : nil
let useSound = args.count > 4 && args[4] == "true"

let center = UNUserNotificationCenter.current()
let semaphore = DispatchSemaphore(value: 0)

// First, check current authorization status
center.getNotificationSettings { settings in
    switch settings.authorizationStatus {
    case .notDetermined:
        // Request authorization
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if let error = error {
                fputs("Authorization error: \(error.localizedDescription)\n", stderr)
                exit(2)
            }
            if !granted {
                fputs("Notifications not authorized by user\n", stderr)
                exit(2)
            }
            semaphore.signal()
        }
    case .denied:
        fputs("Notifications denied. Please enable in System Settings > Notifications\n", stderr)
        exit(2)
    case .authorized, .provisional, .ephemeral:
        semaphore.signal()
    @unknown default:
        semaphore.signal()
    }
}
semaphore.wait()

// Create notification content
let content = UNMutableNotificationContent()
content.title = title
content.body = body
if let subtitle = subtitle {
    content.subtitle = subtitle
}
if useSound {
    content.sound = .default
}

// Create and send notification request
let requestId = "krema-\(ProcessInfo.processInfo.processIdentifier)-\(Date().timeIntervalSince1970)"
let request = UNNotificationRequest(identifier: requestId, content: content, trigger: nil)

center.add(request) { error in
    if let error = error {
        fputs("Failed to deliver notification: \(error.localizedDescription)\n", stderr)
        exit(3)
    }
    print("OK")
    semaphore.signal()
}
semaphore.wait()
