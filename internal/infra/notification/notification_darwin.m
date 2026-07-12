#import <Foundation/Foundation.h>
#import <UserNotifications/UserNotifications.h>

@interface AirmedyNotificationDelegate : NSObject <UNUserNotificationCenterDelegate>
@end

@implementation AirmedyNotificationDelegate

- (void)userNotificationCenter:(UNUserNotificationCenter *)center
       willPresentNotification:(UNNotification *)notification
         withCompletionHandler:(void (^)(UNNotificationPresentationOptions options))completionHandler {
    completionHandler(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionList);
}

@end

static AirmedyNotificationDelegate *notificationDelegate;

static void scheduleTrackAdvancedNotification(UNUserNotificationCenter *center,
                                               NSString *title,
                                               NSString *body,
                                               NSString *artworkPath) {
    UNMutableNotificationContent *content = [[UNMutableNotificationContent alloc] init];
    content.title = title ?: @"";
    content.body = body ?: @"";

    // Deliberately leave content.sound unset: automatic track changes are silent.
    if (artworkPath.length > 0) {
        NSURL *url = [NSURL fileURLWithPath:artworkPath];
        NSError *attachmentError = nil;
        UNNotificationAttachment *attachment = [UNNotificationAttachment attachmentWithIdentifier:@"artwork"
                                                                                                  URL:url
                                                                                              options:nil
                                                                                                error:&attachmentError];
        if (attachment != nil) {
            content.attachments = @[attachment];
        }
    }

    NSString *identifier = [[NSUUID UUID] UUIDString];
    UNNotificationRequest *request = [UNNotificationRequest requestWithIdentifier:identifier
                                                                            content:content
                                                                            trigger:nil];
    [center addNotificationRequest:request withCompletionHandler:nil];
}

void SendTrackAdvancedNotification(const char *titleCString,
                                   const char *bodyCString,
                                   const char *artworkPathCString) {
    NSString *title = [NSString stringWithUTF8String:titleCString ?: ""];
    NSString *body = [NSString stringWithUTF8String:bodyCString ?: ""];
    NSString *artworkPath = [NSString stringWithUTF8String:artworkPathCString ?: ""];
    UNUserNotificationCenter *center = [UNUserNotificationCenter currentNotificationCenter];

    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        notificationDelegate = [[AirmedyNotificationDelegate alloc] init];
        center.delegate = notificationDelegate;
    });

    [center getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
        switch (settings.authorizationStatus) {
            case UNAuthorizationStatusAuthorized:
            case UNAuthorizationStatusProvisional: {
                scheduleTrackAdvancedNotification(center, title, body, artworkPath);
                break;
            }
            case UNAuthorizationStatusNotDetermined: {
                [center requestAuthorizationWithOptions:UNAuthorizationOptionAlert
                                      completionHandler:^(BOOL granted, NSError *error) {
                    if (granted) {
                        scheduleTrackAdvancedNotification(center, title, body, artworkPath);
                    }
                }];
                break;
            }
            case UNAuthorizationStatusDenied:
            default:
                break;
        }
    }];
}
