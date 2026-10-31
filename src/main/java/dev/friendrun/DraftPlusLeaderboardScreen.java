package dev.friendrun;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Separate Draft+ FFA and Team leaderboards, backed by isolated Firebase nodes. */
public final class DraftPlusLeaderboardScreen extends Screen {
    private final Screen parent;
    private Ui.R panel, ffaRect, teamRect, refreshRect, listRect, backRect;
    private Button ffaButton, teamButton, refreshButton, backButton;
    private List<Lobby.DraftPlusRank> rows = List.of();
    private boolean team;
    private boolean loading;
    private String error = "";
    private int scroll;

    public DraftPlusLeaderboardScreen(Screen parent) {
        super(Component.literal("FriendRun — Рейтинг Draft+"));
        this.parent = parent;
    }

    private Button make(Ui.R rect, Runnable action) {
        Button button = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
            .bounds(rect.x(), rect.y(), rect.w(), rect.h()).build());
        Ui.invisible(button);
        return button;
    }

    private void load() {
        loading = true;
        error = "";
        rows = List.of();
        final boolean requestedTeam = team;
        Thread worker = new Thread(() -> {
            try {
                List<Lobby.DraftPlusRank> loaded =
                    Lobby.fetchDraftPlusLeaderboard(Config.databaseUrl, requestedTeam);
                Minecraft.getInstance().execute(() -> {
                    if (team == requestedTeam) {
                        rows = loaded;
                        loading = false;
                        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - 8)));
                    }
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    if (team == requestedTeam) {
                        error = ex.getMessage() == null ? "Не удалось загрузить рейтинг" : ex.getMessage();
                        loading = false;
                    }
                });
            }
        }, "FriendRun-DraftPlus-Leaderboard");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 460);
        int h = Math.min(height - 20, 330);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel = new Ui.R(x, y, w, h);
        int tabW = (w - 32 - 6) / 2;
        ffaRect = new Ui.R(x + 12, y + 42, tabW, 22);
        teamRect = new Ui.R(x + 18 + tabW, y + 42, tabW, 22);
        refreshRect = new Ui.R(x + w - 66, y + 70, 54, 20);
        listRect = new Ui.R(x + 12, y + 94, w - 24, h - 130);
        backRect = new Ui.R(x + 12, y + h - 28, w - 24, 20);
        ffaButton = make(ffaRect, () -> {
            if (team) { team = false; scroll = 0; load(); }
        });
        teamButton = make(teamRect, () -> {
            if (!team) { team = true; scroll = 0; load(); }
        });
        refreshButton = make(refreshRect, this::load);
        backButton = make(backRect, () -> Mc.setScreen(parent));
        load();
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!listRect.in((int) mouseX, (int) mouseY) || rows.size() <= 8) return false;
        scroll = Math.max(0, Math.min(rows.size() - 8, scroll + (scrollY > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.CROWN, panel.x() + 14, panel.y() + 10, 2, Ui.GOLD);
        Ui.bold(g, "Рейтинг Draft+", panel.x() + 36, panel.y() + 9, Ui.GOLD);
        Gfx.text(g, "Отдельный рейтинг · старт 1000 MMR · K = 32",
            panel.x() + 14, panel.y() + 27, Ui.MUTED);

        super.extractRenderState(g, mx, my, delta);
        Ui.button(g, ffaRect, ffaRect.in(mx, my), true, team ? Ui.Kind.NORMAL : Ui.Kind.GREEN);
        Gfx.centered(g, "FFA", ffaRect.x() + ffaRect.w() / 2, ffaRect.cy() - 4, team ? Ui.TEXT : Ui.GREEN);
        Ui.button(g, teamRect, teamRect.in(mx, my), true, team ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, "Команды", teamRect.x() + teamRect.w() / 2, teamRect.cy() - 4, team ? Ui.GREEN : Ui.TEXT);
        Ui.button(g, refreshRect, refreshRect.in(mx, my), !loading, Ui.Kind.NORMAL);
        Gfx.centered(g, "Обновить", refreshRect.x() + refreshRect.w() / 2, refreshRect.cy() - 4, Ui.TEXT);

        Ui.frame(g, listRect.x(), listRect.y(), listRect.w(), listRect.h(), Ui.SECTION, Ui.LINE);
        if (loading) {
            Gfx.centered(g, "Загрузка рейтинга…", listRect.x() + listRect.w() / 2,
                listRect.y() + 30, Ui.MUTED);
        } else if (!error.isBlank()) {
            Gfx.centered(g, Ui.trunc(error, listRect.w() - 20), listRect.x() + listRect.w() / 2,
                listRect.y() + 30, Ui.RED);
        } else if (rows.isEmpty()) {
            Gfx.centered(g, "Пока нет рейтинговых матчей", listRect.x() + listRect.w() / 2,
                listRect.y() + 30, Ui.MUTED);
        } else {
            int rowY = listRect.y() + 7;
            Gfx.text(g, "#", listRect.x() + 8, rowY, Ui.MUTED);
            Gfx.text(g, "Игрок", listRect.x() + 34, rowY, Ui.MUTED);
            Ui.right(g, "Победы / матчи", listRect.x() + listRect.w() - 76, rowY, Ui.MUTED);
            Ui.right(g, "MMR", listRect.x() + listRect.w() - 8, rowY, Ui.GOLD);
            rowY += 17;
            int visible = Math.min(8, rows.size() - scroll);
            for (int i = 0; i < visible; i++) {
                Lobby.DraftPlusRank row = rows.get(scroll + i);
                int y = rowY + i * 22;
                if (i % 2 == 0) Gfx.rect(g, listRect.x() + 4, y - 2, listRect.x() + listRect.w() - 4, y + 18, Ui.ROW);
                Gfx.text(g, Integer.toString(scroll + i + 1), listRect.x() + 8, y + 3, Ui.MUTED);
                Gfx.text(g, Ui.trunc(row.name(), listRect.w() - 190), listRect.x() + 34, y + 3,
                    row.name().equals(FriendRunClient.myName()) ? Ui.GOLD : Ui.TEXT);
                Ui.right(g, row.wins() + " / " + row.games(), listRect.x() + listRect.w() - 76, y + 3, Ui.MUTED);
                Ui.right(g, Integer.toString(row.mmr()), listRect.x() + listRect.w() - 8, y + 3, Ui.TEXT);
            }
        }

        Ui.button(g, backRect, backRect.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, "Назад", backRect.x() + backRect.w() / 2, backRect.cy() - 4, Ui.TEXT);
        ffaButton.active = teamButton.active = backButton.active = true;
        refreshButton.active = !loading;
    }
}
