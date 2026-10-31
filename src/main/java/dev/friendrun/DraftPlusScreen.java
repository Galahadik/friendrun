package dev.friendrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Separate Draft+ board, ban and pick screen. */
public final class DraftPlusScreen extends Screen {
    private Ui.R panel, leftRect, rightRect;
    private Button leftButton, rightButton;

    public DraftPlusScreen() {
        super(Component.literal("FriendRun — Draft+"));
    }

    private Button make(Ui.R rect, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
            .bounds(rect.x(), rect.y(), rect.w(), rect.h()).build());
        Ui.invisible(button);
        return button;
    }

    @Override
    protected void init() {
        int pw = Math.min(width - 20, 570);
        int ph = Math.min(height - 20, 330);
        int px = (width - pw) / 2, py = (height - ph) / 2;
        panel = new Ui.R(px, py, pw, ph);
        int boardW = 5 * 34 + 4 * 4;
        int optionsX = px + 18 + boardW + 18;
        int optionW = Math.max(92, (px + pw - 12 - optionsX - 8) / 2);
        leftRect = new Ui.R(optionsX, py + 82, optionW, 190);
        rightRect = new Ui.R(optionsX + optionW + 8, py + 82, optionW, 190);
        leftButton = make(leftRect, () -> {
            DraftPlusFlow.State state = FriendRunClient.STATE.draftPlusState;
            if (state == null) return;
            if (state.phase() == DraftPlusFlow.Phase.BANS) FriendRunClient.submitDraftPlusBan(state.offerA());
            else if (state.phase() == DraftPlusFlow.Phase.DRAFT) FriendRunClient.submitDraftPlusPick(0);
        });
        rightButton = make(rightRect, () -> {
            DraftPlusFlow.State state = FriendRunClient.STATE.draftPlusState;
            if (state == null) return;
            if (state.phase() == DraftPlusFlow.Phase.BANS) FriendRunClient.submitDraftPlusBan(state.offerB());
            else if (state.phase() == DraftPlusFlow.Phase.DRAFT) FriendRunClient.submitDraftPlusPick(1);
        });
    }

    @Override
    public void onClose() {
        FriendRunClient.dismissDraftPlusScreen();
        Mc.setScreen(null);
    }

    private void drawGoal(GuiGraphicsExtractor g, Ui.R rect, String encoded, boolean hover,
                          boolean enabled, int index, boolean ban) {
        Ui.button(g, rect, hover, enabled, enabled ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        DraftGoal goal = DraftGoal.parse(encoded);
        if (goal == null) {
            Gfx.centered(g, "Цель недоступна", rect.x() + rect.w() / 2, rect.y() + 82, Ui.MUTED);
            return;
        }
        var icon = goal.icon();
        if (icon != null) {
            int ix = rect.x() + (rect.w() - 24) / 2;
            var stack = ItemInfo.stack(icon);
            if (stack != null) Gfx.item(g, stack, ix + 4, rect.y() + 22);
            else Gfx.itemTexture(g, icon, ix + 4, rect.y() + 22, 16);
        }
        String category = switch (goal.type()) {
            case ITEM -> "ПРЕДМЕТ";
            case QUEST -> "ЗАДАНИЕ";
            case ADVANCEMENT -> "ДОСТИЖЕНИЕ";
        };
        Gfx.centered(g, category, rect.x() + rect.w() / 2, rect.y() + 52, Ui.MUTED);
        Gfx.fitted(g, goal.displayName(), rect.x() + rect.w() / 2, rect.y() + 78, rect.w() - 12, Ui.TEXT);
        Gfx.centered(g, ban ? "ЗАБАНИТЬ " + (index + 1) : "ВЫБРАТЬ " + (index + 1),
            rect.x() + rect.w() / 2, rect.y() + 151, enabled ? Ui.GOLD : Ui.MUTED);
    }

    private void drawBoard(GuiGraphicsExtractor g, DraftPlusFlow.State state, int x, int y) {
        int cell = 34, gap = 4;
        for (int i = 0; i < DraftPlusRules.CELL_COUNT; i++) {
            int cx = x + (i % 5) * (cell + gap);
            int cy = y + (i / 5) * (cell + gap);
            Gfx.rect(g, cx, cy, cx + cell, cy + cell, 0xFF111A27);
            Gfx.rect(g, cx, cy, cx + cell, cy + 1, Ui.LINE);
            if (i >= state.boardGoals().size()) {
                Gfx.centered(g, "·", cx + cell / 2, cy + 12, Ui.MUTED);
                continue;
            }
            DraftGoal goal = DraftGoal.parse(state.boardGoals().get(i));
            if (goal != null && goal.icon() != null) {
                var stack = ItemInfo.stack(goal.icon());
                if (stack != null) Gfx.item(g, stack, cx + 9, cy + 3);
                else Gfx.itemTexture(g, goal.icon(), cx + 9, cy + 3, 16);
            }
            String owner = state.owners().get(i);
            int color = 0xFF9BA9BC;
            if (state.format() == DraftPlusRules.Format.TEAM) {
                int team = state.players().stream().filter(p -> p.id().equals(owner))
                    .map(DraftPlusRules.Player::teamIndex).findFirst().orElse(-1);
                color = team == DraftPlusTeams.TEAM_A ? Ui.ORANGE : team == DraftPlusTeams.TEAM_B ? Ui.GOLD : color;
            } else {
                int playerIndex = 0;
                for (int p = 0; p < state.players().size(); p++)
                    if (state.players().get(p).id().equals(owner)) { playerIndex = p; break; }
                color = Ui.mix(Ui.ORANGE, Ui.GOLD, state.players().size() <= 1 ? 0 : playerIndex / (float) (state.players().size() - 1));
            }
            Gfx.rect(g, cx + 2, cy + cell - 4, cx + cell - 2, cy + cell - 2, color);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        DraftPlusFlow.State state = FriendRunClient.STATE.draftPlusState;
        Gfx.rect(g, 0, 0, width, height, 0xB5080D14);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        if (state == null) {
            String error = FriendRunClient.STATE.draftPlusStartError;
            Gfx.centered(g, error.isBlank() ? "Ожидание состояния Draft+…" : "Не удалось начать Draft+: " + error,
                width / 2, height / 2, error.isBlank() ? Ui.MUTED : Ui.RED);
            super.extractRenderState(g, mx, my, delta);
            return;
        }

        String format = state.format() == DraftPlusRules.Format.FFA ? "FFA" : "КОМАНДЫ";
        String phase = switch (state.phase()) {
            case BANS -> "БАНЫ";
            case DRAFT -> "ДРАФТ";
            case COMPLETE -> "ЗАВЕРШЕНО";
        };
        Gfx.centered(g, "DRAFT+ · " + format + " · " + phase, width / 2, panel.y() + 10, Ui.GOLD);
        Gfx.centered(g, "Поле 5×5 · выбрано " + state.boardGoals().size() + " / 25 · банов " + state.bannedGoals().size(),
            width / 2, panel.y() + 27, Ui.TEXT);

        boolean myTurn = FriendRunClient.myName().equals(state.currentActor());
        boolean active = myTurn && state.phase() != DraftPlusFlow.Phase.COMPLETE;
        String status;
        if (state.phase() == DraftPlusFlow.Phase.COMPLETE) {
            if (state.winner().isBlank()) status = "Поле заполнено — ничья";
            else if (state.winner().startsWith("player:")) status = "Победитель: " + state.winner().substring("player:".length());
            else status = "Победитель: " + (state.winner().equals("team:0")
                ? FriendRunClient.STATE.draftPlusTeamNameA : FriendRunClient.STATE.draftPlusTeamNameB);
        } else {
            status = myTurn
                ? (state.phase() == DraftPlusFlow.Phase.BANS ? "Твой ход — выбери цель для бана" : "Твой ход — выбери цель на поле")
                : "Сейчас выбирает: " + state.currentActor();
        }
        Gfx.centered(g, status, width / 2, panel.y() + 46, active ? Ui.GREEN : Ui.MUTED);
        drawBoard(g, state, panel.x() + 18, panel.y() + 82);

        boolean ban = state.phase() == DraftPlusFlow.Phase.BANS;
        if (state.phase() == DraftPlusFlow.Phase.COMPLETE) {
            Gfx.centered(g, "Больше выборов нет", leftRect.x() + (rightRect.x() + rightRect.w() - leftRect.x()) / 2,
                panel.y() + 170, Ui.MUTED);
        } else {
            drawGoal(g, leftRect, state.offerA(), leftRect.in(mx, my), active, 0, ban);
            drawGoal(g, rightRect, state.offerB(), rightRect.in(mx, my), active, 1, ban);
        }
        if (leftButton != null) leftButton.active = active;
        if (rightButton != null) rightButton.active = active;
        super.extractRenderState(g, mx, my, delta);
    }
}
