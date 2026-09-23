package com.machine.benchmap;

import java.util.Calendar;

/**
 * MapRenderStyle - Différents styles artistiques et cartographiques pour BenchMap.
 * Permet d'explorer Montréal sous un nouvel angle chaque jour :
 * - Standard Épuré
 * - Tracé d'architecte (Thin)
 * - Bauhaus Graphique (Bold / Thick)
 * - Cadastre Tireté (Dashed)
 * - Boulevards Doubles (Double casing)
 * - Atelier Pinceau (Artistic / Brush strokes)
 * - Matrice Ponctuelle (Dotted)
 * - Noir & Blanc Argentique (Desaturated)
 */
public enum MapRenderStyle {
    SWISS_CLEAN(
            "swiss_clean",
            "Standard Épuré",
            "Élégant, équilibré, lisibilité maximale",
            "🎨"
    ),
    THIN_ARCHITECTURAL(
            "thin_archi",
            "Tracé d'architecte (Thin)",
            "Lignes ultra-fines techniques, précision d'épure",
            "📐"
    ),
    BOLD_BAUHAUS(
            "bold_bauhaus",
            "Bauhaus Graphique (Bold)",
            "Lignes épaisses denses, affiches géométriques",
            "⬛"
    ),
    DASHED_CADASTRAL(
            "dashed_cadastral",
            "Cadastre Tireté (Dashed)",
            "Lignes en tirets d'arpenteur, contours pointillés",
            "✂️"
    ),
    DOUBLE_CASING(
            "double_casing",
            "Boulevards Doubles (Double)",
            "Grands axes à double contour parisien & montréalais",
            "🛣️"
    ),
    ARTISTIC_BRUSH(
            "artistic_brush",
            "Atelier Pinceau (Brush)",
            "Traits organiques, gravure et esquisse à l'encre",
            "🖌️"
    ),
    DOTTED_MATRIX(
            "dotted_matrix",
            "Matrice Ponctuelle (Dotted)",
            "Réseau et parcelles en stipple pointilliste",
            "⚪"
    ),
    DESATURATED_NOIR(
            "desaturated_noir",
            "Noir & Blanc Argentique (Noir)",
            "Monochromie pure, ambiance cinéma & sels d'argent",
            "🎬"
    );

    public final String id;
    public final String title;
    public final String subtitle;
    public final String emoji;

    MapRenderStyle(String id, String title, String subtitle, String emoji) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.emoji = emoji;
    }

    public static MapRenderStyle fromId(String id) {
        if (id == null) return SWISS_CLEAN;
        for (MapRenderStyle s : values()) {
            if (s.id.equalsIgnoreCase(id)) return s;
        }
        return SWISS_CLEAN;
    }

    /**
     * Calcule le style automatique du jour selon le jour de la semaine.
     * Offre un nouveau visage à Montréal chaque matin !
     */
    public static MapRenderStyle getDailyStyle() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        switch (dayOfWeek) {
            case Calendar.MONDAY:    return THIN_ARCHITECTURAL;
            case Calendar.TUESDAY:   return DASHED_CADASTRAL;
            case Calendar.WEDNESDAY: return BOLD_BAUHAUS;
            case Calendar.THURSDAY:  return ARTISTIC_BRUSH;
            case Calendar.FRIDAY:    return DOUBLE_CASING;
            case Calendar.SATURDAY:  return DOTTED_MATRIX;
            case Calendar.SUNDAY:    return DESATURATED_NOIR;
            default:                 return SWISS_CLEAN;
        }
    }
}
