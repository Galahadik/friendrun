package dev.friendrun;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Separate, paged-in-memory match history for Draft+ FFA and Team. */
public final class DraftPlusHistoryScreen extends Screen {
    private final Screen parent;
    private Ui.R panel, ffaRect, teamRect, refreshRect, listRect, backRect;
    private Button ffaButton, teamButton, refreshButton, backButton;
    private final Ui.R[] rowRects = new Ui.R[8];
    private final Button[] rowButtons = new Button[8];
    private List<Lobby.DraftPlusHistoryEntry> rows = List.of();
    private boolean team;
    private boolean loading;
    private String error = "";
    private int scroll;

    public DraftPlusHistoryScreen(Screen parent, boolean team) {
        super(Component.literal("FriendRun — История Draft+"));
        this.parent = parent;
        this.team = team;
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
                List<Lobby.DraftPlusHistoryEntry> loaded =
                    Lobby.fetchDraftPlusHistory(Config.databaseUrl, requestedTeam);
                Minecraft.getInstance().execute(() -> {
                    if (team == requestedTeam) {
                        rows = loaded;
                        loading = false;
                        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - rowButtons.length)));
                    }
                });
            } catch (Exception ex) {
                Minecraft.getInstance().execute(() -> {
                    if (team == requestedTeam) {
                        error = ex.getMessage() == null ? "Не удалось загрузить историю" : ex.getMessage();
                        loading = false;
                    }
                });
            }
        }, "FriendRun-DraftPlus-History");
        worker.setDaemon(true);
        worker.start();
    }

    private void openRow(int index) {
        int actual = scroll + index;
        if (actual < 0 || actual >= rows.size()) return;
        Mc.setScreen(new DraftPlusHistoryDetailScreen(this, rows.get(actual)));
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 500);
        int h = Math.min(height - 20, 340);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel = new Ui.R(x, y, w, h);
        int tabW = (w - 32 - 6) / 2;
        ffaRect = new Ui.R(x + 12, y + 42, tabW, 22);
        teamRect = new Ui.R(x + 18 + tabW, y + 42, tabW, 22);
        refreshRect = new Ui.R(x + w - 66, y + 70, 54, 20);
        listRect = new Ui.R(x + 12, y + 94, w - 24, h - 130);
        backRect = new Ui.R(x + 12, y + h - 28, w - 24, 20);

        ffaButton = make(ffaRect, () -> { if (team) { team = false; scroll = 0; load(); } });
        teamButton = make(teamRect, () -> { if (!team) { team = true; scroll = 0; load(); } });
        refreshButton = make(refreshRect, this::load);
        backButton = make(backRect, () -> Mc.setScreen(parent));
        for (int i = 0; i < rowButtons.length; i++) {
            final int index = i;
            Ui.R rect = new Ui.R(listRect.x() + 4, listRect.y() + 24 + i * 22, listRect.w() - 8, 20);
            rowRects[i] = rect;
            rowButtons[i] = make(rect, () -> openRow(index));
        }
        load();
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!listRect.in((int) mouseX, (int) mouseY) || rows.size() <= rowButtons.length) return false;
        scroll = Math.max(0, Math.min(rows.size() - rowButtons.length, scroll + (scrollY > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.GEAR, panel.x() + 14, panel.y() + 10, 2, Ui.GOLD);
        Ui.bold(g, "История Draft+", panel.x() + 36, panel.y() + 9, Ui.GOLD);
        Gfx.text(g, "Последние 50 матчей", panel.x() + 14, panel.y() + 27, Ui.MUTED);

        super.extractRenderState(g, mx, my, delta);
        Ui.button(g, ffaRect, ffaRect.in(mx, my), true, team ? Ui.Kind.NORMAL : Ui.Kind.GREEN);
        Gfx.centered(g, "FFA", ffaRect.x() + ffaRect.w() / 2, ffaRect.cy() - 4, team ? Ui.TEXT : Ui.GREEN);
        Ui.button(g, teamRect, teamRect.in(mx, my), true, team ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, "Команды", teamRect.x() + teamRect.w() / 2, teamRect.cy() - 4, team ? Ui.GREEN : Ui.TEXT);
        Ui.button(g, refreshRect, refreshRect.in(mx, my), !loading, Ui.Kind.NORMAL);
        Gfx.centered(g, "Обновить", refreshRect.x() + refreshRect.w() / 2, refreshRect.cy() - 4, Ui.TEXT);

        Ui.frame(g, listRect.x(), listRect.y(), listRect.w(), listRect.h(), Ui.SECTION, Ui.LINE);
        if (loading) {
            Gfx.centered(g, "Загрузка истории…", listRect.x() + listRect.w() / 2, listRect.y() + 30, Ui.MUTED);
        } else if (!error.isBlank()) {
            Gfx.centered(g, Ui.trunc(error, listRect.w() - 20), listRect.x() + listRect.w() / 2, listRect.y() + 30, Ui.RED);
        } else if (rows.isEmpty()) {
            Gfx.centered(g, "Пока нет сохранённых матчей", listRect.x() + listRect.w() / 2, listRect.y() + 30, Ui.MUTED);
        } else {
            int visible = Math.min(rowButtons.length, rows.size() - scroll);
            for (int i = 0; i < rowButtons.length; i++) {
                boolean available = i < visible;
                rowButtons[i].active = available && !loading;
                Ui.R rect = rowRects[i];
                Ui.button(g, rect, rect.in(mx, my), available && !loading, Ui.Kind.NORMAL);
                if (!available) continue;
                Lobby.DraftPlusHistoryEntry entry = rows.get(scroll + i);
                String date = new SimpleDateFormat("dd.MM HH:mm").format(new Date(entry.timestamp()));
                String mode = entry.rated() ? "Rated" : "Training";
                String winner = entry.winnerName().isBlank() ? "—" : entry.winnerName();
                String left = (scroll + i + 1) + ". " + date + " · " + mode + " · " + winner;
                Gfx.text(g, Ui.trunc(left, rect.w() - 120), rect.x() + 7, rect.y() + 6, Ui.TEXT);
                String right = RunState.formatTime(entry.durationMs());
                Ui.right(g, right, rect.x() + rect.w() - 7, rect.y() + 6, Ui.MUTED);
            }
        }

        Ui.button(g, backRect, backRect.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, "Назад", backRect.x() + backRect.w() / 2, backRect.cy() - 4, Ui.TEXT);
        ffaButton.active = teamButton.active = backButton.active = true;
        refreshButton.active = !loading;
    }
}
