package dev.friendrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Draft+ role picker and host-only match settings. */
public final class DraftPlusRoleScreen extends Screen {
    private final Screen parent;
    private Ui.R panel;
    private final Ui.R[] roleRects = new Ui.R[DraftPlusRole.values().length];
    private final Button[] roleButtons = new Button[DraftPlusRole.values().length];
    private Ui.R rolesToggleRect, ratedRect, chaosRect, countRect, difficultyRect, backRect;
    private Button rolesToggleButton, ratedButton, chaosButton, countButton, difficultyButton, backButton;

    public DraftPlusRoleScreen(Screen parent) {
        super(Component.literal("FriendRun — Настройки Draft+"));
        this.parent = parent;
    }

    private Button make(Ui.R rect, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
            .bounds(rect.x(), rect.y(), rect.w(), rect.h()).build());
        Ui.invisible(button);
        return button;
    }

    private static String myRoleId(RunState state) {
        String me = FriendRunClient.myName();
        return state.players.stream().filter(player -> player.name().equals(me))
            .map(RunState.PlayerInfo::draftPlusRole).findFirst().orElse("");
    }

    private boolean editable() {
        RunState state = FriendRunClient.STATE;
        return FriendRunClient.inLobby() && state.gameMode == Game.DRAFT_PLUS
            && !state.launched && state.phase == RunState.Phase.IDLE;
    }

    private void publishSettings(boolean roles, boolean rated, boolean chaos, int count, int difficulty) {
        FriendRunClient.setDraftPlusSettings(roles, rated, chaos, count, difficulty);
    }

    @Override
    protected void init() {
        int widthPanel = Math.min(width - 20, 480);
        int heightPanel = Math.min(height - 16, 340);
        int x = (width - widthPanel) / 2;
        int y = (height - heightPanel) / 2;
        panel = new Ui.R(x, y, widthPanel, heightPanel);

        int inner = widthPanel - 24;
        int gap = 6;
        int half = (inner - gap) / 2;
        rolesToggleRect = new Ui.R(x + 12, y + 43, half, 22);
        ratedRect = new Ui.R(x + 12 + half + gap, y + 43, half, 22);
        int third = (inner - gap * 2) / 3;
        chaosRect = new Ui.R(x + 12, y + 70, third, 22);
        countRect = new Ui.R(x + 12 + third + gap, y + 70, third, 22);
        difficultyRect = new Ui.R(x + 12 + (third + gap) * 2, y + 70, third, 22);

        rolesToggleButton = make(rolesToggleRect, () -> {
            RunState s = FriendRunClient.STATE;
            publishSettings(!s.draftPlusRolesEnabled, s.draftPlusRated, s.draftPlusChaosEnabled,
                s.draftPlusChaosCount, s.draftPlusChaosDifficulty);
        });
        ratedButton = make(ratedRect, () -> {
            RunState s = FriendRunClient.STATE;
            publishSettings(s.draftPlusRolesEnabled, !s.draftPlusRated, s.draftPlusChaosEnabled,
                s.draftPlusChaosCount, s.draftPlusChaosDifficulty);
        });
        chaosButton = make(chaosRect, () -> {
            RunState s = FriendRunClient.STATE;
            publishSettings(s.draftPlusRolesEnabled, s.draftPlusRated, !s.draftPlusChaosEnabled,
                s.draftPlusChaosCount, s.draftPlusChaosDifficulty);
        });
        countButton = make(countRect, () -> {
            RunState s = FriendRunClient.STATE;
            int next = s.draftPlusChaosCount >= 4 ? 1 : s.draftPlusChaosCount + 1;
            publishSettings(s.draftPlusRolesEnabled, s.draftPlusRated, s.draftPlusChaosEnabled,
                next, s.draftPlusChaosDifficulty);
        });
        difficultyButton = make(difficultyRect, () -> {
            RunState s = FriendRunClient.STATE;
            publishSettings(s.draftPlusRolesEnabled, s.draftPlusRated, s.draftPlusChaosEnabled,
                s.draftPlusChaosCount, (s.draftPlusChaosDifficulty + 1) % 3);
        });

        for (int i = 0; i < DraftPlusRole.values().length; i++) {
            int col = i % 2;
            int row = i / 2;
            Ui.R rect = new Ui.R(x + 12 + col * (half + gap), y + 101 + row * 48, half, 40);
            roleRects[i] = rect;
            DraftPlusRole role = DraftPlusRole.values()[i];
            roleButtons[i] = make(rect, () -> {
                if (!editable() || !FriendRunClient.STATE.draftPlusRolesEnabled) return;
                FriendRunClient.setDraftPlusRole(role.id());
                Mc.setScreen(parent);
            });
        }

        backRect = new Ui.R(x + 12, y + heightPanel - 28, inner, 20);
        backButton = make(backRect, () -> Mc.setScreen(parent));
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        RunState state = FriendRunClient.STATE;
        String selectedId = myRoleId(state);
        boolean canEdit = editable();
        boolean hostCanEdit = canEdit && state.amHost;
        boolean canChooseRole = canEdit && state.draftPlusRolesEnabled;
        String[] difficulty = {"Лёгкая", "Средняя", "Высокая"};
        String count = state.draftPlusChaosCount == 4 ? "Случайно 1–3" : Integer.toString(state.draftPlusChaosCount);

        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.GEAR, panel.x() + 14, panel.y() + 11, 2, Ui.GOLD);
        Ui.bold(g, "Настройки Draft+", panel.x() + 36, panel.y() + 10, Ui.GOLD);
        Gfx.text(g, "Настройки матча меняет только хост", panel.x() + 14, panel.y() + 28, Ui.MUTED);

        super.extractRenderState(g, mx, my, delta);

        Ui.button(g, rolesToggleRect, rolesToggleRect.in(mx, my), hostCanEdit,
            state.draftPlusRolesEnabled ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, "Роли: " + (state.draftPlusRolesEnabled ? "ВКЛ" : "ВЫКЛ"),
            (rolesToggleRect.x() + rolesToggleRect.w() / 2), rolesToggleRect.cy() - 4, hostCanEdit ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, ratedRect, ratedRect.in(mx, my), hostCanEdit,
            state.draftPlusRated ? Ui.Kind.GOLD : Ui.Kind.NORMAL);
        Gfx.centered(g, state.draftPlusRated ? "Рейтинг: ВКЛ" : "Training",
            (ratedRect.x() + ratedRect.w() / 2), ratedRect.cy() - 4, hostCanEdit ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, chaosRect, chaosRect.in(mx, my), hostCanEdit,
            state.draftPlusChaosEnabled ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, "Хаос: " + (state.draftPlusChaosEnabled ? "ВКЛ" : "ВЫКЛ"),
            (chaosRect.x() + chaosRect.w() / 2), chaosRect.cy() - 4, hostCanEdit ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, countRect, countRect.in(mx, my), hostCanEdit && state.draftPlusChaosEnabled, Ui.Kind.NORMAL);
        Gfx.centered(g, "Кол-во: " + count, (countRect.x() + countRect.w() / 2), countRect.cy() - 4,
            hostCanEdit && state.draftPlusChaosEnabled ? Ui.TEXT : Ui.MUTED);
        Ui.button(g, difficultyRect, difficultyRect.in(mx, my), hostCanEdit && state.draftPlusChaosEnabled, Ui.Kind.NORMAL);
        Gfx.centered(g, difficulty[state.draftPlusChaosDifficulty], (difficultyRect.x() + difficultyRect.w() / 2), difficultyRect.cy() - 4,
            hostCanEdit && state.draftPlusChaosEnabled ? Ui.TEXT : Ui.MUTED);

        for (int i = 0; i < DraftPlusRole.values().length; i++) {
            DraftPlusRole role = DraftPlusRole.values()[i];
            Ui.R rect = roleRects[i];
            boolean selected = role.id().equals(selectedId);
            Ui.button(g, rect, rect.in(mx, my), canChooseRole, selected ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
            Gfx.text(g, role.title(), rect.x() + 10, rect.y() + 7, selected ? Ui.GREEN : (canChooseRole ? Ui.TEXT : Ui.MUTED));
            Gfx.text(g, Ui.trunc(role.bonus(), rect.w() - 18), rect.x() + 10, rect.y() + 23, Ui.MUTED);
            roleButtons[i].active = canChooseRole;
        }
        if (!state.draftPlusRolesEnabled)
            Gfx.centered(g, "Роли выключены — бонусы не применяются", (panel.x() + panel.w() / 2), panel.y() + 250, Ui.MUTED);

        Ui.button(g, backRect, backRect.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, "Назад", (backRect.x() + backRect.w() / 2), backRect.cy() - 4, Ui.TEXT);

        rolesToggleButton.active = ratedButton.active = chaosButton.active = hostCanEdit;
        countButton.active = difficultyButton.active = hostCanEdit && state.draftPlusChaosEnabled;
        backButton.active = true;
    }
}
