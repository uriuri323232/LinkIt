package com.lnk.shortcuts;

import java.util.Locale;

/** מטא-דאטה של סוגי הקיצורים. */
final class Types {
    private Types() {}

    static final class T {
        final String id, label, emoji, desc;
        final int color;

        T(String id, String label, String emoji, String desc, int color) {
            this.id = id;
            this.label = label;
            this.emoji = emoji;
            this.desc = desc;
            this.color = color;
        }
    }

    static final T[] ALL = {
            new T("url", "קישור", "🔗", "אתר או כתובת אינטרנט", 0xFF3B5BDB),
            new T("file", "קובץ", "📄", "פתיחת קובץ מהמכשיר", 0xFF0CA678),
            new T("folder", "תיקייה", "📁", "פתיחת תיקייה", 0xFFF59F00),
            new T("app", "אפליקציה", "📱", "הפעלת אפליקציה", 0xFF7048E8),
            new T("call", "חיוג", "📞", "חיוג למספר", 0xFF2F9E44),
            new T("sms", "הודעה", "💬", "SMS מוכן לשליחה", 0xFF1C7ED6),
            new T("whatsapp", "וואטסאפ", "💚", "שיחה ישירה", 0xFF25A244),
            new T("email", "אימייל", "✉️", "מייל מוכן לשליחה", 0xFFE8590C),
            new T("map", "מיקום", "📍", "מפה או ניווט", 0xFFE03131),
            new T("text", "טקסט", "📋", "העתקת טקסט ללוח", 0xFF5F3DC4),
            new T("settings", "הגדרות", "⚙️", "הגדרות המכשיר", 0xFF495057),
            new T("torch", "פנס", "🔦", "הדלקה / כיבוי", 0xFFE67700),
            new T("timer", "טיימר", "⏱️", "הפעלת טיימר", 0xFF1098AD),
            new T("group", "תפריט", "🗂️", "כמה קיצורים בחלון אחד", 0xFFD6336C),
    };

    static T get(String id) {
        for (T t : ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }

    /** הגדרות מערכת: {action, שם, אימוג'י}. */
    static final String[][] SETTINGS = {
            {"android.settings.WIFI_SETTINGS", "Wi-Fi", "📶"},
            {"android.settings.BLUETOOTH_SETTINGS", "בלוטוס", "🔷"},
            {"android.settings.AIRPLANE_MODE_SETTINGS", "מצב טיסה", "✈️"},
            {"android.settings.DATA_ROAMING_SETTINGS", "רשת סלולרית", "📡"},
            {"android.settings.WIRELESS_SETTINGS", "חיבורים ורשתות", "🌐"},
            {"android.settings.TETHER_SETTINGS", "נקודה חמה", "🔥"},
            {"android.settings.DISPLAY_SETTINGS", "תצוגה ובהירות", "🌗"},
            {"android.settings.SOUND_SETTINGS", "צליל ורטט", "🔊"},
            {"android.settings.BATTERY_SAVER_SETTINGS", "חיסכון בסוללה", "🔋"},
            {"android.settings.LOCATION_SOURCE_SETTINGS", "מיקום", "📍"},
            {"android.settings.ACCESSIBILITY_SETTINGS", "נגישות", "♿"},
            {"android.settings.DATE_SETTINGS", "תאריך ושעה", "🕒"},
            {"android.settings.LOCALE_SETTINGS", "שפה", "🔤"},
            {"android.settings.INPUT_METHOD_SETTINGS", "מקלדת", "⌨️"},
            {"android.settings.INTERNAL_STORAGE_SETTINGS", "אחסון", "💾"},
            {"android.settings.APPLICATION_SETTINGS", "אפליקציות", "🧩"},
            {"android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS", "כל האפליקציות", "🗃️"},
            {"android.settings.NFC_SETTINGS", "NFC", "📲"},
            {"android.settings.CAST_SETTINGS", "שיקוף מסך", "📺"},
            {"android.settings.SECURITY_SETTINGS", "אבטחה", "🔒"},
            {"android.settings.PRIVACY_SETTINGS", "פרטיות", "🛡️"},
            {"android.settings.DEVICE_INFO_SETTINGS", "אודות הטלפון", "ℹ️"},
            {"android.settings.SETTINGS", "הגדרות ראשיות", "⚙️"},
    };

    static String settingLabel(String action) {
        for (String[] s : SETTINGS) if (s[0].equals(action)) return s[2] + " " + s[1];
        return action;
    }

    static String fmtSecs(long s) {
        long m = s / 60;
        long r = s % 60;
        if (m >= 60) return String.format(Locale.US, "%d:%02d:%02d", m / 60, m % 60, r);
        return String.format(Locale.US, "%d:%02d", m, r);
    }

    static long parseSecs(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** שורת משנה לתצוגה ברשימה. */
    static String subtitle(Item it) {
        String label = get(it.type).label;
        String tail;
        switch (it.type) {
            case "file":
            case "folder":
                tail = it.d1.startsWith("/") ? it.d1 : it.d2;
                break;
            case "app":
                tail = it.d2;
                break;
            case "sms":
            case "whatsapp":
            case "call":
            case "email":
            case "map":
            case "url":
                tail = it.d1;
                break;
            case "text":
                tail = it.d1.replace('\n', ' ');
                break;
            case "settings":
                tail = settingLabel(it.d1);
                break;
            case "timer":
                tail = fmtSecs(parseSecs(it.d1));
                break;
            case "group": {
                int n = 0;
                for (String p : it.d1.split(",")) if (!p.isEmpty()) n++;
                tail = n + " פריטים";
                break;
            }
            default:
                tail = "";
        }
        return tail == null || tail.isEmpty() ? label : label + " · " + tail;
    }
}
