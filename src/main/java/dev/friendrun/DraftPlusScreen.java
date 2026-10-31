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
            Gfx.centered(g, Tr.t("friendrun.draft_plus.goal_unavailable", "Цель недоступна"), rect.x() + rect.w() / 2, rect.y() + 82, Ui.MUTED);
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
            case ITEM -> Tr.t("friendrun.draft_plus.category.item", "ПРЕДМЕТ");
            case QUEST -> Tr.t("friendrun.draft_plus.category.quest", "ЗАДАНИЕ");
            case ADVANCEMENT -> Tr.t("friendrun.draft_plus.category.advancement", "ДОСТИЖЕНИЕ");
        };
        Gfx.centered(g, category, rect.x() + rect.w() / 2, rect.y() + 52, Ui.MUTED);
        Gfx.fitted(g, goal.displayName(), rect.x() + rect.w() / 2, rect.y() + 78, rect.w() - 12, Ui.TEXT);
        Gfx.centered(g, Tr.t(ban ? "friendrun.draft_plus.action.ban" : "friendrun.draft_plus.action.pick", ban ? "ЗАБАНИТЬ %s" : "ВЫБРАТЬ %s", index + 1),
            rect.x() + rect.w() / 2, rect.y() + 151, enabled ? Ui.GOLD : Ui.MUTED);
    }

    private void drawBoard(GuiGraphicsExtractor g, DraftPlusFlow.State state, java.util.List<DraftGoal> personalGoals, int x, int y) {
        int cell = 34, gap = 4;
        boolean usePersonal = (state.phase() == DraftPlusFlow.Phase.PLAY
            || state.phase() == DraftPlusFlow.Phase.COMPLETE)
            && personalGoals.size() == DraftPlusRules.CELL_COUNT;
        for (int i = 0; i < DraftPlusRules.CELL_COUNT; i++) {
            int cx = x + (i % 5) * (cell + gap);
            int cy = y + (i / 5) * (cell + gap);
            Gfx.rect(g, cx, cy, cx + cell, cy + cell, 0xFF111A27);
            Gfx.rect(g, cx, cy, cx + cell, cy + 1, Ui.LINE);
            if (i >= (usePersonal ? personalGoals.size() : state.boardGoals().size())) {
                Gfx.centered(g, "·", cx + cell / 2, cy + 12, Ui.MUTED);
                continue;
            }
            DraftGoal goal = usePersonal ? personalGoals.get(i) : DraftGoal.parse(state.boardGoals().get(i));
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
            Gfx.centered(g, error.isBlank()
                ? Tr.t("friendrun.draft_plus.waiting_state", "Ожидание состояния Draft+…")
                : Tr.t("friendrun.draft_plus.start_error", "Не удалось начать Draft+: %s", error),
                width / 2, height / 2, error.isBlank() ? Ui.MUTED : Ui.RED);
            super.extractRenderState(g, mx, my, delta);
            return;
        }

        String format = Tr.t(state.format() == DraftPlusRules.Format.FFA ? "friendrun.draft_plus.format.ffa" : "friendrun.draft_plus.format.team", state.format() == DraftPlusRules.Format.FFA ? "FFA" : "Команды");
        String phase = switch (state.phase()) {
            case BANS -> Tr.t("friendrun.draft_plus.phase.bans", "БАНЫ");
            case DRAFT -> Tr.t("friendrun.draft_plus.phase.draft", "ДРАФТ");
            case PLAY -> Tr.t("friendrun.draft_plus.phase.play", "ИГРА");
            case COMPLETE -> Tr.t("friendrun.draft_plus.phase.complete", "ЗАВЕРШЕНО");
        };
        Gfx.centered(g, "DRAFT+ · " + format + " · " + phase, width / 2, panel.y() + 10, Ui.GOLD);
        String chaos = FriendRunClient.STATE.draftPlusChaosModifiers.isEmpty() ? Tr.t("friendrun.draft_plus.chaos.off", "Хаос: выкл")
            : Tr.t("friendrun.draft_plus.chaos.prefix", "Хаос: ") + FriendRunClient.STATE.draftPlusChaosModifiers.stream()
                .map(DraftPlusChaos::displayName).collect(java.util.stream.Collectors.joining(", "));
        String progress = Tr.t("friendrun.draft_plus.progress", "Поле 5×5 · выбрано %s / 25 · банов %s · %s",
            state.boardGoals().size(), state.bannedGoals().size(), chaos);
        Gfx.centered(g, Ui.trunc(progress, panel.w() - 32), width / 2, panel.y() + 27, Ui.TEXT);

        boolean myTurn = FriendRunClient.myName().equals(state.currentActor());
        boolean active = myTurn && state.phase() != DraftPlusFlow.Phase.COMPLETE;
        String status;
        if (state.phase() == DraftPlusFlow.Phase.COMPLETE) {
            if (state.winner().isBlank()) status = Tr.t("friendrun.draft_plus.board_draw", "Поле заполнено — победитель не определён");
            else if (state.winner().startsWith("player:")) status = Tr.t("friendrun.draft_plus.winner.player", "Победитель: %s", state.winner().substring("player:".length()));
            else status = Tr.t("friendrun.draft_plus.winner.team", "Победитель: %s", state.winner().equals("team:0")
                ? FriendRunClient.STATE.draftPlusTeamNameA : FriendRunClient.STATE.draftPlusTeamNameB);
        } else {
            status = myTurn
                ? (state.phase() == DraftPlusFlow.Phase.BANS
                    ? Tr.t("friendrun.draft_plus.turn.ban", "Твой ход — выбери цель для бана")
                    : Tr.t("friendrun.draft_plus.turn.pick", "Твой ход — выбери цель на поле"))
                : Tr.t("friendrun.draft_plus.turn.current", "Сейчас выбирает: %s", state.currentActor());
        }
        Gfx.centered(g, status, width / 2, panel.y() + 46, active ? Ui.GREEN : Ui.MUTED);
        int lastPick = -1;
        for (int i = state.pickers().size() - 1; i >= 0; i--)
            if (!state.pickers().get(i).isBlank()) { lastPick = i; break; }
        String coinWinner = Tr.t("friendrun.draft_plus.after_bans", "после банов");
        if (state.firstDraftActor() >= 0) {
            DraftPlusRules.Player first = DraftPlusRules.drafter(
                state.format(), state.players(), state.firstDraftActor(), 0);
            coinWinner = state.format() == DraftPlusRules.Format.TEAM
                ? (first.teamIndex() == DraftPlusTeams.TEAM_A
                    ? FriendRunClient.STATE.draftPlusTeamNameA : FriendRunClient.STATE.draftPlusTeamNameB)
                : first.id();
        }
        DraftGoal lastGoal = lastPick < 0 ? null : DraftGoal.parse(state.boardGoals().get(lastPick));
        String lastSelection = Tr.t("friendrun.draft_plus.coin.starts", "🪙 Монетка: начинает %s", coinWinner)
            + (lastPick < 0 ? "" : Tr.t("friendrun.draft_plus.last_pick", " · %s выбрал: %s",
                state.pickers().get(lastPick), lastGoal == null ? state.boardGoals().get(lastPick) : lastGoal.displayName()));
        Gfx.centered(g, Ui.trunc(lastSelection, panel.w() - 32), width / 2, panel.y() + 63, Ui.MUTED);
        drawBoard(g, state, FriendRunClient.STATE.draftGoals, panel.x() + 18, panel.y() + 82);

        boolean ban = state.phase() == DraftPlusFlow.Phase.BANS;
        if (state.phase() == DraftPlusFlow.Phase.COMPLETE) {
            Gfx.centered(g, Tr.t("friendrun.draft_plus.no_more_choices", "Больше выборов нет"), leftRect.x() + (rightRect.x() + rightRect.w() - leftRect.x()) / 2,
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
