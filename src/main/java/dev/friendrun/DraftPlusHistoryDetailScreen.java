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
    private Ui.R panel, backRect;
    private Button backButton;

    public DraftPlusHistoryDetailScreen(Screen parent, Lobby.DraftPlusHistoryEntry entry) {
        super(Component.literal("FriendRun — Матч Draft+"));
        this.parent = parent;
        this.entry = entry;
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
        backRect = new Ui.R(x + 12, y + h - 28, w - 24, 20);
        backButton = make(backRect, () -> Mc.setScreen(parent));
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
        String date = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(entry.timestamp()));
        String title = entry.winnerName().isBlank() || entry.winnerName().equals("—")
            ? "Ничья" : "Победитель: " + entry.winnerName();

        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.CROWN, panel.x() + 14, panel.y() + 10, 2, Ui.GOLD);
        Ui.bold(g, "Матч Draft+ · " + entry.format(), panel.x() + 36, panel.y() + 9, Ui.GOLD);
        Gfx.text(g, date + " · " + RunState.formatTime(entry.durationMs())
            + " · " + (entry.rated() ? "Рейтинговый" : "Тренировочный"), panel.x() + 14, panel.y() + 28, Ui.MUTED);
        Gfx.text(g, Ui.trunc(title, panel.w() - 28), panel.x() + 14, panel.y() + 46, Ui.TEXT);
        if (!entry.deltas().isBlank())
            Gfx.text(g, Ui.trunc(entry.deltas(), panel.w() - 28), panel.x() + 14, panel.y() + 62, Ui.GOLD);

        super.extractRenderState(g, mx, my, delta);
        int tableY = panel.y() + 84;
        Ui.frame(g, panel.x() + 12, tableY, panel.w() - 24, 18 + Math.min(10, players.size()) * 18, Ui.SECTION, Ui.LINE);
        Gfx.text(g, "Игрок", panel.x() + 20, tableY + 5, Ui.MUTED);
        Gfx.text(g, "Клетки", panel.x() + panel.w() - 250, tableY + 5, Ui.MUTED);
        Gfx.text(g, "Роль", panel.x() + panel.w() - 190, tableY + 5, Ui.MUTED);
        Ui.right(g, "MMR", panel.x() + panel.w() - 20, tableY + 5, Ui.MUTED);
        int row = 0;
        for (String id : players.keySet()) {
            if (row >= 10) break;
            JsonObject player = Lobby.obj(players, id);
            int y = tableY + 20 + row * 18;
            String team = (int) Lobby.num(player, "team") == DraftPlusTeams.TEAM_A ? "A"
                : (int) Lobby.num(player, "team") == DraftPlusTeams.TEAM_B ? "B" : "";
            String name = team.isBlank() ? id : id + " · " + team;
            Gfx.text(g, Ui.trunc(name, panel.w() - 290), panel.x() + 20, y, Ui.TEXT);
            Gfx.text(g, Integer.toString((int) Lobby.num(player, "cells")), panel.x() + panel.w() - 250, y, Ui.TEXT);
            Gfx.text(g, Ui.trunc(Lobby.str(player, "role"), 58), panel.x() + panel.w() - 190, y, Ui.MUTED);
            if (player.has("newMmr")) {
                int mmrDelta = (int) Lobby.num(player, "delta");
                String mmr = Lobby.num(player, "newMmr") + " (" + (mmrDelta > 0 ? "+" : "") + mmrDelta + ")";
                Ui.right(g, mmr, panel.x() + panel.w() - 20, y, mmrDelta > 0 ? Ui.GREEN : (mmrDelta < 0 ? Ui.RED : Ui.TEXT));
            } else Ui.right(g, "—", panel.x() + panel.w() - 20, y, Ui.MUTED);
            row++;
        }

        int boardY = tableY + 18 + Math.min(10, players.size()) * 18 + 12;
        Gfx.text(g, "Доска целей", panel.x() + 14, boardY, Ui.MUTED);
        int gridY = boardY + 14;
        int gridX = panel.x() + 12;
        int gridW = panel.w() - 24;
        int cellW = gridW / 5;
        for (int i = 0; i < 25; i++) {
            int col = i % 5, r = i / 5;
            int cx = gridX + col * cellW;
            int cy = gridY + r * 24;
            Ui.frame(g, cx, cy, cellW - 2, 22, Ui.ROW, Ui.LINE);
            String goal = i < goals.size() ? goals.get(i).getAsString() : "—";
            String owner = i < owners.size() ? owners.get(i).getAsString() : "-";
            Gfx.text(g, Ui.trunc(goal, cellW - 10), cx + 4, cy + 3, Ui.TEXT);
            if (!owner.equals("-")) Gfx.text(g, Ui.trunc("✓ " + owner, cellW - 10), cx + 4, cy + 12, Ui.GREEN);
        }

        Ui.button(g, backRect, backRect.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, "Назад", backRect.x() + backRect.w() / 2, backRect.cy() - 4, Ui.TEXT);
        backButton.active = true;
    }
}
