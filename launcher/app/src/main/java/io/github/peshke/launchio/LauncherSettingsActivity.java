package io.github.peshke.launchio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "io.github.peshke.launchio.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView wallpaperChoice;
    private TextView themeChoice;
    private TextView fontChoice;
    private TextView heroPicksChoice, heroWatchingChoice, photosChoice, continueChoice, rowPeekChoice, rowsChoice;
    private TextView heroLogoChoice, showArtChoice, dockInputsChoice, dockBgChoice, cardShapeChoice, cardArtChoice,
            linkWideChoice, linkTallChoice;
    /** The look row that was used, kept across recreate() so its result and focus survive the redraw. */
    private int lookRow;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Look.apply(this);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(clockChoices);
        }
        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> showThemePicker());
        fontChoice = findViewById(R.id.fontChoice);
        fontChoice.setOnClickListener(v -> showFontPicker());
        heroPicksChoice = findViewById(R.id.heroPicksChoice);
        heroPicksChoice.setOnClickListener(v -> toggle(MainActivity.HERO_PICKS_KEY));
        heroWatchingChoice = findViewById(R.id.heroWatchingChoice);
        heroWatchingChoice.setOnClickListener(v -> toggle(MainActivity.HERO_WATCHING_KEY));
        continueChoice = findViewById(R.id.continueChoice);
        continueChoice.setOnClickListener(v -> toggle(MainActivity.CONTINUE_KEY));
        heroLogoChoice = findViewById(R.id.heroLogoChoice);
        heroLogoChoice.setOnClickListener(v -> toggle(MainActivity.HERO_LOGO_KEY));
        showArtChoice = findViewById(R.id.showArtChoice);
        showArtChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(MainActivity.PLAIN_ART_KEY, !choices.getBoolean(MainActivity.PLAIN_ART_KEY, false)).apply();
            changed();
            refresh();
        });
        cardShapeChoice = findViewById(R.id.cardShapeChoice);
        cardShapeChoice.setOnClickListener(v -> flip(MainActivity.CARD_TALL_KEY));
        cardArtChoice = findViewById(R.id.cardArtChoice);
        cardArtChoice.setOnClickListener(v -> flip(MainActivity.CARD_LINKS_KEY));
        linkWideChoice = findViewById(R.id.linkWideChoice);
        linkWideChoice.setOnClickListener(v -> editLink(MainActivity.LINK_WIDE_KEY, R.string.link_wide));
        linkTallChoice = findViewById(R.id.linkTallChoice);
        linkTallChoice.setOnClickListener(v -> editLink(MainActivity.LINK_TALL_KEY, R.string.link_tall));
        dockBgChoice = findViewById(R.id.dockBgChoice);
        dockBgChoice.setOnClickListener(v -> {
            String now = choices.getString(MainActivity.DOCK_BG_KEY, MainActivity.DOCK_BG_BOTH);
            String next = MainActivity.DOCK_BG_BOTH.equals(now) ? MainActivity.DOCK_BG_BACKDROP
                    : MainActivity.DOCK_BG_BACKDROP.equals(now) ? MainActivity.DOCK_BG_GLOW : MainActivity.DOCK_BG_BOTH;
            choices.edit().putString(MainActivity.DOCK_BG_KEY, next).apply();
            changed();
            refresh();
        });
        dockInputsChoice = findViewById(R.id.dockInputsChoice);
        dockInputsChoice.setOnClickListener(v -> showDockInputsPicker());
        rowsChoice = findViewById(R.id.rowsChoice);
        rowsChoice.setOnClickListener(v -> showRowsPicker());
        rowPeekChoice = findViewById(R.id.rowPeekChoice);
        rowPeekChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(MainActivity.ROW_PEEK_KEY, !choices.getBoolean(MainActivity.ROW_PEEK_KEY, false)).apply();
            changed();
            refresh();
        });
        photosChoice = findViewById(R.id.photosChoice);
        photosChoice.setOnClickListener(v -> toggle(Photos.KEY));
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        lookRow = state == null ? 0 : state.getInt("look_row");
        if (lookRow != 0) {
            changed();   // a theme or font changed before the redraw: Home must rebuild too
            findViewById(lookRow).requestFocus();
        } else if (config.launcherSettingsButton) {
            reorderChoice.requestFocus();
        } else {
            columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
        }
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showThemePicker() {
        List<Look.Palette> themes = new java.util.ArrayList<>(Look.THEMES.values());
        String[] labels = new String[themes.size()];
        int checked = 0;
        String current = Look.current(this).key;
        for (int i = 0; i < themes.size(); i++) {
            labels[i] = themes.get(i).name;
            if (themes.get(i).key.equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.look_theme)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    lookChanged(R.id.themeChoice, Look.THEME_KEY, themes.get(which).key);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFontPicker() {
        String[] labels = new String[Look.FONTS.length];
        int checked = 0;
        String current = Look.currentFont(this)[0];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = Look.FONTS[i][1];
            if (Look.FONTS[i][0].equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.look_font)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    lookChanged(R.id.fontChoice, Look.FONT_KEY, Look.FONTS[which][0]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Saves a theme or font and redraws this screen in it; Home rebuilds when it gets RESULT_OK. */
    private void lookChanged(int row, String key, String value) {
        if (value.equals(choices.getString(key, null))) return;
        choices.edit().putString(key, value).commit();
        lookRow = row;
        recreate();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("look_row", lookRow);
    }

    /** The hero's switches: on until turned off. */
    /** Like toggle(), for a choice that is off until picked. */
    private void flip(String key) {
        choices.edit().putBoolean(key, !choices.getBoolean(key, false)).apply();
        changed();
        refresh();
    }

    /** A poster link in Nuvio's format, pasted with the TV's keyboard or the phone remote; empty clears it. */
    private void editLink(String key, int title) {
        android.widget.EditText field = new android.widget.EditText(this);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        field.setHint("https://\u2026/{imdb_id}");
        field.setText(choices.getString(key, ""));
        // The keyboard covers OK on a TV: its Enter key saves.
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(R.string.link_help)
                .setView(field)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    String link = field.getText().toString().trim();
                    if (link.isEmpty()) choices.edit().remove(key).apply();
                    else choices.edit().putString(key, link).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        field.setOnEditorActionListener((v, action, event) -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            return true;
        });
    }

    private void toggle(String key) {
        choices.edit().putBoolean(key, !choices.getBoolean(key, true)).apply();
        changed();
        refresh();
    }

    /** Which TV inputs get a tile in the dock (only there, never in the apps). */
    private void showDockInputsPicker() {
        List<Inputs.Input> all = Inputs.list(this);
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> chosen = java.util.Arrays.asList(choices.getString(MainActivity.DOCK_INPUTS_KEY, "").split(","));
        String[] labels = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i).label;
            checked[i] = chosen.contains(all.get(i).id);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.dock_inputs)
                .setMultiChoiceItems(labels, checked, (dialog, which, on) -> checked[which] = on)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    StringBuilder ids = new StringBuilder();
                    for (int i = 0; i < all.size(); i++) {
                        if (checked[i]) ids.append(ids.length() > 0 ? "," : "").append(all.get(i).id);
                    }
                    choices.edit().putString(MainActivity.DOCK_INPUTS_KEY, ids.toString()).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Which catalogs get a row under Continue watching. */
    private void showRowsPicker() {
        List<String[]> all = MainActivity.catalogChoices(getResources());
        List<String> chosen = MainActivity.chosenCatalogs(choices, getResources());
        String[] labels = new String[all.size()];
        boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            labels[i] = all.get(i)[1];
            checked[i] = chosen.contains(all.get(i)[0]);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.rows_label)
                .setMultiChoiceItems(labels, checked, (dialog, which, on) -> checked[which] = on)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    StringBuilder keys = new StringBuilder();
                    for (int i = 0; i < all.size(); i++) {
                        if (checked[i]) keys.append(keys.length() > 0 ? "," : "").append(all.get(i)[0]);
                    }
                    choices.edit().putString(MainActivity.CATALOGS_KEY, keys.toString()).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        heroPicksChoice.setText(getString(R.string.setting_state, getString(R.string.featured_picks),
                getString(choices.getBoolean(MainActivity.HERO_PICKS_KEY, true) ? R.string.on : R.string.off)));
        heroWatchingChoice.setText(getString(R.string.setting_state, getString(R.string.featured_watching),
                getString(choices.getBoolean(MainActivity.HERO_WATCHING_KEY, true) ? R.string.on : R.string.off)));
        continueChoice.setText(getString(R.string.setting_state, getString(R.string.continue_watching),
                getString(choices.getBoolean(MainActivity.CONTINUE_KEY, true) ? R.string.on : R.string.off)));
        heroLogoChoice.setText(getString(R.string.setting_state, getString(R.string.hero_logos),
                getString(choices.getBoolean(MainActivity.HERO_LOGO_KEY, true) ? R.string.on : R.string.off)));
        String bg = choices.getString(MainActivity.DOCK_BG_KEY, MainActivity.DOCK_BG_BOTH);
        dockBgChoice.setText(getString(R.string.setting_state, getString(R.string.dock_bg),
                getString(MainActivity.DOCK_BG_BACKDROP.equals(bg) ? R.string.dock_bg_backdrop
                        : MainActivity.DOCK_BG_GLOW.equals(bg) ? R.string.dock_bg_glow : R.string.dock_bg_both)));
        showArtChoice.setText(getString(R.string.setting_state, getString(R.string.show_art),
                getString(choices.getBoolean(MainActivity.PLAIN_ART_KEY, false) ? R.string.art_plain : R.string.art_nuvio)));
        StringBuilder inputNames = new StringBuilder();
        List<String> dockIds = java.util.Arrays.asList(choices.getString(MainActivity.DOCK_INPUTS_KEY, "").split(","));
        for (Inputs.Input in : Inputs.list(this)) {
            if (dockIds.contains(in.id)) inputNames.append(inputNames.length() > 0 ? ", " : "").append(in.name);
        }
        dockInputsChoice.setText(getString(R.string.setting_state, getString(R.string.dock_inputs),
                inputNames.length() > 0 ? inputNames : getString(R.string.off)));
        StringBuilder rowNames = new StringBuilder();
        List<String> chosenRows = MainActivity.chosenCatalogs(choices, getResources());
        for (String[] c : MainActivity.catalogChoices(getResources())) {
            if (chosenRows.contains(c[0])) rowNames.append(rowNames.length() > 0 ? ", " : "").append(c[1]);
        }
        rowsChoice.setText(getString(R.string.setting_state, getString(R.string.rows_label),
                rowNames.length() > 0 ? rowNames : getString(R.string.off)));
        boolean tall = choices.getBoolean(MainActivity.CARD_TALL_KEY, false);
        cardShapeChoice.setText(getString(R.string.setting_state, getString(R.string.card_shape),
                getString(tall ? R.string.card_tall : R.string.card_wide)));
        cardArtChoice.setText(getString(R.string.setting_state, getString(R.string.card_art),
                getString(choices.getBoolean(MainActivity.CARD_LINKS_KEY, false) ? R.string.card_links : R.string.card_standard)));
        linkWideChoice.setText(getString(R.string.setting_state, getString(R.string.link_wide),
                getString(choices.contains(MainActivity.LINK_WIDE_KEY) ? R.string.link_set : R.string.link_unset)));
        linkTallChoice.setText(getString(R.string.setting_state, getString(R.string.link_tall),
                getString(choices.contains(MainActivity.LINK_TALL_KEY) ? R.string.link_set : R.string.link_unset)));
        rowPeekChoice.setText(getString(R.string.setting_state, getString(R.string.row_peek),
                getString(choices.getBoolean(MainActivity.ROW_PEEK_KEY, false) ? R.string.on : R.string.off)));
        photosChoice.setText(getString(R.string.setting_state, getString(R.string.background_photos),
                getString(choices.getBoolean(Photos.KEY, true) ? R.string.on : R.string.off)));
        themeChoice.setText(getString(R.string.look_theme_state, Look.current(this).name));
        fontChoice.setText(getString(R.string.look_font_state, Look.currentFont(this)[1]));
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
