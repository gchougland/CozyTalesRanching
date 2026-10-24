package com.hexvane.cozytalesranching.items;

import com.hexvane.cozytalesranching.houseui.UiOut;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;

/** Writes a {@link LedgerView} as the commands of {@code CozyRanch/LedgerPage.ui}. The page is sent whole. */
public final class LedgerLayout {
    public static final String PAGE = "CozyRanch/LedgerPage.ui";
    public static final String HOUSE = "CozyRanch/LedgerHouse.ui";
    public static final String ANIMAL = "CozyRanch/LedgerAnimal.ui";
    public static final String SECTION = "CozyRanch/LedgerSection.ui";
    /** The house page's product chip, reused. */
    public static final String CHIP = "CozyRanch/HouseProductChip.ui";
    public static final String ACTION_JOURNAL = "Journal";
    /** Chips that fit on a house card; with more kinds, the last place says "+N more". */
    public static final int MAX_CHIPS = 6;

    private LedgerLayout() {}

    public static void render(@Nonnull LedgerView view, @Nonnull UiOut ui) {
        ui.append(PAGE);
        ui.text("#LedgerTitle", LedgerView.t("title"));
        ui.text("#LedgerSummary", LedgerView.t("summary").with("animals", view.animals()).with("houses", view.houses()).with("waiting", view.waiting()));
        ui.text("#LedgerJournal", LedgerView.t("button.journal"));
        ui.click("#LedgerJournal", ACTION_JOURNAL, "");
        boolean empty = view.houseList().isEmpty() && view.loose().isEmpty();
        ui.set("#LedgerList.Visible", !empty);
        ui.set("#LedgerEmpty.Visible", empty);
        ui.text("#LedgerEmptyText", LedgerView.t("empty"));
        ui.clear("#LedgerList");
        int i = 0;
        for (LedgerView.House h : view.houseList()) {
            String card = "#LedgerList[" + i++ + "] ";
            ui.append("#LedgerList", HOUSE);
            ui.text(card + "#LHName", h.name());
            ui.text(card + "#LHCount", LedgerView.t("house.count").with("count", h.residents()).with("capacity", h.capacity()));
            ui.text(card + "#LHWhere", LedgerView.t("house.line").with("species", h.species()).with("where", h.where()));
            List<LedgerView.Product> products = h.products();
            int shown = products.size() > MAX_CHIPS ? MAX_CHIPS - 1 : products.size();
            for (int k = 0; k < shown; k++) {
                String chip = card + "#LHProducts[" + k + "] ";
                ui.append(card + "#LHProducts", CHIP);
                ui.set(chip + "#PIcon.ItemId", products.get(k).itemId());
                ui.text(chip + "#PCount", LedgerView.t("products.count").with("count", products.get(k).count()));
            }
            boolean more = products.size() > shown;
            ui.set(card + "#LHMore.Visible", more);
            if (more) ui.text(card + "#LHMore", LedgerView.t("products.more").with("count", products.size() - shown));
            ui.set(card + "#LHNoProducts.Visible", products.isEmpty());
            if (products.isEmpty()) ui.text(card + "#LHNoProducts", LedgerView.t(h.residents() == 0 ? "house.empty" : "products.none"));
            for (LedgerView.Animal a : h.animals()) animal(ui, i++, a);
        }
        if (!view.loose().isEmpty()) {
            section(ui, i++, LedgerView.t("loose.title"));
            for (LedgerView.Animal a : view.loose()) animal(ui, i++, a);
            if (view.looseMore() > 0) section(ui, i++, LedgerView.t("loose.more").with("count", view.looseMore()));
        }
    }

    private static void section(@Nonnull UiOut ui, int index, @Nonnull com.hexvane.cozytalesranching.houseui.Txt text) {
        ui.append("#LedgerList", SECTION);
        ui.text("#LedgerList[" + index + "] #LSText", text);
    }

    private static void animal(@Nonnull UiOut ui, int index, @Nonnull LedgerView.Animal a) {
        String row = "#LedgerList[" + index + "] ";
        ui.append("#LedgerList", ANIMAL);
        ui.text(row + "#LAName", a.name());
        ui.text(row + "#LASpecies", a.species());
        ui.set(row + "#LABaby.Visible", a.baby());
        if (a.baby()) ui.text(row + "#LABabyText", LedgerView.t("baby"));
        for (int h = 1; h <= LedgerView.MAX_HEARTS; h++) ui.set(row + "#LAHeart" + h + ".Visible", h <= a.hearts());
        ui.text(row + "#LAWhere", LedgerView.t("where." + a.where().name().toLowerCase(Locale.ROOT)));
    }
}
