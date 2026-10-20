package io.github.peshke.launchio;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing itself. Android only lists other apps' media sessions (what's playing, with
 * play/pause/skip) to an app with notification access; enabling this listener grants that.
 * Enable over ADB: cmd notification allow_listener io.github.peshke.launchio/io.github.peshke.launchio.NowPlayingListener
 */
public class NowPlayingListener extends NotificationListenerService {
}
