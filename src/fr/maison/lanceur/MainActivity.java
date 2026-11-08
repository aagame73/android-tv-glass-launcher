package fr.maison.lanceur;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Écran d'accueil sur mesure pour la télé TCL. */
public class MainActivity extends Activity {

    static final String TAG = "Lanceur";
    static final String PKG_TELEVIZO = "com.ottplay.ottplay";
    static final String PKG_TV = "com.tcl.tv";

    static final int VERRE = 0x26FFFFFF;        // fond de tuile : blanc 15 %
    static final int VERRE_FOCUS = 0x4DFFFFFF;  // tuile sélectionnée : blanc 30 %
    static final int BORD = 0x40FFFFFF;

    // Météo via Open-Meteo (gratuit, sans clé ni compte) ; ville et coordonnées dans config.properties.
    static final String METEO_URL = "https://api.open-meteo.com/v1/forecast?latitude=" + Config.LATITUDE + "&longitude=" + Config.LONGITUDE
            + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
            + "&timezone=Europe%2FParis&forecast_days=1";
    static final long METEO_PERIODE = 30 * 60 * 1000L;

    private Typeface police;
    private SharedPreferences prefs;
    private LinearLayout dock;
    private View tuileTelevizo, boutonPlus;
    private TextView nomDock;
    private TextView meteoIcone, meteoTemp, meteoDetail;
    private ImageView fond;
    private int fondAffiche = -1;

    /** Fonds d'écran : un par jour, en alternant plage et espace. */
    static final int[] FONDS = {
            R.drawable.plage_1, R.drawable.espace_1, R.drawable.plage_2, R.drawable.espace_2,
            R.drawable.plage_3, R.drawable.espace_3, R.drawable.plage_4, R.drawable.espace_4,
            R.drawable.plage_5, R.drawable.espace_5, R.drawable.plage_6, R.drawable.espace_6,
            R.drawable.plage_7, R.drawable.espace_7};

    /** Choisit le fond du jour (heure locale) ; ne recharge l'image que si le jour a changé. */
    private void fondDuJour() {
        long now = System.currentTimeMillis();
        long jour = (now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L;
        int i = (int) (jour % FONDS.length);
        if (i != fondAffiche) {
            fond.setImageResource(FONDS[i]);
            fondAffiche = i;
        }
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rafraichirMeteo = new Runnable() {
        @Override public void run() {
            chargerMeteo();
            fondDuJour();   // si l'écran d'accueil reste affiché après minuit
            handler.postDelayed(this, METEO_PERIODE);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        police = getResources().getFont(R.font.quicksand);
        prefs = getSharedPreferences("lanceur", MODE_PRIVATE);
        FrameLayout racine = new FrameLayout(this);

        fond = new ImageView(this);
        fond.setScaleType(ImageView.ScaleType.CENTER_CROP);
        racine.addView(fond, new FrameLayout.LayoutParams(-1, -1));

        // Voile sombre en haut et en bas pour la lisibilité du texte.
        View voile = new View(this);
        voile.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x80000000, 0x00000000, 0x00000000, 0x55000000}));
        racine.addView(voile, new FrameLayout.LayoutParams(-1, -1));

        // Heure et date, en haut à gauche.
        LinearLayout horloge = new LinearLayout(this);
        horloge.setOrientation(LinearLayout.VERTICAL);
        horloge.addView(texteHorloge("HH:mm", 40));
        horloge.addView(texteHorloge("EEEE d MMMM", 16));
        FrameLayout.LayoutParams lpH = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lpH.setMargins(dp(40), dp(24), 0, 0);
        racine.addView(horloge, lpH);

        // Météo, en haut à droite.
        LinearLayout meteo = new LinearLayout(this);
        meteo.setOrientation(LinearLayout.VERTICAL);
        meteo.setGravity(Gravity.END);
        LinearLayout ligne = new LinearLayout(this);
        ligne.setGravity(Gravity.CENTER_VERTICAL);
        meteoIcone = texte("", 28);
        meteoTemp = texte("--°", 34);
        meteoTemp.setPadding(dp(10), 0, 0, 0);
        ligne.addView(meteoIcone);
        ligne.addView(meteoTemp);
        meteo.addView(ligne);
        meteoDetail = texte(Config.VILLE, 15);
        meteoDetail.setGravity(Gravity.END);
        meteo.addView(meteoDetail);
        FrameLayout.LayoutParams lpM = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lpM.setMargins(0, dp(24), dp(40), 0);
        racine.addView(meteo, lpM);

        // Les 3 tuiles principales, sur une ligne au centre.
        LinearLayout principales = new LinearLayout(this);
        principales.setClipChildren(false);
        tuileTelevizo = tuile(icone(PKG_TELEVIZO), null, "Televizo", v -> lancer(PKG_TELEVIZO));
        principales.addView(tuileTelevizo);
        principales.addView(tuile(icone(PKG_TV), null, "Chaînes TV", v -> lancer(PKG_TV)));
        principales.addView(tuile(null, "🔌", "HDMI", v -> choisirHdmi()));
        FrameLayout.LayoutParams lpP = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        lpP.setMargins(0, 0, 0, dp(30));
        racine.addView(principales, lpP);

        // Barre du bas : applis ajoutées (défile horizontalement), puis ＋ et ⚙.
        LinearLayout bas = new LinearLayout(this);
        bas.setGravity(Gravity.CENTER_VERTICAL);
        bas.setClipChildren(false);
        HorizontalScrollView defil = new HorizontalScrollView(this);
        defil.setHorizontalScrollBarEnabled(false);
        defil.setClipChildren(false);
        defil.setClipToPadding(false);
        GradientDrawable barre = new GradientDrawable();
        barre.setColor(0x1AFFFFFF);
        barre.setCornerRadius(dp(26));
        barre.setStroke(dp(1), 0x33FFFFFF);
        defil.setBackground(barre);
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        dock.setPadding(dp(8), 0, dp(8), 0);
        defil.addView(dock);
        bas.addView(defil, new LinearLayout.LayoutParams(0, -2, 1f));
        boutonPlus = tuile(null, "＋", null, v -> choisirAppli());
        bas.addView(boutonPlus);
        bas.addView(tuile(null, "⚙", null, v -> startActivity(new Intent(Settings.ACTION_SETTINGS))));
        FrameLayout.LayoutParams lpB = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        lpB.setMargins(dp(40), 0, dp(26), dp(20));
        racine.addView(bas, lpB);

        // Nom de l'appli sélectionnée dans la barre, juste au-dessus.
        nomDock = texte("", 15);
        FrameLayout.LayoutParams lpN = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        lpN.setMargins(dp(56), 0, 0, dp(116));
        racine.addView(nomDock, lpN);

        construireDock(null);
        setContentView(racine);
        afficherMeteo(prefs.getString("meteo", null));   // dernière météo connue, en attendant le réseau
        tuileTelevizo.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fondDuJour();
        handler.removeCallbacks(rafraichirMeteo);
        handler.post(rafraichirMeteo);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(rafraichirMeteo);
    }

    /** L'écran d'accueil ne se ferme pas avec Retour. */
    @Override
    public void onBackPressed() { }

    // ---------- Textes ----------

    private TextView texte(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(police);
        t.setShadowLayer(6, 0, 2, 0xAA000000);
        return t;
    }

    private TextClock texteHorloge(String format, int sp) {
        TextClock c = new TextClock(this);
        c.setFormat24Hour(format);
        c.setFormat12Hour(format);
        c.setTextColor(Color.WHITE);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        c.setTypeface(police);
        c.setShadowLayer(6, 0, 2, 0xAA000000);
        return c;
    }

    // ---------- Grille ----------

    /** Tuiles ajoutées par l'utilisateur, dans l'ordre. */
    private List<String> tuilesPerso() {
        String s = prefs.getString("tuiles", "");
        List<String> l = new ArrayList<>();
        if (!s.isEmpty()) l.addAll(Arrays.asList(s.split(",")));
        return l;
    }

    private void enregistrerTuiles(List<String> l) {
        prefs.edit().putString("tuiles", String.join(",", l)).apply();
    }

    /** (Re)construit la barre des applis ajoutées ; focusPkg = icône à sélectionner ensuite. */
    private void construireDock(String focusPkg) {
        dock.removeAllViews();
        View focus = null;
        PackageManager pm = getPackageManager();
        for (String pkg : tuilesPerso()) {
            String nom;
            try {
                nom = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
            } catch (PackageManager.NameNotFoundException e) {
                continue;   // appli désinstallée entre-temps
            }
            View t = tuile(icone(pkg), null, null, v -> lancer(pkg));
            View.OnFocusChangeListener anim = t.getOnFocusChangeListener();
            t.setOnFocusChangeListener((v, f) -> {
                anim.onFocusChange(v, f);
                nomDock.setText(f ? nom : "");
            });
            t.setOnLongClickListener(v -> { retirer(pkg, nom); return true; });
            dock.addView(t);
            if (pkg.equals(focusPkg)) focus = t;
        }
        if (dock.getChildCount() == 0) {
            TextView aide = texte("Appuie sur ＋ pour ajouter une appli ici", 15);
            aide.setAlpha(0.75f);
            aide.setPadding(dp(16), dp(26), dp(16), dp(26));
            dock.addView(aide);
        }
        if (focusPkg != null) (focus != null ? focus : boutonPlus).requestFocus();
    }

    private View tuile(Drawable img, String symbole, String libelle, View.OnClickListener action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        boolean petite = libelle == null;

        GradientDrawable verre = new GradientDrawable();
        verre.setColor(VERRE);
        verre.setCornerRadius(dp(petite ? 32 : 18));
        verre.setStroke(dp(1), BORD);
        t.setBackground(verre);

        if (img != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(img);
            int ic = petite ? dp(40) : dp(56);
            t.addView(iv, new LinearLayout.LayoutParams(ic, ic));
        } else {
            TextView sym = new TextView(this);
            sym.setText(symbole);
            sym.setTextColor(Color.WHITE);
            sym.setTextSize(TypedValue.COMPLEX_UNIT_SP, petite ? 28 : 40);
            sym.setGravity(Gravity.CENTER);
            t.addView(sym);
        }
        if (!petite) {
            TextView tv = texte(libelle, 20);
            tv.setLetterSpacing(0.03f);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setPadding(dp(8), dp(8), dp(8), 0);
            t.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        }

        t.setOnFocusChangeListener((v, focus) -> {
            verre.setColor(focus ? VERRE_FOCUS : VERRE);
            verre.setStroke(dp(focus ? 2 : 1), focus ? Color.WHITE : BORD);
            v.animate().scaleX(focus ? 1.08f : 1f).scaleY(focus ? 1.08f : 1f).setDuration(150).start();
        });
        t.setOnClickListener(action);

        LinearLayout.LayoutParams lp = petite
                ? new LinearLayout.LayoutParams(dp(64), dp(64))
                : new LinearLayout.LayoutParams(dp(200), dp(130));
        int m = petite ? dp(10) : dp(16);
        lp.setMargins(m, m, m, m);
        t.setLayoutParams(lp);
        return t;
    }

    private Drawable icone(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return getDrawable(android.R.drawable.sym_def_app_icon);
        }
    }

    private void lancer(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(this, "Appli introuvable : " + pkg, Toast.LENGTH_LONG).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---------- Ajout / retrait de tuiles ----------

    /** Une appli proposée dans la liste « Ajouter ». */
    static class Appli {
        String pkg, nom;
        Drawable icone;
        @Override public String toString() { return nom; }
    }

    private void choisirAppli() {
        PackageManager pm = getPackageManager();
        List<String> exclues = new ArrayList<>(tuilesPerso());
        exclues.addAll(Arrays.asList(getPackageName(), PKG_TELEVIZO, PKG_TV));
        List<Appli> applis = new ArrayList<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (exclues.contains(pkg)) continue;
                exclues.add(pkg);
                Appli a = new Appli();
                a.pkg = pkg;
                a.nom = String.valueOf(ri.loadLabel(pm));
                a.icone = ri.loadIcon(pm);
                applis.add(a);
            }
        }
        if (applis.isEmpty()) {
            Toast.makeText(this, "Toutes les applis ont déjà une tuile", Toast.LENGTH_LONG).show();
            return;
        }
        Collections.sort(applis, (x, y) -> x.nom.compareToIgnoreCase(y.nom));

        ArrayAdapter<Appli> ad = new ArrayAdapter<Appli>(this, android.R.layout.simple_list_item_1, applis) {
            @Override public View getView(int pos, View v, ViewGroup parent) {
                TextView r = (TextView) super.getView(pos, v, parent);
                Drawable ic = getItem(pos).icone.getConstantState() != null
                        ? getItem(pos).icone.getConstantState().newDrawable().mutate() : getItem(pos).icone;
                ic.setBounds(0, 0, dp(32), dp(32));
                r.setCompoundDrawables(ic, null, null, null);
                r.setCompoundDrawablePadding(dp(16));
                return r;
            }
        };
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Ajouter une tuile")
                .setAdapter(ad, (d, pos) -> {
                    List<String> l = tuilesPerso();
                    l.add(applis.get(pos).pkg);
                    enregistrerTuiles(l);
                    construireDock(applis.get(pos).pkg);
                })
                .show();
    }

    private void retirer(String pkg, String nom) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Retirer la tuile « " + nom + " » ?")
                .setMessage("L'appli reste installée : seule la tuile disparaît.")
                .setPositiveButton("Retirer", (d, w) -> {
                    List<String> l = tuilesPerso();
                    l.remove(pkg);
                    enregistrerTuiles(l);
                    construireDock("");
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ---------- HDMI ----------

    /** Un port HDMI vu par la télé (son état de branchement n'est pas fiable sur cette TCL). */
    static class Port {
        String id, nom;
        @Override public String toString() { return nom; }
    }

    private List<Port> ports() {
        List<Port> res = new ArrayList<>();
        TvInputManager tim = getSystemService(TvInputManager.class);
        if (tim == null) return res;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.getType() != TvInputInfo.TYPE_HDMI || info.getParentId() != null) continue;
            Port p = new Port();
            p.id = info.getId();
            p.nom = String.valueOf(info.loadLabel(this));
            res.add(p);
        }
        Collections.sort(res, (x, y) -> x.nom.compareTo(y.nom));
        return res;
    }

    private void choisirHdmi() {
        final List<Port> ports = ports();
        if (ports.isEmpty()) {
            Toast.makeText(this, "Aucune entrée HDMI détectée", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Entrée HDMI")
                .setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, ports),
                        (d, pos) -> ouvrirHdmi(ports.get(pos).id))
                .show();
    }

    private void ouvrirHdmi(String id) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "ouverture HDMI impossible", e);
            Toast.makeText(this, "Impossible d'ouvrir cette entrée", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Météo ----------

    private void chargerMeteo() {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(METEO_URL).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                }
                String json = sb.toString();
                new JSONObject(json);   // vérifie que la réponse est valide avant de l'enregistrer
                prefs.edit().putString("meteo", json).apply();
                runOnUiThread(() -> afficherMeteo(json));
            } catch (Exception e) {
                Log.w(TAG, "météo indisponible", e);   // on garde la dernière valeur affichée
            }
        }).start();
    }

    private void afficherMeteo(String json) {
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            JSONObject cur = o.getJSONObject("current");
            JSONObject day = o.getJSONObject("daily");
            int code = cur.getInt("weather_code");
            boolean jour = cur.optInt("is_day", 1) == 1;
            meteoIcone.setText(icone(code, jour));
            meteoTemp.setText(Math.round(cur.getDouble("temperature_2m")) + "°");
            meteoDetail.setText(Config.VILLE + " · " + description(code) + "\nmax "
                    + Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)) + "° / min "
                    + Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)) + "°");
        } catch (Exception e) {
            Log.w(TAG, "météo illisible", e);
        }
    }

    /** Codes météo WMO utilisés par Open-Meteo. */
    static String icone(int c, boolean jour) {
        if (c == 0) return jour ? "☀️" : "🌙";
        if (c <= 2) return jour ? "🌤️" : "🌙";
        if (c == 3) return "☁️";
        if (c == 45 || c == 48) return "🌫️";
        if (c >= 51 && c <= 57) return "🌦️";
        if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return "🌧️";
        if ((c >= 71 && c <= 77) || c == 85 || c == 86) return "🌨️";
        if (c >= 95) return "⛈️";
        return "🌡️";
    }

    static String description(int c) {
        if (c == 0) return "Ciel dégagé";
        if (c <= 2) return "Éclaircies";
        if (c == 3) return "Couvert";
        if (c == 45 || c == 48) return "Brouillard";
        if (c >= 51 && c <= 57) return "Bruine";
        if (c >= 61 && c <= 67) return "Pluie";
        if (c >= 71 && c <= 77) return "Neige";
        if (c >= 80 && c <= 82) return "Averses";
        if (c == 85 || c == 86) return "Averses de neige";
        if (c >= 95) return "Orage";
        return "";
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
