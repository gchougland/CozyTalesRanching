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

    public static final String ACTION_MOVE_OUT = "MoveOut";
    public static final String ACTION_CALL_BACK = "CallBack";
    public static final String ACTION_MOVE_IN = "MoveIn";
    public static final String ACTION_BREEDING = "Breeding";
    public static final String ACTION_RENAME = "Rename";

    private static final String[] MOOD_FACES = {"Happy", "Okay", "Sad"};

    private HousePageLayout() {}

    public static void render(@Nonnull HouseView view, @Nonnull UiOut ui) {
        ui.append(PAGE);
        ui.text("#HouseTitle", view.title());
        ui.text("#SpeciesLine", Txt.ui("header.species").with("species", view.species()));
        ui.text("#ResidentCount", Txt.ui("header.count").with("count", view.residentCount()).with("capacity", view.capacity()));

        ui.set("#NoticeBox.Visible", view.notice() != null);
        if (view.notice() != null) ui.text("#NoticeText", view.notice());

        residents(view, ui);
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
            String row = "#ResidentList[" + i + "] ";
            ui.append("#ResidentList", RESIDENT_ROW);
            ui.text(row + "#RName", r.name());
            int face = r.mood().ordinal();
            for (int f = 0; f < MOOD_FACES.length; f++) ui.set(row + "#RFace" + MOOD_FACES[f] + ".Visible", f == face);
            ui.text(row + "#RMood", Txt.ui("mood." + r.mood().name().toLowerCase(java.util.Locale.ROOT)));
            ui.set(row + "#RBaby.Visible", r.baby());
            if (r.baby()) ui.text(row + "#RBabyText", Txt.ui("resident.baby"));
            ui.text(row + "#RTraits", traits(r.traits()));
            hearts(ui, row + "#RHeart", r.hearts());
            ui.text(row + "#RWhere", Txt.ui("where." + r.where().name().toLowerCase(java.util.Locale.ROOT)));
            ui.text(row + "#RCallBack", Txt.ui("button.callback"));
            ui.click(row + "#RCallBack", ACTION_CALL_BACK, r.id().toString());
            ui.text(row + "#RMoveOut", Txt.ui("button.moveout"));
            ui.click(row + "#RMoveOut", ACTION_MOVE_OUT, r.id().toString());
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

    @Nonnull
    private static Txt traits(@Nonnull List<Txt> traits) {
        return switch (traits.size()) {
            case 0 -> Txt.raw("");
            case 1 -> traits.getFirst();
            default -> Txt.ui("resident.traits").with("first", traits.get(0)).with("second", traits.get(1));
        };
    }
}
