package com.lnk.shortcuts;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.util.LruCache;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** LinkIt – מנהל קבצים עם קיצורי דרך. */
public class MainActivity extends Activity {

    static final String ACTION_BROWSE = "com.lnk.shortcuts.BROWSE";

    // תפריט ראשי
    private static final int M_SEARCH = 1, M_PASTE = 2, M_PLACES = 3, M_FAVS = 4,
            M_NEW = 5, M_SORT = 6, M_HIDDEN = 7, M_REFRESH = 8, M_PASTELINK = 9, M_EXT = 10;
    // תפריט בחירה
    private static final int A_COPY = 1, A_CUT = 2, A_DELETE = 3, A_SHARE = 4, A_RENAME = 5,
            A_ZIP = 6, A_UNZIP = 7, A_FAV = 8, A_HOME = 9, A_PROPS = 10, A_ALL = 11, A_LINK = 12, A_OPENWITH = 13, A_TARGET = 14;

    private interface Job {
        String run() throws Exception;
    }

    private SharedPreferences prefs;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    private View permView, browserView;
    private HorizontalScrollView crumbScroll;
    private LinearLayout crumbs;
    private ListView list;
    private TextView empty, status;
    private ProgressBar progress;
    private SearchView searchView;
    private MenuItem searchItem;
    private ActionMode actionMode;
    private FileAdapter adapter;

    private File cur;
    private final List<File> all = new ArrayList<>();
    private final List<File> shown = new ArrayList<>();
    private final Map<String, Integer> positions = new HashMap<>();
    private final Map<String, String[]> lnkCache = new HashMap<>();
    private final ExecutorService thumbPool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> thumbCache = new LruCache<String, Bitmap>(8 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount() / 1024;
        }
    };
    private String filter = "";
    private String lastQuery = "";
    private boolean searchMode;
    private int searchToken;
    private String pendingPath;

    private int sortKey;        // 0 שם, 1 תאריך, 2 גודל, 3 סוג
    private boolean sortDesc;
    private boolean showHidden;

    private static List<File> clip = new ArrayList<>();
    private static boolean clipCut;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------- מחזור חיים

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        sortKey = prefs.getInt("sortKey", 0);
        sortDesc = prefs.getBoolean("sortDesc", false);
        showHidden = prefs.getBoolean("showHidden", false);
        buildUi();
        handleIntent(getIntent());
        Opener.refreshDynamic(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
        if (hasAccess()) consumePending();
    }

    private void handleIntent(Intent in) {
        if (in != null && ACTION_BROWSE.equals(in.getAction())) {
            String p = in.getStringExtra("path");
            if (p != null) pendingPath = p;
        }
    }

    private boolean consumePending() {
        if (pendingPath == null) return false;
        File f = new File(pendingPath);
        pendingPath = null;
        if (f.isFile()) f = f.getParentFile();
        if (f != null && f.isDirectory()) {
            load(f, false);
            return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasAccess()) {
            permView.setVisibility(View.GONE);
            browserView.setVisibility(View.VISIBLE);
            if (consumePending()) return;
            if (cur == null) {
                File start = new File(prefs.getString("last", ""));
                if (!start.isDirectory() || !start.canRead()) {
                    start = Environment.getExternalStorageDirectory();
                }
                load(start, false);
            } else if (actionMode == null && !searchMode) {
                refreshCurrent();
            }
        } else {
            permView.setVisibility(View.VISIBLE);
            browserView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        pool.shutdownNow();
        thumbPool.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (searchItem != null && searchItem.isActionViewExpanded()) {
            searchItem.collapseActionView();
            return;
        }
        if (searchMode) {
            load(cur, true);
            return;
        }
        if (cur != null && !isRoot(cur)) {
            File parent = cur.getParentFile();
            if (parent != null && parent.canRead()) {
                load(parent, true);
                return;
            }
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- הרשאות

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    // ---------------------------------------------------------------- בניית ממשק

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        // מסך בקשת הרשאה
        LinearLayout perm = new LinearLayout(this);
        perm.setOrientation(LinearLayout.VERTICAL);
        perm.setGravity(Gravity.CENTER);
        perm.setPadding(dp(32), dp(32), dp(32), dp(32));
        TextView pt = new TextView(this);
        pt.setText("\uD83D\uDCC1\nכדי לנהל קבצים נדרשת גישה לאחסון המכשיר.\nהאפליקציה פועלת ללא אינטרנט ואינה שולחת מידע לשום מקום.");
        pt.setTextSize(17);
        pt.setGravity(Gravity.CENTER);
        perm.addView(pt);
        Button pb = new Button(this);
        pb.setText("הענק גישה לקבצים");
        pb.setOnClickListener(v -> requestAccess());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(20);
        perm.addView(pb, plp);
        permView = perm;

        // מסך הדפדפן
        LinearLayout br = new LinearLayout(this);
        br.setOrientation(LinearLayout.VERTICAL);

        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbScroll.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        crumbScroll.setBackgroundColor(0xFFEEF2F7);
        crumbs = new LinearLayout(this);
        crumbs.setOrientation(LinearLayout.HORIZONTAL);
        crumbs.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        crumbs.setPadding(dp(8), dp(2), dp(8), dp(2));
        crumbScroll.addView(crumbs);
        br.addView(crumbScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        br.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout body = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(new ColorDrawable(0x14000000));
        list.setDividerHeight(1);
        list.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
        adapter = new FileAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onItemOpen(pos));
        list.setMultiChoiceModeListener(new SelectionCallback());
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        empty = new TextView(this);
        empty.setText("התיקייה ריקה");
        empty.setTextSize(16);
        empty.setTextColor(0xFF888888);
        empty.setGravity(Gravity.CENTER);
        body.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(empty);
        br.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextSize(12);
        status.setTextColor(0xFF666666);
        status.setBackgroundColor(0xFFEEF2F7);
        status.setPadding(dp(14), dp(5), dp(14), dp(5));
        br.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        browserView = br;

        root.addView(permView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(browserView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        searchView = new SearchView(this);
        searchView.setQueryHint("חיפוש (Enter = כולל תתי־תיקיות)");
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                q = q.trim();
                if (!q.isEmpty()) startSearch(q);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String t) {
                if (!searchMode) {
                    filter = t.trim().toLowerCase(Locale.ROOT);
                    applyFilter();
                }
                return true;
            }
        });
    }

    // ---------------------------------------------------------------- טעינה ותצוגה

    private boolean isRoot(File f) {
        if (f.getParentFile() == null) return true;
        for (File r : FileUtils.storageRoots(this)) if (r.equals(f)) return true;
        return false;
    }

    private String rootLabel(File root) {
        if (root.equals(Environment.getExternalStorageDirectory())) return "זיכרון פנימי";
        if (root.getParentFile() == null) return "/";
        return "כרטיס SD";
    }

    private void load(File dir, boolean restore) {
        if (actionMode != null) actionMode.finish();
        searchToken++;
        if (dir == null || !dir.isDirectory() || !dir.canRead()) {
            toast("אין גישה לתיקייה");
            if (cur == null) {
                dir = Environment.getExternalStorageDirectory();
            } else {
                return;
            }
        }
        if (cur != null && !searchMode) positions.put(cur.getPath(), list.getFirstVisiblePosition());
        searchMode = false;
        progress.setVisibility(View.GONE);
        cur = dir;
        prefs.edit().putString("last", dir.getPath()).apply();
        filter = "";
        if (searchItem != null && searchItem.isActionViewExpanded()) {
            searchView.setQuery("", false);
            searchItem.collapseActionView();
        }
        File[] arr = dir.listFiles();
        all.clear();
        if (arr != null) {
            for (File f : arr) {
                if (showHidden || !f.getName().startsWith(".")) all.add(f);
            }
        }
        applyFilter();
        buildCrumbs();
        setTitle(isRoot(dir) ? rootLabel(dir) : dir.getName());
        Integer pos = restore ? positions.get(dir.getPath()) : null;
        list.setSelection(pos != null ? pos : 0);
        invalidateOptionsMenu();
    }

    private void refreshCurrent() {
        if (cur == null) return;
        if (searchMode) {
            startSearch(lastQuery);
            return;
        }
        positions.put(cur.getPath(), list.getFirstVisiblePosition());
        File[] arr = cur.listFiles();
        all.clear();
        if (arr != null) {
            for (File f : arr) {
                if (showHidden || !f.getName().startsWith(".")) all.add(f);
            }
        }
        applyFilter();
        Integer pos = positions.get(cur.getPath());
        if (pos != null) list.setSelection(pos);
    }

    private void applyFilter() {
        shown.clear();
        for (File f : all) {
            if (filter.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(filter)) shown.add(f);
        }
        sortList(shown);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void updateStatus() {
        String s = shown.size() + (searchMode ? " תוצאות" : " פריטים");
        if (!clip.isEmpty()) s += "   •   בלוח: " + clip.size() + (clipCut ? " (גזירה)" : " (העתקה)");
        status.setText(s);
        empty.setText(searchMode ? "לא נמצאו תוצאות" : "התיקייה ריקה");
    }

    private void sortList(List<File> l) {
        final int key = sortKey;
        final boolean desc = sortDesc;
        Collections.sort(l, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                boolean da = a.isDirectory(), db = b.isDirectory();
                if (da != db) return da ? -1 : 1;
                int c;
                switch (key) {
                    case 1:
                        c = Long.compare(a.lastModified(), b.lastModified());
                        break;
                    case 2:
                        c = Long.compare(a.length(), b.length());
                        break;
                    case 3:
                        c = FileUtils.ext(a.getName()).compareTo(FileUtils.ext(b.getName()));
                        break;
                    default:
                        c = 0;
                }
                if (c == 0) c = a.getName().compareToIgnoreCase(b.getName());
                return desc ? -c : c;
            }
        });
    }

    private void buildCrumbs() {
        crumbs.removeAllViews();
        File root = null;
        for (File r : FileUtils.storageRoots(this)) {
            if (FileUtils.isInside(cur, r) && (root == null || r.getPath().length() > root.getPath().length()))
                root = r;
        }
        if (root == null) root = new File("/");
        addCrumb(rootLabel(root), root);
        String rel = cur.getPath().substring(Math.min(cur.getPath().length(), root.getPath().length()));
        File acc = root;
        for (String part : rel.split("/")) {
            if (part.isEmpty()) continue;
            acc = new File(acc, part);
            TextView sep = new TextView(this);
            sep.setText(" \u203A ");
            sep.setTextColor(0xFF888888);
            crumbs.addView(sep);
            addCrumb(part, acc);
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    private void addCrumb(String label, final File target) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(0xFF1565C0);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        t.setOnClickListener(v -> {
            if (!target.equals(cur) || searchMode) load(target, false);
        });
        crumbs.addView(t);
    }

    // ---------------------------------------------------------------- פתיחת פריטים

    private void onItemOpen(int pos) {
        if (pos < 0 || pos >= shown.size()) return;
        File f = shown.get(pos);
        if (ShortcutFile.is(f)) {
            String[] t = ShortcutFile.read(f);
            if (t == null) toast("קובץ הקיצור פגום");
            else openFavorite(new Favorites.Item("", t[0], t[1]));
        } else if (f.isDirectory()) {
            load(f, false);
        } else {
            openFile(f);
        }
    }

    private void openFile(File f) {
        try {
            Intent i = Opener.target(this, "path", f.getAbsolutePath());
            if (i != null) startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("אין אפליקציה שיכולה לפתוח קובץ זה");
        } catch (Exception e) {
            toast("לא ניתן לפתוח את הקובץ");
        }
    }

    // ---------------------------------------------------------------- חיפוש

    private void startSearch(final String q) {
        if (cur == null) return;
        lastQuery = q;
        final int token = ++searchToken;
        final File base = cur;
        final String needle = q.toLowerCase(Locale.ROOT);
        if (actionMode != null) actionMode.finish();
        searchMode = true;
        shown.clear();
        adapter.notifyDataSetChanged();
        progress.setVisibility(View.VISIBLE);
        setTitle("חיפוש: " + q);
        updateStatus();
        pool.execute(() -> {
            List<File> res = new ArrayList<>();
            ArrayDeque<File> stack = new ArrayDeque<>();
            stack.push(base);
            while (!stack.isEmpty() && res.size() < 3000 && token == searchToken) {
                File[] kids = stack.pop().listFiles();
                if (kids == null) continue;
                for (File k : kids) {
                    if (!showHidden && k.getName().startsWith(".")) continue;
                    if (k.getName().toLowerCase(Locale.ROOT).contains(needle)) res.add(k);
                    if (k.isDirectory() && !java.nio.file.Files.isSymbolicLink(k.toPath())) stack.push(k);
                }
            }
            runOnUiThread(() -> {
                if (token != searchToken || isFinishing()) return;
                shown.clear();
                shown.addAll(res);
                sortList(shown);
                progress.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();
                updateStatus();
            });
        });
    }

    // ---------------------------------------------------------------- תפריט עליון

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        searchItem = menu.add(0, M_SEARCH, 0, "חיפוש");
        searchItem.setActionView(searchView);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM
                | MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                filter = "";
                if (cur != null) {
                    if (searchMode) load(cur, true);
                    else applyFilter();
                }
                return true;
            }
        });
        menu.add(0, M_PASTE, 1, "הדבק").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(0, M_PASTELINK, 1, "\uD83D\uDD17 הדבק כקיצור דרך").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_PLACES, 2, "\uD83D\uDCCD מקומות").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_FAVS, 3, "\u2B50 מועדפים וקיצורים").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_NEW, 4, "\u2795 חדש…").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_SORT, 5, "מיון").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_HIDDEN, 6, "הצג קבצים נסתרים")
                .setCheckable(true).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_EXT, 6, "קיצורי תיקייה נפתחים במנהל הקבצים שלי")
                .setCheckable(true).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(0, M_REFRESH, 7, "רענן").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem paste = menu.findItem(M_PASTE);
        if (paste != null) {
            paste.setVisible(!clip.isEmpty());
            paste.setTitle((clipCut ? "הדבק (העברה " : "הדבק (") + clip.size() + ")");
        }
        MenuItem pl = menu.findItem(M_PASTELINK);
        if (pl != null) pl.setVisible(!clip.isEmpty());
        MenuItem hidden = menu.findItem(M_HIDDEN);
        if (hidden != null) hidden.setChecked(showHidden);
        MenuItem ext = menu.findItem(M_EXT);
        if (ext != null) ext.setChecked(prefs.getBoolean("extFolders", true));
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case M_PASTE:
                paste();
                return true;
            case M_PASTELINK:
                pasteAsLinks();
                return true;
            case M_PLACES:
                showPlaces();
                return true;
            case M_FAVS:
                showFavorites();
                return true;
            case M_NEW:
                showNewMenu();
                return true;
            case M_SORT:
                showSortDialog();
                return true;
            case M_HIDDEN:
                showHidden = !showHidden;
                prefs.edit().putBoolean("showHidden", showHidden).apply();
                refreshCurrent();
                return true;
            case M_EXT: {
                boolean now = !prefs.getBoolean("extFolders", true);
                prefs.edit().putBoolean("extFolders", now).apply();
                toast(now ? "קיצורי תיקייה ייפתחו במנהל הקבצים שלך"
                        : "קיצורי תיקייה ייפתחו בתוך LinkIt");
                return true;
            }
            case M_REFRESH:
                refreshCurrent();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ---------------------------------------------------------------- מקומות / מועדפים / מיון

    private void showPlaces() {
        final List<String> labels = new ArrayList<>();
        final List<File> targets = new ArrayList<>();
        for (File r : FileUtils.storageRoots(this)) {
            labels.add("\uD83D\uDCBE " + rootLabel(r) + "  (" + FileUtils.formatSize(r.getFreeSpace()) + " פנויים)");
            targets.add(r);
        }
        String[][] std = {
                {Environment.DIRECTORY_DOWNLOADS, "הורדות"}, {Environment.DIRECTORY_DCIM, "מצלמה"},
                {Environment.DIRECTORY_PICTURES, "תמונות"}, {Environment.DIRECTORY_DOCUMENTS, "מסמכים"},
                {Environment.DIRECTORY_MUSIC, "מוזיקה"}, {Environment.DIRECTORY_MOVIES, "סרטונים"}};
        for (String[] s : std) {
            File d = Environment.getExternalStoragePublicDirectory(s[0]);
            if (d != null && d.isDirectory()) {
                labels.add("\uD83D\uDCC1 " + s[1]);
                targets.add(d);
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("מקומות")
                .setItems(labels.toArray(new String[0]), (d, which) -> load(targets.get(which), false))
                .show();
    }

    private void showFavorites() {
        final List<Favorites.Item> favs = Favorites.load(this);
        if (favs.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("מועדפים וקיצורים")
                    .setMessage("אין מועדפים עדיין.\nסמן קבצים או תיקיות (לחיצה ארוכה) ובחר \u2B50 הוסף למועדפים, "
                            + "או צור קישור/אפליקציה דרך \u2795 חדש.")
                    .setPositiveButton("סגור", null)
                    .show();
            return;
        }
        String[] labels = new String[favs.size()];
        for (int i = 0; i < labels.length; i++) {
            Favorites.Item it = favs.get(i);
            String ic = "url".equals(it.type) ? "\uD83D\uDD17 "
                    : "app".equals(it.type) ? "\uD83D\uDCF1 "
                    : (new File(it.data).isDirectory() ? "\uD83D\uDCC1 " : "\uD83D\uDCC4 ");
            labels[i] = ic + it.name;
        }
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("מועדפים (לחיצה ארוכה להסרה)")
                .setItems(labels, (d, which) -> openFavorite(favs.get(which)))
                .setNegativeButton("סגור", null)
                .create();
        dlg.show();
        dlg.getListView().setOnItemLongClickListener((p, v, pos, id) -> {
            new AlertDialog.Builder(this)
                    .setMessage("להסיר את \"" + favs.get(pos).name + "\" מהמועדפים?")
                    .setPositiveButton("הסר", (d2, w) -> {
                        Favorites.remove(this, pos);
                        dlg.dismiss();
                        showFavorites();
                    })
                    .setNegativeButton("ביטול", null)
                    .show();
            return true;
        });
    }

    private void openFavorite(Favorites.Item it) {
        try {
            if ("path".equals(it.type)) {
                File f = new File(it.data);
                if (f.isDirectory()) {
                    load(f, false);
                    return;
                }
            }
            Intent i = Opener.target(this, it.type, it.data);
            if (i == null) {
                toast("היעד לא נמצא");
            } else if (i.getComponent() != null
                    && MainActivity.class.getName().equals(i.getComponent().getClassName())) {
                load(new File(it.data), false);
            } else {
                startActivity(i);
            }
        } catch (Exception e) {
            toast("לא ניתן לפתוח את היעד");
        }
    }

    private void showSortDialog() {
        String[] names = {"שם", "תאריך שינוי", "גודל", "סוג"};
        String[] labels = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            labels[i] = names[i] + (i == sortKey ? (sortDesc ? "  \u25BC" : "  \u25B2") : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("מיון (לחיצה חוזרת הופכת כיוון)")
                .setItems(labels, (d, which) -> {
                    if (which == sortKey) sortDesc = !sortDesc;
                    else {
                        sortKey = which;
                        sortDesc = which == 1 || which == 2;
                    }
                    prefs.edit().putInt("sortKey", sortKey).putBoolean("sortDesc", sortDesc).apply();
                    applyFilter();
                })
                .show();
    }

    // ---------------------------------------------------------------- חדש…

    private void showNewMenu() {
        String[] items = {"\uD83D\uDCC1 תיקייה חדשה", "\uD83D\uDCC4 קובץ חדש",
                "\uD83D\uDD17 קיצור לקישור (אתר)", "\uD83D\uDCF1 קיצור לאפליקציה"};
        new AlertDialog.Builder(this)
                .setTitle("חדש")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            promptText("תיקייה חדשה", "שם התיקייה", "", name -> createItem(name, true));
                            break;
                        case 1:
                            promptText("קובץ חדש", "שם הקובץ", "", name -> createItem(name, false));
                            break;
                        case 2:
                            showLinkDialog();
                            break;
                        default:
                            pickApp();
                    }
                })
                .show();
    }

    private void createItem(String name, boolean dir) {
        if (cur == null || searchMode) return;
        if (!validName(name)) return;
        File f = new File(cur, name);
        if (f.exists()) {
            toast("כבר קיים פריט בשם זה");
            return;
        }
        try {
            boolean ok = dir ? f.mkdirs() : f.createNewFile();
            if (!ok) toast("היצירה נכשלה");
        } catch (Exception e) {
            toast("היצירה נכשלה");
        }
        refreshCurrent();
    }

    private boolean validName(String name) {
        if (name == null || name.trim().isEmpty() || name.contains("/") || name.equals(".") || name.equals("..")) {
            toast("שם לא תקין");
            return false;
        }
        return true;
    }

    private void showLinkDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        final EditText name = new EditText(this);
        name.setHint("שם לקיצור (אופציונלי)");
        name.setSingleLine(true);
        box.addView(name);
        final EditText url = new EditText(this);
        url.setHint("כתובת אתר (למשל example.com)");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setSingleLine(true);
        url.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        box.addView(url);
        new AlertDialog.Builder(this)
                .setTitle("קיצור לקישור")
                .setView(box)
                .setPositiveButton("המשך", (d, w) -> {
                    String u = url.getText().toString().trim();
                    if (u.isEmpty()) {
                        toast("יש להזין כתובת");
                        return;
                    }
                    if (!u.contains("://")) u = "https://" + u;
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        String host = Uri.parse(u).getHost();
                        n = host != null ? host : u;
                    }
                    chooseTarget(n, "url", u, Opener.appIcon(this));
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void pickApp() {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(main, 0));
        Collections.sort(apps, (a, b) -> a.loadLabel(pm).toString()
                .compareToIgnoreCase(b.loadLabel(pm).toString()));
        final String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i).loadLabel(pm).toString();
        new AlertDialog.Builder(this)
                .setTitle("בחר אפליקציה")
                .setItems(labels, (d, which) -> {
                    ResolveInfo ri = apps.get(which);
                    Icon icon = Icon.createWithBitmap(Opener.toBitmap(ri.loadIcon(pm), dp(96)));
                    chooseTarget(labels[which], "app", ri.activityInfo.packageName, icon);
                })
                .show();
    }

    /** בחירה: מסך הבית או מועדפים. */
    private void chooseTarget(final String name, final String type, final String data, final Icon icon) {
        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(new String[]{"\uD83C\uDFE0 הוסף למסך הבית", "\u2B50 הוסף למועדפים באפליקציה",
                                "\uD83D\uDCC4 צור קובץ קיצור בתיקייה הנוכחית"},
                        (d, which) -> {
                            if (which == 0) Opener.pin(this, type, data, name, icon);
                            else if (which == 1) addFavorite(name, type, data);
                            else createShortcutFile(name, type, data);
                        })
                .show();
    }

    private void createShortcutFile(String name, String type, String data) {
        if (cur == null) return;
        try {
            ShortcutFile.create(cur, name, type, data);
            toast("נוצר קובץ קיצור בתיקייה");
        } catch (Exception e) {
            toast("יצירת הקיצור נכשלה");
        }
        refreshCurrent();
    }

    /** כמו בווינדוס: נוצר קובץ קיצור בתיקייה הנוכחית; אחר כך גוזרים ומדביקים לכל מקום. */
    private void createShortcutFiles(List<File> sel) {
        if (cur == null) return;
        int n = 0;
        for (File f : sel) {
            try {
                ShortcutFile.create(cur, f.getName(), "path", f.getAbsolutePath());
                n++;
            } catch (Exception ignored) {
            }
        }
        toast(n == 0 ? "יצירת הקיצור נכשלה"
                : "נוצר קיצור דרך. גזור והדבק אותו לכל תיקייה שתרצה");
        refreshCurrent();
    }

    /** כמו "הדבק קיצור דרך" בווינדוס: מעתיקים פריט, נכנסים לתיקייה, ומדביקים אותו כקיצור. */
    private void pasteAsLinks() {
        if (clip.isEmpty() || cur == null) return;
        if (searchMode) {
            toast("צא ממצב חיפוש כדי להדביק");
            return;
        }
        int n = 0;
        for (File f : clip) {
            try {
                ShortcutFile.create(cur, f.getName(), "path", f.getAbsolutePath());
                n++;
            } catch (Exception ignored) {
            }
        }
        toast(n == 0 ? "יצירת הקיצור נכשלה" : "נוצרו " + n + " קיצורי דרך");
        refreshCurrent();
    }

    private void openWith(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(FileUtils.uri(this, f), FileUtils.mime(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "פתח באמצעות"));
        } catch (Exception e) {
            toast("אין אפליקציה שיכולה לפתוח קובץ זה");
        }
    }

    private Bitmap makeThumb(File f, boolean video) {
        try {
            int s = dp(44) * 2;
            if (video) {
                if (Build.VERSION.SDK_INT < 29) return null;
                return ThumbnailUtils.createVideoThumbnail(f, new android.util.Size(s, s), null);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= s && o.outHeight / (sample * 2) >= s) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    private String[] lnkInfo(File f) {
        String k = f.getPath() + "|" + f.lastModified();
        if (lnkCache.containsKey(k)) return lnkCache.get(k);
        String[] r = ShortcutFile.read(f);
        lnkCache.put(k, r);
        return r;
    }

    private String lnkEmoji(String[] t) {
        if ("url".equals(t[0])) return "\uD83D\uDD17";
        if ("app".equals(t[0])) return "\uD83D\uDCF1";
        return FileUtils.icon(new File(t[1]));
    }

    private void addFavorite(String name, String type, String data) {
        toast(Favorites.add(this, new Favorites.Item(name, type, data))
                ? "נוסף למועדפים" : "כבר קיים במועדפים");
    }

    // ---------------------------------------------------------------- בחירה מרובה

    private List<File> selected() {
        List<File> out = new ArrayList<>();
        android.util.SparseBooleanArray sb = list.getCheckedItemPositions();
        for (int i = 0; i < sb.size(); i++) {
            int pos = sb.keyAt(i);
            if (sb.valueAt(i) && pos < shown.size()) out.add(shown.get(pos));
        }
        return out;
    }

    private class SelectionCallback implements AbsListView.MultiChoiceModeListener {
        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            mode.setTitle(list.getCheckedItemCount() + " נבחרו");
            mode.invalidate();
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            actionMode = mode;
            menu.add(0, A_COPY, 0, "העתק").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, A_CUT, 1, "גזור").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, A_DELETE, 2, "מחק").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(0, A_LINK, 3, "\uD83D\uDD17 צור קיצור דרך (קובץ)").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_HOME, 3, "\uD83C\uDFE0 קיצור למסך הבית").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_FAV, 4, "\u2B50 הוסף למועדפים").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_SHARE, 5, "שתף").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_OPENWITH, 5, "פתח באמצעות…").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_TARGET, 5, "\uD83D\uDCC2 פתח מיקום יעד").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_RENAME, 6, "שנה שם").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_ZIP, 7, "דחוס ל־ZIP").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_UNZIP, 8, "חלץ כאן").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_PROPS, 9, "מאפיינים").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            menu.add(0, A_ALL, 10, "בחר הכול").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            List<File> sel = selected();
            boolean one = sel.size() == 1;
            boolean anyDir = false;
            for (File f : sel) if (f.isDirectory()) anyDir = true;
            menu.findItem(A_RENAME).setVisible(one);
            menu.findItem(A_HOME).setVisible(one);
            menu.findItem(A_PROPS).setVisible(one);
            menu.findItem(A_OPENWITH).setVisible(one && sel.get(0).isFile());
            boolean lnkPath = false;
            if (one && ShortcutFile.is(sel.get(0))) {
                String[] t = ShortcutFile.read(sel.get(0));
                lnkPath = t != null && "path".equals(t[0]);
            }
            menu.findItem(A_TARGET).setVisible(lnkPath);
            menu.findItem(A_SHARE).setVisible(!anyDir && !sel.isEmpty());
            menu.findItem(A_UNZIP).setVisible(one && sel.get(0).isFile()
                    && FileUtils.ext(sel.get(0).getName()).equals("zip"));
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            List<File> sel = selected();
            if (sel.isEmpty() && item.getItemId() != A_ALL) return true;
            switch (item.getItemId()) {
                case A_COPY:
                case A_CUT:
                    clip = new ArrayList<>(sel);
                    clipCut = item.getItemId() == A_CUT;
                    toast((clipCut ? "נגזרו " : "הועתקו ") + sel.size() + " פריטים – עבור ליעד והדבק");
                    mode.finish();
                    invalidateOptionsMenu();
                    updateStatus();
                    return true;
                case A_DELETE:
                    confirmDelete(sel, mode);
                    return true;
                case A_SHARE:
                    share(sel);
                    return true;
                case A_RENAME:
                    rename(sel.get(0), mode);
                    return true;
                case A_ZIP:
                    zipSelected(sel, mode);
                    return true;
                case A_UNZIP:
                    unzipSelected(sel.get(0), mode);
                    return true;
                case A_FAV:
                    for (File f : sel) {
                        Favorites.add(MainActivity.this,
                                new Favorites.Item(f.getName(), "path", f.getAbsolutePath()));
                    }
                    toast("נוסף למועדפים");
                    mode.finish();
                    return true;
                case A_OPENWITH:
                    openWith(sel.get(0));
                    return true;
                case A_TARGET: {
                    String[] t = ShortcutFile.read(sel.get(0));
                    mode.finish();
                    if (t != null) {
                        File tf = new File(t[1]);
                        File parent = tf.getParentFile();
                        if (tf.exists() && parent != null) load(parent, false);
                        else toast("היעד לא נמצא");
                    }
                    return true;
                }
                case A_LINK:
                    mode.finish();
                    createShortcutFiles(sel);
                    return true;
                case A_HOME:
                    Opener.pinPath(MainActivity.this, sel.get(0));
                    mode.finish();
                    return true;
                case A_PROPS:
                    showProperties(sel.get(0));
                    return true;
                case A_ALL:
                    for (int i = 0; i < shown.size(); i++) list.setItemChecked(i, true);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
        }
    }

    // ---------------------------------------------------------------- פעולות קבצים

    private void runJob(final Job job) {
        progress.setVisibility(View.VISIBLE);
        pool.execute(() -> {
            String msg;
            try {
                msg = job.run();
            } catch (Exception e) {
                msg = "שגיאה: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            final String m = msg;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                progress.setVisibility(View.GONE);
                if (m != null) toast(m);
                if (actionMode != null) actionMode.finish();
                refreshCurrent();
                invalidateOptionsMenu();
            });
        });
    }

    private void paste() {
        if (clip.isEmpty() || cur == null || searchMode) {
            if (searchMode) toast("צא ממצב חיפוש כדי להדביק");
            return;
        }
        final List<File> items = new ArrayList<>(clip);
        final boolean cut = clipCut;
        final File dest = cur;
        runJob(() -> {
            int ok = 0;
            for (File src : items) {
                if (!src.exists()) continue;
                if (src.isDirectory() && FileUtils.isInside(dest, src)) continue; // לא להעתיק תיקייה לתוך עצמה
                if (cut && dest.equals(src.getParentFile())) continue;
                File target = FileUtils.uniqueDest(dest, src.getName(), src.isDirectory());
                if (cut) {
                    if (!src.renameTo(target)) {
                        FileUtils.copy(src, target);
                        FileUtils.deleteRecursive(src);
                    }
                } else {
                    FileUtils.copy(src, target);
                }
                ok++;
            }
            if (cut) {
                clip = new ArrayList<>();
            }
            return (cut ? "הועברו " : "הודבקו ") + ok + " פריטים";
        });
    }

    private void confirmDelete(final List<File> sel, final ActionMode mode) {
        String msg = sel.size() == 1 ? "למחוק את \"" + sel.get(0).getName() + "\"?"
                : "למחוק " + sel.size() + " פריטים?";
        new AlertDialog.Builder(this)
                .setTitle("מחיקה")
                .setMessage(msg + "\nלא ניתן לשחזר.")
                .setPositiveButton("מחק", (d, w) -> runJob(() -> {
                    int fail = 0;
                    for (File f : sel) if (!FileUtils.deleteRecursive(f)) fail++;
                    clip.removeAll(sel);
                    return fail == 0 ? "נמחקו " + sel.size() + " פריטים" : "חלק מהפריטים לא נמחקו";
                }))
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void share(List<File> sel) {
        try {
            Intent i;
            if (sel.size() == 1) {
                i = new Intent(Intent.ACTION_SEND);
                i.setType(FileUtils.mime(sel.get(0)));
                i.putExtra(Intent.EXTRA_STREAM, FileUtils.uri(this, sel.get(0)));
            } else {
                i = new Intent(Intent.ACTION_SEND_MULTIPLE);
                i.setType("*/*");
                ArrayList<Uri> uris = new ArrayList<>();
                for (File f : sel) uris.add(FileUtils.uri(this, f));
                i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "שתף"));
        } catch (Exception e) {
            toast("לא ניתן לשתף");
        }
    }

    private void rename(final File f, final ActionMode mode) {
        promptText("שינוי שם", "שם חדש", f.getName(), name -> {
            if (!validName(name)) return;
            File t = new File(f.getParentFile(), name);
            if (t.exists()) {
                toast("כבר קיים פריט בשם זה");
                return;
            }
            if (f.renameTo(t)) {
                mode.finish();
                refreshCurrent();
            } else {
                toast("שינוי השם נכשל");
            }
        });
    }

    private void zipSelected(final List<File> sel, ActionMode mode) {
        String def = (sel.size() == 1 ? FileUtils.baseName(sel.get(0).getName()) : "archive") + ".zip";
        promptText("דחיסה ל־ZIP", "שם הארכיון", def, name -> {
            if (!validName(name)) return;
            if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) name += ".zip";
            final File out = FileUtils.uniqueDest(cur, name, false);
            runJob(() -> {
                FileUtils.zip(sel, out);
                return "נוצר " + out.getName();
            });
        });
    }

    private void unzipSelected(final File zip, ActionMode mode) {
        final File dest = FileUtils.uniqueDest(zip.getParentFile(), FileUtils.baseName(zip.getName()), true);
        runJob(() -> {
            int n = FileUtils.unzip(zip, dest);
            return "חולצו " + n + " קבצים אל " + dest.getName();
        });
    }

    private void showProperties(final File f) {
        final StringBuilder sb = new StringBuilder();
        sb.append("שם: ").append(f.getName()).append('\n');
        sb.append("נתיב: ").append(f.getParent()).append('\n');
        sb.append("סוג: ").append(f.isDirectory() ? "תיקייה" : FileUtils.mime(f)).append('\n');
        sb.append("שינוי אחרון: ").append(FileUtils.formatDate(f.lastModified())).append('\n');
        sb.append("הרשאות: ").append(f.canRead() ? "קריאה " : "").append(f.canWrite() ? "כתיבה" : "").append('\n');
        if (ShortcutFile.is(f)) {
            String[] t = ShortcutFile.read(f);
            sb.append("יעד הקיצור: ").append(t == null ? "פגום" : t[1]).append('\n');
        }
        final String head = sb.toString();
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("מאפיינים")
                .setMessage(head + "גודל: מחשב…")
                .setPositiveButton("סגור", null)
                .show();
        pool.execute(() -> {
            long[] st = FileUtils.stats(f);
            String tail = "גודל: " + FileUtils.formatSize(st[0]) + " (" + st[0] + " בייט)";
            if (f.isDirectory()) tail += "\nקבצים: " + st[1] + "   תיקיות: " + Math.max(0, st[2] - 1);
            final String t = tail;
            runOnUiThread(() -> {
                if (dlg.isShowing()) dlg.setMessage(head + t);
            });
        });
    }

    // ---------------------------------------------------------------- עזרים

    private interface TextCallback {
        void onText(String s);
    }

    private void promptText(String title, String hint, String initial, final TextCallback cb) {
        final EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setText(initial);
        et.setSelection(0, FileUtils.baseName(initial).length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.addView(et);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("אישור", (d, w) -> cb.onText(et.getText().toString().trim()))
                .setNegativeButton("ביטול", null)
                .create();
        dlg.setOnShowListener(d -> {
            et.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        });
        dlg.show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- אדפטר

    private class FileAdapter extends BaseAdapter {

        private class Holder {
            TextView icon, arrow, name, sub;
            ImageView thumb;
            String bound;
        }

        private void showThumb(Holder h, Bitmap b) {
            h.thumb.setImageBitmap(b);
            h.thumb.setVisibility(View.VISIBLE);
            h.icon.setText("");
        }

        private void bindThumb(final Holder h, final File f) {
            String m = FileUtils.mime(f);
            final boolean video = m.startsWith("video/");
            if (!(m.startsWith("image/") || (video && Build.VERSION.SDK_INT >= 29))) return;
            final String key = f.getPath() + "|" + f.lastModified();
            Bitmap cached = thumbCache.get(key);
            if (cached != null) {
                showThumb(h, cached);
                return;
            }
            try {
                thumbPool.execute(() -> {
                    Bitmap t = makeThumb(f, video);
                    if (t == null) return;
                    thumbCache.put(key, t);
                    runOnUiThread(() -> {
                        if (f.getPath().equals(h.bound)) showThumb(h, t);
                    });
                });
            } catch (Exception ignored) {
            }
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(8), dp(14), dp(8));
                row.setMinimumHeight(dp(60));
                StateListDrawable sd = new StateListDrawable();
                sd.addState(new int[]{android.R.attr.state_activated}, new ColorDrawable(0x331565C0));
                sd.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(0x22000000));
                sd.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
                row.setBackground(sd);

                h = new Holder();
                h.icon = new TextView(MainActivity.this);
                h.icon.setTextSize(26);
                h.icon.setGravity(Gravity.CENTER);
                h.arrow = new TextView(MainActivity.this);
                h.arrow.setText("\u2197");
                h.arrow.setTextSize(11);
                h.arrow.setTypeface(null, android.graphics.Typeface.BOLD);
                h.arrow.setTextColor(0xFF1565C0);
                h.arrow.setGravity(Gravity.CENTER);
                GradientDrawable ab = new GradientDrawable();
                ab.setShape(GradientDrawable.OVAL);
                ab.setColor(0xFFFFFFFF);
                ab.setStroke(1, 0xFF1565C0);
                h.arrow.setBackground(ab);
                FrameLayout iconBox = new FrameLayout(MainActivity.this);
                iconBox.setMinimumHeight(dp(44));
                iconBox.addView(h.icon, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                h.thumb = new ImageView(MainActivity.this);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.thumb.setVisibility(View.GONE);
                iconBox.addView(h.thumb, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
                iconBox.addView(h.arrow, new FrameLayout.LayoutParams(dp(16), dp(16),
                        Gravity.BOTTOM | Gravity.START));
                row.addView(iconBox, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout col = new LinearLayout(MainActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                clp.setMarginStart(dp(10));
                h.name = new TextView(MainActivity.this);
                h.name.setTextSize(16);
                h.name.setTextColor(0xFF111111);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                h.name.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                h.sub = new TextView(MainActivity.this);
                h.sub.setTextSize(12);
                h.sub.setTextColor(0xFF777777);
                h.sub.setMaxLines(2);
                h.sub.setEllipsize(TextUtils.TruncateAt.END);
                h.sub.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                col.addView(h.name);
                col.addView(h.sub);
                row.addView(col, clp);
                row.setTag(h);
                convertView = row;
            } else {
                h = (Holder) convertView.getTag();
            }
            File f = shown.get(position);
            boolean isLnk = ShortcutFile.is(f);
            h.arrow.setVisibility(isLnk ? View.VISIBLE : View.GONE);
            String sub;
            if (isLnk) {
                String[] lnk = lnkInfo(f);
                h.icon.setText(lnk == null ? "\u2753" : lnkEmoji(lnk));
                h.name.setText(FileUtils.baseName(f.getName()));
                sub = "קיצור דרך: " + (lnk == null ? "פגום" : lnk[1]);
            } else {
                h.icon.setText(FileUtils.icon(f));
                h.name.setText(f.getName());
                sub = f.isDirectory() ? FileUtils.formatDate(f.lastModified())
                        : FileUtils.formatSize(f.length()) + "  \u00B7  " + FileUtils.formatDate(f.lastModified());
            }
            h.bound = f.getPath();
            h.thumb.setVisibility(View.GONE);
            h.thumb.setImageDrawable(null);
            if (!isLnk && f.isFile()) bindThumb(h, f);
            if (searchMode && f.getParent() != null) sub = f.getParent() + "\n" + sub;
            h.sub.setText(sub);
            return convertView;
        }
    }
}
