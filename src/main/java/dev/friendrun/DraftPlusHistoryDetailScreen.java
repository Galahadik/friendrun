package dev.friendrun;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Detailed read-only view of a saved Draft+ match. */
public final class DraftPlusHistoryDetailScreen extends Screen {
    private final Screen parent;
    private final Lobby.DraftPlusHistoryEntry entry;
    private Ui.R panel, backRect, rematchRect, exitRect;
    private Button backButton, rematchButton, exitButton;
    private final boolean resultMode;

    public DraftPlusHistoryDetailScreen(Screen parent, Lobby.DraftPlusHistoryEntry entry) {
        this(parent, entry, false);
    }

    public DraftPlusHistoryDetailScreen(Screen parent, Lobby.DraftPlusHistoryEntry entry, boolean resultMode) {
        super(Component.literal(Tr.t("friendrun.draft_plus.detail.title", "Матч Draft+")));
        this.parent = parent;
        this.entry = entry;
        this.resultMode = resultMode;
    }

    private Button make(Ui.R rect, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
            .bounds(rect.x(), rect.y(), rect.w(), rect.h()).build());
        Ui.invisible(button);
        return button;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 620);
        int h = Math.min(height - 16, 460);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel = new Ui.R(x, y, w, h);
        if (resultMode) {
            int half = (w - 30) / 2;
            rematchRect = new Ui.R(x + 12, y + h - 28, half, 20);
            exitRect = new Ui.R(x + 18 + half, y + h - 28, half, 20);
            rematchButton = make(rematchRect, () -> {
                FriendRunClient.rematchDraftPlus();
                Mc.setScreen(new LobbyRoomScreen(null));
            });
            exitButton = make(exitRect, () -> Mc.setScreen(new LobbyRoomScreen(null)));
        } else {
            backRect = new Ui.R(x + 12, y + h - 28, w - 24, 20);
            backButton = make(backRect, () -> Mc.setScreen(parent));
        }
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        JsonObject data = entry.data();
        JsonObject players = Lobby.obj(data, "players");
        JsonArray goals = data.has("boardGoals") && data.get("boardGoals").isJsonArray()
            ? data.getAsJsonArray("boardGoals") : new JsonArray();
        JsonArray owners = data.has("owners") && data.get("owners").isJsonArray()
            ? data.getAsJsonArray("owners") : new JsonArray();
        JsonArray pickers = data.has("pickers") && data.get("pickers").isJsonArray()
            ? data.getAsJsonArray("pickers") : new JsonArray();
        String date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(entry.timestamp()));
        String title = Lobby.bool(data, "cancelled")
            ? Tr.t("friendrun.draft_plus.detail.cancelled", "Матч отменён — MMR не меняется")
            : entry.winnerName().isBlank() || entry.winnerName().equals("—")
                ? Tr.t("friendrun.draft_plus.detail.draw", "Ничья")
                : Tr.t("friendrun.draft_plus.detail.winner", "Победитель: %s", entry.winnerName());

        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.CROWN, panel.x() + 14, panel.y() + 10, 2, Ui.GOLD);
        String formatLabel = entry.format().equalsIgnoreCase("TEAM")
            ? Tr.t("friendrun.draft_plus.detail.team_format", "Команды") : "FFA";
        Ui.bold(g, Tr.t("friendrun.draft_plus.detail.mode", "Матч Draft+ · %s", formatLabel),
            panel.x() + 36, panel.y() + 9, Ui.GOLD);
        Gfx.text(g, date + " · " + RunState.formatTime(entry.durationMs())
            + " · " + (Lobby.bool(data, "cancelled")
                ? Tr.t("friendrun.draft_plus.history.cancelled", "Отменён")
                : entry.rated() ? Tr.t("friendrun.draft_plus.history.rated", "Рейтинговый")
                    : Tr.t("friendrun.draft_plus.history.training", "Тренировочный")),
            panel.x() + 14, panel.y() + 28, Ui.MUTED);
        Gfx.text(g, Ui.trunc(title, panel.w() - 28), panel.x() + 14, panel.y() + 46, Ui.TEXT);
        JsonArray chaos = data.has("chaosModifiers") && data.get("chaosModifiers").isJsonArray()
            ? data.getAsJsonArray("chaosModifiers") : new JsonArray();
        String chaosText = chaos.isEmpty() ? Tr.t("friendrun.draft_plus.detail.chaos.off", "Хаос-моды: выключены")
            : Tr.t("friendrun.draft_plus.detail.chaos.prefix", "Хаос: ") + java.util.stream.StreamSupport.stream(chaos.spliterator(), false)
                .map(value -> DraftPlusChaos.displayName(value.getAsString()))
                .collect(java.util.stream.Collectors.joining(", "));
        String summary = entry.deltas().isBlank() ? chaosText : entry.deltas() + " · " + chaosText;
        Gfx.text(g, Ui.trunc(summary, panel.w() - 28), panel.x() + 14, panel.y() + 62,
            entry.deltas().isBlank() ? Ui.MUTED : Ui.GOLD);

        super.extractRenderState(g, mx, my, delta);
        int tableY = panel.y() + 84;
        Ui.frame(g, panel.x() + 12, tableY, panel.w() - 24, 18 + Math.min(10, players.size()) * 18, Ui.SECTION, Ui.LINE);
        Gfx.text(g, Tr.t("friendrun.draft_plus.detail.player", "Игрок"), panel.x() + 20, tableY + 5, Ui.MUTED);
        Gfx.text(g, Tr.t("friendrun.draft_plus.detail.cells_picks", "Кл/пик"), panel.x() + panel.w() - 290, tableY + 5, Ui.MUTED);
        Gfx.text(g, Tr.t("friendrun.draft_plus.detail.contribution", "Вклад"), panel.x() + panel.w() - 235, tableY + 5, Ui.MUTED);
        Gfx.text(g, Tr.t("friendrun.draft_plus.detail.role", "Роль"), panel.x() + panel.w() - 165, tableY + 5, Ui.MUTED);
        Ui.right(g, Tr.t("friendrun.draft_plus.detail.mmr", "MMR"), panel.x() + panel.w() - 20, tableY + 5, Ui.MUTED);
        int row = 0;
        for (String id : players.keySet()) {
            if (row >= 10) break;
            JsonObject player = Lobby.obj(players, id);
            int y = tableY + 20 + row * 18;
            String team = (int) Lobby.num(player, "team") == DraftPlusTeams.TEAM_A ? "A"
                : (int) Lobby.num(player, "team") == DraftPlusTeams.TEAM_B ? "B" : "";
            String name = team.isBlank() ? id : id + " · " + team;
            if (Lobby.bool(data, "globalTimeout"))
                name += Tr.t("friendrun.draft_plus.detail.all_disconnected", " · все отключились");
            else if (Lobby.bool(player, "forfeited"))
                name += Tr.t("friendrun.draft_plus.detail.afk_loss", " · AFK/поражение");
            Gfx.text(g, Ui.trunc(name, panel.w() - 330), panel.x() + 20, y, Ui.TEXT);
            String cellsAndPicks = (int) Lobby.num(player, "cells") + " / "
                + (int) Lobby.num(player, "successfulDraftPicks");
            Gfx.text(g, cellsAndPicks, panel.x() + panel.w() - 290, y, Ui.TEXT);
            int contributionScore = (int) Lobby.num(player, "contributionScore");
            int contributionDelta = (int) Lobby.num(player, "contributionDelta");
            String contribution = Tr.t("friendrun.draft_plus.detail.contribution_value", "%s (%s MMR)",
                contributionScore, (contributionDelta > 0 ? "+" : "") + contributionDelta);
            Gfx.text(g, Ui.trunc(contribution, 66), panel.x() + panel.w() - 235, y, Ui.MUTED);
            Gfx.text(g, Ui.trunc(DraftPlusRole.label(Lobby.str(player, "role")), 52), panel.x() + panel.w() - 165, y, Ui.MUTED);
            if (player.has("newMmr")) {
                int mmrDelta = (int) Lobby.num(player, "delta");
                String mmr = Tr.t("friendrun.draft_plus.detail.mmr_value", "%s (%s)",
                    Lobby.num(player, "newMmr"), (mmrDelta > 0 ? "+" : "") + mmrDelta);
                Ui.right(g, mmr, panel.x() + panel.w() - 20, y, mmrDelta > 0 ? Ui.GREEN : (mmrDelta < 0 ? Ui.RED : Ui.TEXT));
            } else Ui.right(g, "—", panel.x() + panel.w() - 20, y, Ui.MUTED);
            row++;
        }

        int boardY = tableY + 18 + Math.min(10, players.size()) * 18 + 12;
        Gfx.text(g, Tr.t("friendrun.draft_plus.detail.board", "Доска целей"), panel.x() + 14, boardY, Ui.MUTED);
        int gridY = boardY + 14;
        int gridX = panel.x() + 12;
        int gridW = panel.w() - 24;
        int cellW = gridW / 5;
        for (int i = 0; i < 25; i++) {
            int col = i % 5, r = i / 5;
            int cx = gridX + col * cellW;
            int cy = gridY + r * 24;
            Ui.frame(g, cx, cy, cellW - 2, 22, Ui.ROW, Ui.LINE);
            String encodedGoal = i < goals.size() ? goals.get(i).getAsString() : "—";
            DraftGoal parsedGoal = DraftGoal.parse(encodedGoal);
            String goal = parsedGoal == null ? encodedGoal : parsedGoal.displayName();
            String owner = i < owners.size() ? owners.get(i).getAsString() : "-";
            String picker = i < pickers.size() ? pickers.get(i).getAsString() : "";
            Gfx.text(g, Ui.trunc(goal, cellW - 10), cx + 4, cy + 3, Ui.TEXT);
            if (!owner.equals("-")) {
                String attribution = picker.isBlank() ? "✓ " + owner : "✓ " + owner + " · " + picker;
                Gfx.text(g, Ui.trunc(attribution, cellW - 10), cx + 4, cy + 12, Ui.GREEN);
            }
        }

        if (resultMode) {
            Ui.button(g, rematchRect, rematchRect.in(mx, my), true, Ui.Kind.GREEN);
            Gfx.centered(g, Tr.t("friendrun.draft_plus.detail.rematch", "Сыграть ещё раз"), rematchRect.x() + rematchRect.w() / 2,
                rematchRect.cy() - 4, Ui.GREEN);
            Ui.button(g, exitRect, exitRect.in(mx, my), true, Ui.Kind.NORMAL);
            Gfx.centered(g, Tr.t("friendrun.draft_plus.detail.exit_lobby", "Выйти в лобби"), exitRect.x() + exitRect.w() / 2,
                exitRect.cy() - 4, Ui.TEXT);
            rematchButton.active = exitButton.active = true;
        } else {
            Ui.button(g, backRect, backRect.in(mx, my), true, Ui.Kind.NORMAL);
            Gfx.centered(g, Tr.t("friendrun.ui.back", "Назад"), backRect.x() + backRect.w() / 2, backRect.cy() - 4, Ui.TEXT);
            backButton.active = true;
        }
    }
}
