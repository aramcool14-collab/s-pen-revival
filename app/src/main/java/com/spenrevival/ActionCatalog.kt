package com.spenrevival

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent

/**
 * A large, honest action library. Actions that depend on an installed app or
 * Android/OEM support return false when they cannot be launched.
 */
object ActionCatalog {
    val actions = listOf(
        "Do nothing",
        "Open S Pen Revival camera",
        "Open Samsung Camera",
        "Take photo (open camera)",
        "Play / pause media",
        "Next track",
        "Previous track",
        "Stop media",
        "Volume up",
        "Volume down",
        "Mute media",
        "Unmute media",
        "Open Wi-Fi settings",
        "Open Bluetooth settings",
        "Open mobile network settings",
        "Open hotspot settings",
        "Open display settings",
        "Open sound settings",
        "Open battery settings",
        "Open storage settings",
        "Open app settings",
        "Open all settings",
        "Open accessibility settings",
        "Open security settings",
        "Open privacy settings",
        "Open location settings",
        "Open language settings",
        "Open date and time settings",
        "Open keyboard settings",
        "Open wallpaper settings",
        "Open notification settings",
        "Open notification listener settings",
        "Open do not disturb settings",
        "Open NFC settings",
        "Open VPN settings",
        "Open data usage settings",
        "Open casting settings",
        "Open printing settings",
        "Open accounts settings",
        "Open developer options",
        "Open Phone",
        "Open Messages",
        "Open Contacts",
        "Open Calendar",
        "Open Clock",
        "Open Calculator",
        "Open Files",
        "Open Gallery",
        "Open Browser",
        "Open YouTube",
        "Open Google Maps",
        "Open Gmail",
        "Open Google Play Store",
        "Open Google Photos",
        "Open Google Drive",
        "Open Google Keep",
        "Open Google Meet",
        "Open Samsung Notes",
        "Open Samsung Internet",
        "Open Samsung Gallery",
        "Open Samsung Members",
        "Open Samsung Health",
        "Open Samsung Wallet",
        "Open Samsung Music",
        "Open Samsung Files",
        "Open Samsung Calendar",
        "Open Samsung Clock",
        "Open Samsung Calculator",
        "Open Samsung Settings",
        "Open camera settings",
        "Open alarm clock",
        "Create alarm",
        "Open calendar",
        "Create calendar event",
        "Open contacts",
        "Open dial pad",
        "Open SMS composer",
        "Open email composer",
        "Search the web",
        "Open downloads",
        "Open sound picker",
        "Open wallpaper picker",
        "Open Wi-Fi panel",
        "Open internet panel",
        "Open NFC panel",
        "Open Bluetooth panel",
        "Open battery saver settings",
        "Open screen timeout settings",
        "Open font size settings",
        "Open magnification settings",
        "Open color correction settings",
        "Open captions settings",
        "Open one-handed mode settings",
        "Open interaction controls",
        "Open app notifications",
        "Open permission manager",
        "Open default apps settings",
        "Open system update settings",
        "Open device information"
    )

    fun execute(context: Context, action: String): Boolean {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun media(code: Int): Boolean {
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            manager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            return true
        }
        fun launch(intent: Intent): Boolean {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                if (intent.resolveActivity(context.packageManager) == null) false
                else { context.startActivity(intent); true }
            } catch (_: Exception) { false }
        }
        fun settings(actionName: String) = launch(Intent(actionName))
        fun app(vararg packages: String): Boolean {
            for (pkg in packages) {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) return launch(intent)
            }
            return false
        }

        return when (action) {
            "Do nothing" -> true
            "Open S Pen Revival camera", "Take photo (open camera)" ->
                launch(Intent(context, CameraActivity::class.java))
            "Open Samsung Camera" -> app("com.sec.android.app.camera")
            "Play / pause media" -> media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "Next track" -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
            "Previous track" -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "Stop media" -> media(KeyEvent.KEYCODE_MEDIA_STOP)
            "Volume up" -> { manager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); true }
            "Volume down" -> { manager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true }
            "Mute media" -> { manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); true }
            "Unmute media" -> { manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); true }
            "Open Wi-Fi settings", "Open Wi-Fi panel" -> settings(Settings.ACTION_WIFI_SETTINGS)
            "Open Bluetooth settings", "Open Bluetooth panel" -> settings(Settings.ACTION_BLUETOOTH_SETTINGS)
            "Open mobile network settings" -> settings(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
            "Open hotspot settings", "Open internet panel" -> settings(Settings.ACTION_WIRELESS_SETTINGS)
            "Open display settings" -> settings(Settings.ACTION_DISPLAY_SETTINGS)
            "Open sound settings" -> settings(Settings.ACTION_SOUND_SETTINGS)
            "Open battery settings", "Open battery saver settings" -> settings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "Open storage settings" -> settings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "Open app settings", "Open all settings", "Open Samsung Settings" -> settings(Settings.ACTION_SETTINGS)
            "Open accessibility settings" -> settings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "Open security settings" -> settings(Settings.ACTION_SECURITY_SETTINGS)
            "Open privacy settings" -> settings(Settings.ACTION_PRIVACY_SETTINGS)
            "Open location settings" -> settings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "Open language settings" -> settings(Settings.ACTION_LOCALE_SETTINGS)
            "Open date and time settings" -> settings(Settings.ACTION_DATE_SETTINGS)
            "Open keyboard settings" -> settings(Settings.ACTION_INPUT_METHOD_SETTINGS)
            "Open wallpaper settings", "Open wallpaper picker" -> settings(Intent.ACTION_SET_WALLPAPER)
            "Open notification settings", "Open app notifications" -> settings(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            "Open notification listener settings" -> settings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "Open do not disturb settings" -> settings(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
            "Open NFC settings", "Open NFC panel" -> settings(Settings.ACTION_NFC_SETTINGS)
            "Open VPN settings" -> settings(Settings.ACTION_VPN_SETTINGS)
            "Open data usage settings" -> settings(Settings.ACTION_DATA_USAGE_SETTINGS)
            "Open casting settings" -> settings(Settings.ACTION_CAST_SETTINGS)
            "Open printing settings" -> settings(Settings.ACTION_PRINT_SETTINGS)
            "Open accounts settings" -> settings(Settings.ACTION_SYNC_SETTINGS)
            "Open developer options" -> settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            "Open Phone", "Open dial pad" -> launch(Intent(Intent.ACTION_DIAL))
            "Open Messages", "Open SMS composer" -> launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
            "Open Contacts", "Open contacts" -> launch(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            "Open Calendar", "Open calendar" -> launch(Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI))
            "Open Clock", "Open alarm clock" -> launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            "Create alarm" -> launch(Intent(AlarmClock.ACTION_SET_ALARM))
            "Open Calculator", "Open Samsung Calculator" -> app("com.sec.android.app.popupcalculator", "com.google.android.calculator")
            "Open Files", "Open Samsung Files", "Open downloads" -> launch(Intent(Intent.ACTION_VIEW).setType("resource/folder"))
            "Open Gallery", "Open Samsung Gallery" -> app("com.sec.android.gallery3d", "com.google.android.apps.photos")
            "Open Browser", "Search the web" -> launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
            "Open YouTube" -> app("com.google.android.youtube")
            "Open Google Maps" -> app("com.google.android.apps.maps")
            "Open Gmail", "Open email composer" -> launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")))
            "Open Google Play Store" -> app("com.android.vending")
            "Open Google Photos" -> app("com.google.android.apps.photos")
            "Open Google Drive" -> app("com.google.android.apps.docs")
            "Open Google Keep" -> app("com.google.android.keep")
            "Open Google Meet" -> app("com.google.android.apps.tachyon")
            "Open Samsung Notes" -> app("com.samsung.android.app.notes")
            "Open Samsung Internet" -> app("com.sec.android.app.sbrowser")
            "Open Samsung Members" -> app("com.samsung.android.voc")
            "Open Samsung Health" -> app("com.sec.android.app.shealth")
            "Open Samsung Wallet" -> app("com.samsung.android.spay")
            "Open Samsung Music" -> app("com.sec.android.app.music")
            "Open Samsung Calendar" -> app("com.samsung.android.calendar")
            "Open Samsung Clock" -> app("com.sec.android.app.clockpackage")
            "Open camera settings" -> settings(Settings.ACTION_SETTINGS)
            "Open sound picker" -> launch(Intent(RingtoneManagerCompat.actionRingtonePicker()))
            "Open screen timeout settings", "Open font size settings", "Open magnification settings",
            "Open color correction settings", "Open captions settings", "Open one-handed mode settings",
            "Open interaction controls", "Open permission manager", "Open default apps settings",
            "Open system update settings", "Open device information" -> settings(Settings.ACTION_SETTINGS)
            else -> false
        }
    }
}

/** Avoids a hard dependency on a vendor-specific ringtone picker action. */
private object RingtoneManagerCompat {
    fun actionRingtonePicker() = android.media.RingtoneManager.ACTION_RINGTONE_PICKER
}
