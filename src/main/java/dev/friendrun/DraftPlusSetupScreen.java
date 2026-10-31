package dev.friendrun;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Настройка формата Draft+ в визуальном стиле FriendRun. */
public final class DraftPlusSetupScreen extends Screen {
    private final Screen parent;
    private Button ffaButton, teamButton, sizeButton, confirmButton, backButton;
    private Ui.R panel, ffaRect, teamRect, sizeRect, confirmRect, backRect;

    public DraftPlusSetupScreen(Screen parent) {
        super(Component.literal("FriendRun — Draft+"));
        this.parent = parent;
    }

    private Button make(Ui.R r, Runnable action) {
        Button b = addRenderableWidget(Button.builder(Component.empty(), ignored -> action.run())
            .bounds(r.x(), r.y(), r.w(), r.h()).build());
        Ui.invisible(b);
        return b;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 420);
        int h = 210;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        panel = new Ui.R(x, y, w, h);
        ffaRect = new Ui.R(x + 12, y + 54, (w - 28) / 2, 30);
        teamRect = new Ui.R(ffaRect.x() + ffaRect.w() + 4, y + 54, ffaRect.w(), 30);
        sizeRect = new Ui.R(x + 12, y + 96, w - 24, 26);
        confirmRect = new Ui.R(x + 12, y + 158, (w - 28) / 2, 22);
        backRect = new Ui.R(confirmRect.x() + confirmRect.w() + 4, y + 158, confirmRect.w(), 22);

        ffaButton = make(ffaRect, () -> FriendRunClient.STATE.draftPlusFormat = 0);
        teamButton = make(teamRect, () -> FriendRunClient.STATE.draftPlusFormat = 1);
        sizeButton = make(sizeRect, () -> {
            RunState s = FriendRunClient.STATE;
            s.draftPlusTeamSize = s.draftPlusTeamSize % 5 + 1;
        });
        confirmButton = make(confirmRect, () -> {
            RunState s = FriendRunClient.STATE;
            if (!s.amHost || s.phase != RunState.Phase.IDLE) return;
            FriendRunClient.setDraftPlusConfig(s.draftPlusFormat, s.draftPlusTeamSize);
            FriendRunClient.setGameMode(Game.DRAFT_PLUS);
            Mc.setScreen(parent);
        });
        backButton = make(backRect, () -> Mc.setScreen(parent));
    }

    @Override
    public void onClose() {
        Mc.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        RunState s = FriendRunClient.STATE;
        boolean editable = s.amHost && s.phase == RunState.Phase.IDLE;
        boolean teamMode = s.draftPlusFormat == 1;

        Ui.backdrop(g, width, height);
        Ui.panel(g, panel.x(), panel.y(), panel.w(), panel.h());
        Icons.draw(g, Icons.DICE, panel.x() + 14, panel.y() + 13, 2, Ui.GOLD);
        Ui.bold(g, "Draft+", panel.x() + 36, panel.y() + 12, Ui.GOLD);
        Gfx.text(g, "Выберите формат матча", panel.x() + 14, panel.y() + 34, Ui.MUTED);

        super.extractRenderState(g, mx, my, delta);

        Ui.button(g, ffaRect, ffaRect.in(mx, my), editable, s.draftPlusFormat == 0 ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, "FFA", ffaRect.x() + ffaRect.w() / 2, ffaRect.cy() - 4, s.draftPlusFormat == 0 ? Ui.GREEN : Ui.TEXT);
        Ui.button(g, teamRect, teamRect.in(mx, my), editable, teamMode ? Ui.Kind.GREEN : Ui.Kind.NORMAL);
        Gfx.centered(g, "Команды", teamRect.x() + teamRect.w() / 2, teamRect.cy() - 4, teamMode ? Ui.GREEN : Ui.TEXT);

        Ui.button(g, sizeRect, sizeRect.in(mx, my), editable && teamMode, Ui.Kind.NORMAL);
        String teamSize = "Размер команд: " + s.draftPlusTeamSize + " × " + s.draftPlusTeamSize + " (нажмите, чтобы изменить)";
        Gfx.centered(g, teamMode ? teamSize : "FFA: от 1 до 10 игроков · доска 5 × 5",
            sizeRect.x() + sizeRect.w() / 2, sizeRect.cy() - 4, teamMode ? Ui.TEXT : Ui.MUTED);
        Gfx.centered(g, "Победа: захватить 13 из 25 клеток", panel.x() + panel.w() / 2, panel.y() + 139, Ui.MUTED);

        Ui.button(g, confirmRect, confirmRect.in(mx, my), editable, Ui.Kind.GOLD);
        Gfx.centered(g, "Выбрать", confirmRect.x() + confirmRect.w() / 2, confirmRect.cy() - 4, editable ? 0xFF1A1200 : Ui.MUTED);
        Ui.button(g, backRect, backRect.in(mx, my), true, Ui.Kind.NORMAL);
        Gfx.centered(g, "Назад", backRect.x() + backRect.w() / 2, backRect.cy() - 4, Ui.TEXT);

        ffaButton.active = teamButton.active = editable;
        sizeButton.active = editable && teamMode;
        confirmButton.active = editable;
    }
}