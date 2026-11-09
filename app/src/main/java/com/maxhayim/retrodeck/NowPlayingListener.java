package com.maxhayim.retrodeck;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Exists so Android lets RetroDeck see which app is playing and what (Settings → Notification
 * access). It reads no notifications: only the media sessions other apps publish, through
 * {@link OtherApps}.
 */
public class NowPlayingListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Not read.
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not read.
    }
}
