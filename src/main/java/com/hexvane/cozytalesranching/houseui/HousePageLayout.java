package com.hexvane.cozytalesranching.houseui;

import java.util.List;
import javax.annotation.Nonnull;

/**
 * Writes a {@link HouseView} as the commands and button bindings of {@code CozyRanch/HousePage.ui}. Every build sends
 * the whole page (the page is rebuilt after each action), so each element's state is set in every branch.
 */
public final class HousePageLayout {
    public static final String PAGE = "CozyRanch/HousePage.ui";
    public static final String RESIDENT_ROW = "CozyRanch/HouseResidentRow.ui";
    public static final String FOLLOWER_ROW = "CozyRanch/HouseFollowerRow.ui";
    public static final String MISSING_ROW = "CozyRanch/HouseMissingRow.ui";
    public static final String PRODUCT_CHIP = "CozyRanch/HouseProductChip.ui";
    public static final String MOVE_ROW = "CozyRanch/HouseMoveRow.ui";

    public static final String ACTION_MOVE_OUT = "MoveOut";
    public static final String ACTION_CALL_BACK = "CallBack";
    public static final String ACTION_MOVE_IN = "MoveIn";
    public static final String ACTION_BREEDING = "Breeding";
    public static final String ACTION_RENAME = "Rename";
    public static final String ACTION_SELECT = "Select";
    public static final String ACTION_LOCATE = "Locate";
    public static final String ACTION_PICK_HOUSE = "PickHouse";
    public static final String ACTION_MOVE_TO = "MoveTo";
    public static final String ACTION_CANCEL_MOVE = "CancelMove";
    public static final String ACTION_COLLECT = "Collect";
    public static final String ACTION_JOURNAL = "Journal";
    public static final String ACTION_REFRESH = "Refresh";
    public static final String ACTION_BORDER = "Border";
    public static final String ACTION_NAME = "Name";

    /** Product chips that fit beside the Collect button; with more kinds, one chip's place shows "+N more". */
    public static final int MAX_CHIPS = 5;

    private static final String[] MOOD_FACES = {"Happy", "Okay", "Sad"};

    private HousePageLayout() {}

    public static void render(@Nonnull HouseView view, @Nonnull UiOut ui) {
        ui.append(PAGE);
        ui.text("#HouseTitle", view.title());
        ui.text("#SpeciesLine", Txt.ui("header.species").with("species", view.species()));
        ui.text("#ResidentCount", Txt.ui("header.count").with("count", view.residentCount()).with("capacity", view.capacity()));
        ui.text("#JournalButton", Txt.ui("button.journal"));
        ui.click("#JournalButton", ACTION_JOURNAL, "");

        ui.set("#NoticeBox.Visible", view.notice() != null);
        if (view.notice() != null) ui.text("#NoticeText", view.notice());

        products(view, ui);
        residents(view, ui);
        selected(view, ui);
        followers(view, ui);
        habitat(view, ui);

        ui.text("#BreedingTitle", Txt.ui("breeding.title"));
        ui.text("#BreedingState", Txt.ui(view.breeding() ? "breeding.on" : "breeding.off"));
        ui.text("#BreedingToggle", Txt.ui(view.breeding() ? "breeding.turnoff" : "breeding.turnon"));
        ui.click("#BreedingToggle", ACTION_BREEDING, "");

        ui.text("#RenameTitle", Txt.ui("rename.title"));
        ui.set("#RenameField.Value", view.customName() == null ? "" : view.customName());
        ui.text("#RenameButton", Txt.ui("rename.button"));
        ui.clickWithField("#RenameButton", ACTION_RENAME, "#RenameField");
    }

    private static void products(@Nonnull HouseView view, @Nonnull UiOut ui) {
        ui.text("#ProductsTitle", Txt.ui("products.title"));
        ui.clear("#ProductList");
        List<HouseView.Product> products = view.products();
        int shown = products.size() > MAX_CHIPS ? MAX_CHIPS - 1 : products.size();
        for (int i = 0; i < shown; i++) {
            HouseView.Product p = products.get(i);
            String chip = "#ProductList[" + i + "] ";
            ui.append("#ProductList", PRODUCT_CHIP);
            ui.set(chip + "#PIcon.ItemId", p.itemId());
            ui.text(chip + "#PCount", Txt.ui("products.count").with("count", p.count()));
        }
        boolean more = products.size() > shown;
        ui.set("#ProductsMore.Visible", more);
        if (more) ui.text("#ProductsMore", Txt.ui("products.more").with("count", products.size() - shown));
        ui.set("#ProductsEmpty.Visible", products.isEmpty());
        if (products.isEmpty()) ui.text("#ProductsEmpty", Txt.ui("products.empty"));
        ui.text("#CollectButton", Txt.ui("button.collect"));
        ui.set("#CollectButton.Disabled", products.isEmpty());
        ui.click("#CollectButton", ACTION_COLLECT, "");
        boolean buckets = view.bucketsNeeded();
        ui.set("#MilkHint.Visible", buckets);
        if (buckets) ui.text("#MilkHint", Txt.ui("products.buckets"));
    }

    private static void residents(@Nonnull HouseView view, @Nonnull UiOut ui) {
        ui.text("#ResidentsTitle", Txt.ui("residents.title"));
        boolean empty = view.residents().isEmpty();
        ui.set("#ResidentList.Visible", !empty);
        ui.set("#EmptyResidents.Visible", empty);
        ui.text("#EmptyResidentsText", Txt.ui("residents.empty").with("species", view.species()));
        ui.clear("#ResidentList");
        List<HouseView.Resident> rows = view.residents();
        for (int i = 0; i < rows.size(); i++) {
            HouseView.Resident r = rows.get(i);
            String list = "#ResidentList[" + i + "]";
            String row = list + " ";
            ui.append("#ResidentList", RESIDENT_ROW);
            ui.set(row + "#RSelected.Visible", view.selected() != null && view.selected().id().equals(r.id()));
            ui.text(row + "#RName", r.name());
            int face = r.mood().ordinal();
            for (int f = 0; f < MOOD_FACES.length; f++) ui.set(row + "#RFace" + MOOD_FACES[f] + ".Visible", f == face);
            ui.text(row + "#RMood", Txt.ui("mood." + r.mood().name().toLowerCase(java.util.Locale.ROOT)));
            ui.set(row + "#RBaby.Visible", r.baby());
            if (r.baby()) ui.text(row + "#RBabyText", Txt.ui("resident.baby"));
            ui.set(row + "#RFeed.Visible", r.feedBonus() != null);
            if (r.feedBonus() != null) ui.text(row + "#RFeedText", r.feedBonus());
            ui.set(row + "#RPersonality.Visible", r.personality() != null);
            if (r.personality() != null) {
                ui.text(row + "#RPersonality", r.personality());
                if (r.personality().key() != null) ui.tooltip(row + "#RPersonality", Txt.key(r.personality().key() + ".desc"));
            }
            traits(ui, row, r.traits());
            ui.set(row + "#RNameButton.Visible", r.nameable());
            if (r.nameable()) {
                ui.text(row + "#RNameButton", Txt.ui("button.name"));
                ui.click(row + "#RNameButton", ACTION_NAME, r.id().toString());
            }
            hearts(ui, row + "#RHeart", r.hearts());
            ui.text(row + "#RWhere", Txt.ui("where." + r.where().name().toLowerCase(java.util.Locale.ROOT)));
            ui.click(list, ACTION_SELECT, r.id().toString());
        }
    }

    /** The bar under the list: the selected resident's actions, or the house picker for Move. */
    private static void selected(@Nonnull HouseView view, @Nonnull UiOut ui) {
        HouseView.Resident r = view.selected();
        ui.set("#DetailCard.Visible", r != null && !view.picking());
        ui.set("#MoveCard.Visible", r != null && view.picking());
        ui.clear("#MoveList");
        if (r == null) return;
        String id = r.id().toString();
        ui.text("#DetailName", r.name());
        ui.text("#LocateButton", Txt.ui("button.locate"));
        ui.click("#LocateButton", ACTION_LOCATE, id);
        ui.text("#ResummonButton", Txt.ui("button.callback"));
        ui.click("#ResummonButton", ACTION_CALL_BACK, id);
        boolean canMove = !view.moveTargets().isEmpty();
        ui.set("#MoveButton.Visible", canMove);
        if (canMove) {
            ui.text("#MoveButton", Txt.ui("button.move"));
            ui.click("#MoveButton", ACTION_PICK_HOUSE, id);
        }
        ui.text("#MoveOutButton", Txt.ui("button.moveout"));
        ui.click("#MoveOutButton", ACTION_MOVE_OUT, id);
        if (!view.picking()) return;
        ui.text("#MoveTitle", Txt.ui("move.title").with("name", r.name()));
        ui.text("#MoveCancel", Txt.ui("button.cancel"));
        ui.click("#MoveCancel", ACTION_CANCEL_MOVE, "");
        List<HouseView.MoveTarget> targets = view.moveTargets();
        for (int i = 0; i < targets.size(); i++) {
            HouseView.MoveTarget t = targets.get(i);
            String row = "#MoveList[" + i + "] ";
            ui.append("#MoveList", MOVE_ROW);
            ui.text(row + "#MVName", t.name());
            ui.text(row + "#MVCount", Txt.ui("header.count").with("count", t.residents()).with("capacity", t.capacity()));
            ui.text(row + "#MVGo", Txt.ui("button.movehere"));
            ui.click(row + "#MVGo", ACTION_MOVE_TO, id, t.houseId().toString());
        }
    }

    private static void followers(@Nonnull HouseView view, @Nonnull UiOut ui) {
        ui.text("#FollowersTitle", Txt.ui("followers.title"));
        boolean none = view.followers().isEmpty();
        ui.set("#FollowerList.Visible", !none);
        ui.set("#NoFollowers.Visible", none);
        ui.text("#NoFollowersText", Txt.ui("followers.empty").with("species", view.species()));
        ui.clear("#FollowerList");
        List<HouseView.Follower> rows = view.followers();
        boolean full = view.full();
        for (int i = 0; i < rows.size(); i++) {
            HouseView.Follower f = rows.get(i);
            String row = "#FollowerList[" + i + "] ";
            ui.append("#FollowerList", FOLLOWER_ROW);
            ui.text(row + "#FName", f.name());
            hearts(ui, row + "#FHeart", f.hearts());
            ui.text(row + "#FMoveIn", Txt.ui(full ? "button.full" : "button.movein"));
            ui.set(row + "#FMoveIn.Disabled", full);
            ui.click(row + "#FMoveIn", ACTION_MOVE_IN, f.id().toString());
        }
    }

    private static void habitat(@Nonnull HouseView view, @Nonnull UiOut ui) {
        HouseView.Habitat h = view.habitat();
        ui.text("#HabitatTitle", Txt.ui("habitat.title"));
        String face = switch (h.level()) {
            case GREAT -> "Happy";
            case GOOD -> "Okay";
            case POOR -> "Sad";
            case UNCHECKED -> "";
        };
        ui.set("#HabitatFace.Visible", !face.isEmpty());
        for (String f : MOOD_FACES) ui.set("#HabitatFace" + f + ".Visible", f.equals(face));
        ui.text("#HabitatSummary", Txt.ui("habitat." + h.level().name().toLowerCase(java.util.Locale.ROOT)));
        String detail = h.level() == HouseView.HabitatLevel.UNCHECKED ? "habitat.unchecked.detail"
            : h.missingRequired().isEmpty() && h.missingPreferred().isEmpty() ? "habitat.detail.none" : "habitat.detail.some";
        ui.text("#HabitatDetail", Txt.ui(detail));
        ui.clear("#HabitatMissing");
        int i = 0;
        for (String id : h.missingRequired()) missingRow(ui, i++, id, true);
        for (String id : h.missingPreferred()) missingRow(ui, i++, id, false);
        ui.text("#RefreshButton", Txt.ui("button.refresh"));
        ui.tooltip("#RefreshButton", Txt.ui("tooltip.refresh"));
        ui.click("#RefreshButton", ACTION_REFRESH, "");
        ui.text("#AreaButton", Txt.ui(view.borderOn() ? "button.area.hide" : "button.area.show"));
        ui.tooltip("#AreaButton", Txt.ui("tooltip.area"));
        ui.click("#AreaButton", ACTION_BORDER, "");
    }

    private static void missingRow(@Nonnull UiOut ui, int index, @Nonnull String featureId, boolean required) {
        String row = "#HabitatMissing[" + index + "] ";
        ui.append("#HabitatMissing", MISSING_ROW);
        ui.set(row + "#MNeed.Visible", required);
        ui.set(row + "#MWant.Visible", !required);
        if (required) ui.text(row + "#MNeed", Txt.ui("habitat.need"));
        else ui.text(row + "#MWant", Txt.ui("habitat.want"));
        ui.text(row + "#MName", Txt.ui("habitat." + featureId));
    }

    private static void hearts(@Nonnull UiOut ui, @Nonnull String prefix, int hearts) {
        for (int h = 1; h <= HouseView.MAX_HEARTS; h++) ui.set(prefix + h + ".Visible", h <= hearts);
    }

    /** One pill per trait ({@code #RTrait1}, {@code #RTrait2}), each with what the trait does as its tooltip. */
    private static void traits(@Nonnull UiOut ui, @Nonnull String row, @Nonnull List<HouseView.Trait> traits) {
        for (int t = 1; t <= HouseView.MAX_TRAITS; t++) {
            boolean has = t <= traits.size();
            String pill = row + "#RTrait" + t;
            ui.set(pill + ".Visible", has);
            if (!has) continue;
            HouseView.Trait trait = traits.get(t - 1);
            ui.text(pill, trait.name());
            ui.tooltip(pill, trait.description());
        }
    }
}
