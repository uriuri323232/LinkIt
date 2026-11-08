package com.lnk.shortcuts;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** כלי עיצוב משותפים (בלי ספריות חיצוניות). */
final class Ui {
    private Ui() {}

    static boolean night(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    static int bg(Context c) { return night(c) ? 0xFF0D0F14 : 0xFFF3F5FA; }
    static int surface(Context c) { return night(c) ? 0xFF1A1D24 : 0xFFFFFFFF; }
    static int fieldBg(Context c) { return night(c) ? 0xFF232731 : 0xFFECEEF5; }
    static int text(Context c) { return night(c) ? 0xFFECEFF4 : 0xFF1B1F2A; }
    static int sub(Context c) { return night(c) ? 0xFF9AA3B2 : 0xFF6B7280; }
    static int primary(Context c) { return night(c) ? 0xFF8CA7FF : 0xFF3B5BDB; }
    static int onPrimary(Context c) { return night(c) ? 0xFF0B1020 : 0xFFFFFFFF; }
    static int outline(Context c) { return night(c) ? 0xFF272C37 : 0xFFE4E8F0; }
    static int tint(Context c) { return night(c) ? 0xFF252E4A : 0xFFE6ECFF; }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rect(Context c, int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable rectStroke(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = rect(c, fill, radiusDp);
        g.setStroke(Math.max(1, dp(c, 1)), stroke);
        return g;
    }

    static Drawable press(Context c, Drawable content) {
        return new RippleDrawable(
                ColorStateList.valueOf(night(c) ? 0x33FFFFFF : 0x1F000000), content, null);
    }

    static TextView label(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    static TextView section(Context c, String s) {
        TextView t = label(c, s, 13, sub(c), true);
        t.setLetterSpacing(0.04f);
        t.setPadding(dp(c, 6), dp(c, 22), dp(c, 6), dp(c, 8));
        return t;
    }

    static TextView button(Context c, String s, boolean primary, View.OnClickListener l) {
        TextView t = label(c, s, 16, primary ? onPrimary(c) : text(c), true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 52));
        t.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        if (primary) t.setElevation(dp(c, 2));
        t.setBackground(press(c, primary
                ? rect(c, primary(c), 18)
                : rectStroke(c, surface(c), outline(c), 18)));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static TextView chip(Context c, String s, boolean selected, View.OnClickListener l) {
        TextView t = label(c, s, 13, selected ? onPrimary(c) : text(c), true);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 40));
        t.setPadding(dp(c, 16), dp(c, 9), dp(c, 16), dp(c, 9));
        t.setBackground(press(c, selected
                ? rect(c, primary(c), 22)
                : rectStroke(c, surface(c), outline(c), 22)));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static EditText input(Context c, String hint, String value, int type, boolean multi, boolean ltr) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(16);
        e.setTextColor(text(c));
        e.setHintTextColor(sub(c));
        e.setBackground(rect(c, fieldBg(c), 16));
        e.setMinHeight(dp(c, 52));
        e.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        e.setInputType(multi ? (type | InputType.TYPE_TEXT_FLAG_MULTI_LINE) : type);
        if (multi) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
            e.setEllipsize(TextUtils.TruncateAt.END);
            e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        }
        if (ltr) e.setTextDirection(View.TEXT_DIRECTION_LTR);
        return e;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static void rtl(View v) {
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    static void watch(EditText e, final Runnable r) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) { r.run(); }
        });
    }

    /** כרטיס קרדיט קבוע. */
    static View creditCard(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(c, 20), dp(c, 16), dp(c, 20), dp(c, 16));
        box.setBackground(rectStroke(c, tint(c), outline(c), 18));
        TextView a = label(c, "פותח על ידי", 12, sub(c), false);
        a.setGravity(Gravity.CENTER);
        TextView b = label(c, "יהודי לא פשוט", 18, text(c), true);
        b.setGravity(Gravity.CENTER);
        TextView d = label(c, "@מתמחים טופ", 15, primary(c), true);
        d.setGravity(Gravity.CENTER);
        d.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.addView(a);
        box.addView(b);
        box.addView(d);
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(c, 12), 0, dp(c, 8));
        wrap.addView(box, new LinearLayout.LayoutParams(MATCH, WRAP));
        return wrap;
    }
}
